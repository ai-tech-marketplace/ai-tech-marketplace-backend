package com.aitechmarketplace.backend.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record CouponValidateRequest(
        @NotBlank(message = "Coupon code is required")
        String couponCode,

        Long addressId
) {}
