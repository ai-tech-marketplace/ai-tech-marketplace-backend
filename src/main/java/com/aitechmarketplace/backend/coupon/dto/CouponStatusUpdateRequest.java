package com.aitechmarketplace.backend.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record CouponStatusUpdateRequest(
        @NotBlank(message = "Status is required")
        String status
) {}
