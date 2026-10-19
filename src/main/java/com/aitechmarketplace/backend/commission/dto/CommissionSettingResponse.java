package com.aitechmarketplace.backend.commission.dto;

import com.aitechmarketplace.backend.commission.entity.CommissionScope;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CommissionSettingResponse(
        Long id,
        CommissionScope scope,
        Long targetId,
        String targetName,
        BigDecimal rate,
        String description,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
