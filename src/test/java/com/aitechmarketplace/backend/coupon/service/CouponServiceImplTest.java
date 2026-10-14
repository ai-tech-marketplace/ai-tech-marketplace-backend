package com.aitechmarketplace.backend.coupon.service;

import com.aitechmarketplace.backend.address.entity.Address;
import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.coupon.dto.CouponCreateRequest;
import com.aitechmarketplace.backend.coupon.dto.CouponValidateRequest;
import com.aitechmarketplace.backend.coupon.dto.CouponValidateResponse;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.CouponUsage;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private CouponServiceImpl couponService;

    private User user;
    private Coupon percentCoupon;
    private Cart cart;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        percentCoupon = new Coupon();
        percentCoupon.setId(10L);
        percentCoupon.setCode("TECH10");
        percentCoupon.setName("Tech 10%");
        percentCoupon.setDiscountType(DiscountType.PERCENTAGE);
        percentCoupon.setDiscountValue(new BigDecimal("10.00"));
        percentCoupon.setMaxDiscountAmount(new BigDecimal("500000.00"));
        percentCoupon.setMinOrderValue(new BigDecimal("1000000.00"));
        percentCoupon.setUsageLimit(100);
        percentCoupon.setUsedCount(0);
        percentCoupon.setUsageLimitPerUser(1);
        percentCoupon.setStatus("ACTIVE");
        percentCoupon.setStartDate(OffsetDateTime.now().minusDays(1));
        percentCoupon.setEndDate(OffsetDateTime.now().plusDays(10));

        cart = new Cart();
        org.springframework.test.util.ReflectionTestUtils.setField(cart, "id", 100L);
        cart.setUser(user);

        product = new Product();
        product.setId(200L);
        product.setName("AI Laptop");
        product.setPrice(new BigDecimal("20000000.00"));

        cartItem = new CartItem(cart, product, 1);
    }

    @Test
    void validateCoupon_percentageDiscount_shouldCalculateCorrectlyAndCapAtMax() {
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));
        when(couponUsageRepository.countByCouponIdAndUserIdAndStatus(10L, 1L, "USED")).thenReturn(0L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);
        CouponValidateResponse response = couponService.validateCoupon(1L, request);

        assertThat(response.valid()).isTrue();
        assertThat(response.code()).isEqualTo("TECH10");
        assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("20000000.00"));
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("19530000.00")); // 20M + 30k - 500k
    }

    @Test
    void validateCoupon_fixedDiscount_shouldDeductFixedAmount() {
        Coupon fixedCoupon = new Coupon();
        fixedCoupon.setId(20L);
        fixedCoupon.setCode("FIXED50");
        fixedCoupon.setName("50k Off");
        fixedCoupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        fixedCoupon.setDiscountValue(new BigDecimal("50000.00"));
        fixedCoupon.setMinOrderValue(new BigDecimal("100000.00"));
        fixedCoupon.setUsageLimit(50);
        fixedCoupon.setUsedCount(0);
        fixedCoupon.setUsageLimitPerUser(1);
        fixedCoupon.setStatus("ACTIVE");
        fixedCoupon.setStartDate(OffsetDateTime.now().minusDays(1));
        fixedCoupon.setEndDate(OffsetDateTime.now().plusDays(5));

        when(couponRepository.findByCodeIgnoreCase("FIXED50")).thenReturn(Optional.of(fixedCoupon));
        when(couponUsageRepository.countByCouponIdAndUserIdAndStatus(20L, 1L, "USED")).thenReturn(0L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));

        CouponValidateRequest request = new CouponValidateRequest("FIXED50", null);
        CouponValidateResponse response = couponService.validateCoupon(1L, request);

        assertThat(response.valid()).isTrue();
        assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    void validateCoupon_shippingDiscount_withAddress_shouldDeductShipping() {
        Coupon shipCoupon = new Coupon();
        shipCoupon.setId(30L);
        shipCoupon.setCode("FREESHIP");
        shipCoupon.setName("Free Shipping");
        shipCoupon.setDiscountType(DiscountType.SHIPPING);
        shipCoupon.setDiscountValue(new BigDecimal("30000.00"));
        shipCoupon.setMaxDiscountAmount(new BigDecimal("30000.00"));
        shipCoupon.setMinOrderValue(BigDecimal.ZERO);
        shipCoupon.setUsageLimit(100);
        shipCoupon.setUsedCount(0);
        shipCoupon.setUsageLimitPerUser(2);
        shipCoupon.setStatus("ACTIVE");
        shipCoupon.setStartDate(OffsetDateTime.now().minusDays(1));
        shipCoupon.setEndDate(OffsetDateTime.now().plusDays(5));

        Address address = new Address();
        address.setId(5L);
        address.setUser(user);

        when(couponRepository.findByCodeIgnoreCase("FREESHIP")).thenReturn(Optional.of(shipCoupon));
        when(couponUsageRepository.countByCouponIdAndUserIdAndStatus(30L, 1L, "USED")).thenReturn(0L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));
        when(addressRepository.findById(5L)).thenReturn(Optional.of(address));

        CouponValidateRequest request = new CouponValidateRequest("FREESHIP", 5L);
        CouponValidateResponse response = couponService.validateCoupon(1L, request);

        assertThat(response.valid()).isTrue();
        assertThat(response.shippingDiscountPending()).isFalse();
        assertThat(response.discountAmount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("20000000.00"));
    }

    @Test
    void validateCoupon_whenNotFound_shouldThrowNotFoundException() {
        when(couponRepository.findByCodeIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

        CouponValidateRequest request = new CouponValidateRequest("UNKNOWN", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Coupon not found")
                .matches(e -> ((NotFoundException) e).getErrorCode().equals("COUPON_NOT_FOUND"));
    }

    @Test
    void validateCoupon_whenInactive_shouldThrowBadRequestException() {
        percentCoupon.setStatus("INACTIVE");
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("COUPON_INACTIVE"));
    }

    @Test
    void validateCoupon_whenExpired_shouldThrowBadRequestException() {
        percentCoupon.setEndDate(OffsetDateTime.now().minusHours(1));
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("COUPON_EXPIRED"));
    }

    @Test
    void validateCoupon_whenUsageLimitReached_shouldThrowBadRequestException() {
        percentCoupon.setUsageLimit(5);
        percentCoupon.setUsedCount(5);
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("COUPON_USAGE_LIMIT_EXCEEDED"));
    }

    @Test
    void validateCoupon_whenUserLimitReached_shouldThrowBadRequestException() {
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));
        when(couponUsageRepository.countByCouponIdAndUserIdAndStatus(10L, 1L, "USED")).thenReturn(1L);

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("COUPON_USER_LIMIT_EXCEEDED"));
    }

    @Test
    void validateCoupon_whenSubtotalBelowMinOrder_shouldThrowBadRequestException() {
        product.setPrice(new BigDecimal("500000.00")); // Below 1,000,000 threshold
        when(couponRepository.findByCodeIgnoreCase("TECH10")).thenReturn(Optional.of(percentCoupon));
        when(couponUsageRepository.countByCouponIdAndUserIdAndStatus(10L, 1L, "USED")).thenReturn(0L);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByIdAsc(100L)).thenReturn(List.of(cartItem));

        CouponValidateRequest request = new CouponValidateRequest("TECH10", null);

        assertThatThrownBy(() -> couponService.validateCoupon(1L, request))
                .isInstanceOf(BadRequestException.class)
                .matches(e -> ((BadRequestException) e).getErrorCode().equals("COUPON_MIN_ORDER_NOT_MET"));
    }

    @Test
    void recordCouponUsage_shouldIncrementUsedCountAndSaveUsage() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        couponService.recordCouponUsage(percentCoupon, 1L, 999L, new BigDecimal("500000.00"));

        assertThat(percentCoupon.getUsedCount()).isEqualTo(1);
        verify(couponRepository).save(percentCoupon);
        verify(couponUsageRepository).save(any(CouponUsage.class));
    }

    @Test
    void rollbackCouponUsage_shouldMarkReleasedAndDecrementUsedCount() {
        percentCoupon.setUsedCount(1);
        CouponUsage usage = new CouponUsage();
        usage.setCoupon(percentCoupon);
        usage.setUser(user);
        usage.setOrderId(999L);
        usage.setStatus("USED");

        when(couponUsageRepository.findByOrderId(999L)).thenReturn(Optional.of(usage));

        couponService.rollbackCouponUsage(999L);

        assertThat(usage.getStatus()).isEqualTo("RELEASED");
        assertThat(usage.getReleasedAt()).isNotNull();
        assertThat(percentCoupon.getUsedCount()).isEqualTo(0);
        verify(couponUsageRepository).save(usage);
        verify(couponRepository).save(percentCoupon);
    }

    @Test
    void createCoupon_whenCodeExists_shouldThrowConflictException() {
        when(couponRepository.existsByCodeIgnoreCase("DUP")).thenReturn(true);

        CouponCreateRequest request = new CouponCreateRequest(
                "DUP", "Duplicate", "desc", DiscountType.FIXED_AMOUNT,
                new BigDecimal("10000.00"), null, BigDecimal.ZERO,
                100, 1, true,
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(5)
        );

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ConflictException.class)
                .matches(e -> ((ConflictException) e).getErrorCode().equals("COUPON_ALREADY_EXISTS"));
    }
}
