package com.aitechmarketplace.backend.order.dto;

import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
        @NotNull(message = "Address ID is required")
        Long addressId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        String couponCode,

        String notes
) {}
