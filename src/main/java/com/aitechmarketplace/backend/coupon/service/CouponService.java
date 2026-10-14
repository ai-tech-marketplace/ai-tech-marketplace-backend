package com.aitechmarketplace.backend.coupon.service;

import com.aitechmarketplace.backend.coupon.dto.*;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CouponService {

    Page<CouponResponse> getPublicCoupons(Long userIdOrNull, Pageable pageable);

    CouponValidateResponse validateCoupon(Long userId, CouponValidateRequest request);

    Coupon getValidCouponForOrder(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee);

    void recordCouponUsage(Coupon coupon, Long userId, Long orderId, BigDecimal discountAmount);

    void rollbackCouponUsage(Long orderId);

    Page<CouponResponse> getAdminCoupons(String status, String search, Pageable pageable);

    CouponResponse createCoupon(CouponCreateRequest request);

    CouponResponse updateCoupon(Long id, CouponUpdateRequest request);

    CouponResponse updateCouponStatus(Long id, String status);
}
