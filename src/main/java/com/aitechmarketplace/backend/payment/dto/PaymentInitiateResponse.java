package com.aitechmarketplace.backend.payment.dto;

import java.time.OffsetDateTime;

public record PaymentInitiateResponse(
        String transactionReference,
        String paymentUrl,
        OffsetDateTime expiresAt
) {}
