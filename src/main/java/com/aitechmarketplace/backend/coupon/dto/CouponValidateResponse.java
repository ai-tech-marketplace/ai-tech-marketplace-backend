package com.aitechmarketplace.backend.coupon.dto;

import com.aitechmarketplace.backend.coupon.entity.DiscountType;

import java.math.BigDecimal;

public record CouponValidateResponse(
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal estimatedTotal,
        boolean valid,
        boolean shippingDiscountPending,
        String message
) {}
