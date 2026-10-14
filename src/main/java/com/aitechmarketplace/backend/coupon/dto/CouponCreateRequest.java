package com.aitechmarketplace.backend.coupon.dto;

import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponCreateRequest(
        @NotBlank(message = "Code is required")
        String code,

        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Discount type is required")
        DiscountType discountType,

        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount value must be greater than zero")
        BigDecimal discountValue,

        BigDecimal maxDiscountAmount,

        BigDecimal minOrderValue,

        @NotNull(message = "Usage limit is required")
        @Min(value = 1, message = "Usage limit must be at least 1")
        Integer usageLimit,

        @Min(value = 1, message = "Usage limit per user must be at least 1")
        Integer usageLimitPerUser,

        Boolean isPublic,

        @NotNull(message = "Start date is required")
        OffsetDateTime startDate,

        @NotNull(message = "End date is required")
        OffsetDateTime endDate
) {}
