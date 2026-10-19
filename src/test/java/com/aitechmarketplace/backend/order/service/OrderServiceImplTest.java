package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.coupon.service.CouponService;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.*;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.DistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    // OrderServiceImpl now only needs these in its constructor:
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private RedisLockRegistry checkoutLockRegistry;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private CheckoutTransactionService checkoutTransactionService;

    @Mock
    private com.aitechmarketplace.backend.settlement.repository.SellerOrderSettlementRepository sellerOrderSettlementRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("buyer@example.com");

        product = new Product();
        product.setId(200L);
        product.setName("Gaming PC");
        product.setSku("PC-001");
        product.setPrice(new BigDecimal("10000000.00"));
        product.setStockQuantity(10);
        product.setActive(true);

        // Default: lock is acquirable
        DistributedLock mockLock = mock(DistributedLock.class);
        try {
            lenient().when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        } catch (InterruptedException ignored) {
        }
        lenient().when(checkoutLockRegistry.obtain(anyString())).thenReturn(mockLock);
    }

    // ── createOrder — lock and delegation tests ─────────────────────────────────

    @Test
    void createOrder_delegatesToCheckoutTransactionService() {
        OrderResponse mockResponse = mock(OrderResponse.class);
        when(checkoutTransactionService.execute(anyLong(), any())).thenReturn(mockResponse);

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, "Call before delivering");
        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response).isEqualTo(mockResponse);
        verify(checkoutTransactionService).execute(1L, request);
    }

    @Test
    void createOrder_whenLockNotAcquired_throwsConflictException() throws Exception {
        DistributedLock busyLock = mock(DistributedLock.class);
        when(busyLock.tryLock(anyLong(), any())).thenReturn(false);
        when(checkoutLockRegistry.obtain(anyString())).thenReturn(busyLock);

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Checkout already in progress");
    }

    @Test
    void createOrder_whenCheckoutThrows_lockIsStillReleased() throws Exception {
        DistributedLock mockLock = mock(DistributedLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(checkoutLockRegistry.obtain(anyString())).thenReturn(mockLock);
        when(checkoutTransactionService.execute(anyLong(), any())).thenThrow(new BadRequestException("Cart is empty", "CART_EMPTY"));

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BadRequestException.class);

        verify(mockLock, times(1)).unlock();
    }

    // ── cancelOrder ─────────────────────────────────────────────────────────────

    @Test
    void cancelOrder_whenPending_shouldRestoreStockAndRollbackCoupon() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 600L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem(order, product, "Gaming PC", "PC-001", 2, new BigDecimal("10000000.00"), new BigDecimal("20000000.00"));
        order.addItem(item);
        product.setStockQuantity(8);

        when(orderRepository.findById(600L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        lenient().when(productRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(product));

        OrderResponse cancelled = orderService.cancelOrder(600L, 1L, false);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(productRepository).save(product);
        verify(couponService).rollbackCouponUsage(600L);
    }

    @Test
    void cancelOrder_whenAlreadyShipped_shouldThrowBadRequestException() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 601L);
        order.setUser(user);
        order.setStatus(OrderStatus.SHIPPED);

        when(orderRepository.findById(601L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(601L, 1L, false))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("ORDER_CANNOT_BE_CANCELLED"));
    }

    // ── getOrderById ─────────────────────────────────────────────────────────────

    @Test
    void getOrderById_whenNotOwnedAndNotAdmin_shouldThrowNotFoundException() {
        User otherUser = new User();
        otherUser.setId(99L);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 700L);
        order.setUser(otherUser);

        when(orderRepository.findById(700L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(700L, 1L, false))
                .isInstanceOf(NotFoundException.class)
                .matches(e -> ((NotFoundException) e).getErrorCode().equals("ORDER_NOT_FOUND"));
    }
}
