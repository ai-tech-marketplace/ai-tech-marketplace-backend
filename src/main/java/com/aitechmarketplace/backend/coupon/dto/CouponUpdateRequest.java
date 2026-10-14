package com.aitechmarketplace.backend.coupon.dto;

import java.time.OffsetDateTime;

public record CouponUpdateRequest(
        String name,
        String description,
        Integer usageLimit,
        OffsetDateTime endDate
) {}
