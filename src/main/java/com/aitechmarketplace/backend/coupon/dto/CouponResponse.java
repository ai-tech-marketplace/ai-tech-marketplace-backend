package com.aitechmarketplace.backend.coupon.dto;

import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponResponse(
        Long id,
        String code,
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderValue,
        Integer usageLimit,
        Integer usedCount,
        Integer usageLimitPerUser,
        boolean isPublic,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getName(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderValue(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.getUsageLimitPerUser(),
                coupon.isPublic(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getStatus(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
