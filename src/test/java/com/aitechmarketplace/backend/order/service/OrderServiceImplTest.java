package com.aitechmarketplace.backend.order.service;

import com.aitechmarketplace.backend.address.entity.Address;
import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
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
import com.aitechmarketplace.backend.user.repository.UserRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private RedisLockRegistry checkoutLockRegistry;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private Address address;
    private Cart cart;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("buyer@example.com");

        address = new Address();
        address.setId(10L);
        address.setUser(user);
        address.setRecipientName("John Doe");
        address.setPhone("0901234567");
        address.setAddressLine("123 Main St");
        address.setCity("Hanoi");
        address.setCountry("Vietnam");

        cart = new Cart();
        ReflectionTestUtils.setField(cart, "id", 100L);
        cart.setUser(user);

        product = new Product();
        product.setId(200L);
        product.setName("Gaming PC");
        product.setSku("PC-001");
        product.setPrice(new BigDecimal("10000000.00"));
        product.setStockQuantity(10);
        product.setActive(true);

        cartItem = new CartItem(cart, product, 2);

        lenient().when(productRepository.findByIdForUpdate(200L)).thenAnswer(inv -> Optional.of(product));

        DistributedLock mockLock = mock(DistributedLock.class);
        try {
            lenient().when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        } catch (InterruptedException ignored) {
        }
        lenient().when(checkoutLockRegistry.obtain(anyString())).thenReturn(mockLock);
    }

    @Test
    void createOrder_shouldSucceed_andDeductStock_andClearCart() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 500L);
            return o;
        });

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, "Call before delivering");
        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("20000000.00"));
        assertThat(response.shippingFee()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("20030000.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getStockQuantity()).isEqualTo(8);

        verify(productRepository).save(product);
        verify(paymentRepository).save(any(Payment.class));
        verify(cartItemRepository).deleteByCartId(100L);
    }

    @Test
    void createOrder_withCoupon_shouldApplyDiscountAndRecordUsage() {
        Coupon coupon = new Coupon();
        coupon.setId(50L);
        coupon.setCode("PROMO10");
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("10.00"));
        coupon.setMaxDiscountAmount(new BigDecimal("1000000.00"));
        coupon.setMinOrderValue(BigDecimal.ZERO);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));
        when(couponService.getValidCouponForOrder(eq("PROMO10"), eq(1L), any(), any())).thenReturn(coupon);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 501L);
            return o;
        });

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.VNPAY, "PROMO10", null);
        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("1000000.00")); // 10% of 20M is 2M, capped at 1M
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("19030000.00"));
        verify(couponService).recordCouponUsage(eq(coupon), eq(1L), eq(501L), any());
    }

    @Test
    void createOrder_whenCartEmpty_shouldThrowBadRequestException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of());

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("CART_EMPTY"));
    }

    @Test
    void createOrder_whenAddressNotOwned_shouldThrowForbiddenException() {
        User otherUser = new User();
        otherUser.setId(99L);
        address.setUser(otherUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createOrder_whenProductOutOfStock_shouldThrowBadRequestException() {
        product.setStockQuantity(1); // Cart has quantity 2

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("OUT_OF_STOCK"));
    }

    @Test
    void createOrder_whenProductInactive_shouldThrowBadRequestException() {
        product.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findById(10L)).thenReturn(Optional.of(address));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));

        OrderCreateRequest request = new OrderCreateRequest(10L, PaymentMethod.COD, null, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("PRODUCT_NOT_AVAILABLE"));
    }

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
