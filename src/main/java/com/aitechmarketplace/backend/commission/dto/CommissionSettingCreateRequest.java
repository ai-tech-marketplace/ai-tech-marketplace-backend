package com.aitechmarketplace.backend.commission.dto;

import com.aitechmarketplace.backend.commission.entity.CommissionScope;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CommissionSettingCreateRequest(
        @NotNull(message = "Scope is required")
        CommissionScope scope,

        Long targetId,

        @NotNull(message = "Rate is required")
        @DecimalMin(value = "0.0000", message = "Rate must be at least 0.0000")
        @DecimalMax(value = "1.0000", message = "Rate must be at most 1.0000")
        BigDecimal rate,

        String description,

        Boolean isActive
) {
}
