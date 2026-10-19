package com.aitechmarketplace.backend.payment.dto;

import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentInitiateRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        String returnUrl
) {}
