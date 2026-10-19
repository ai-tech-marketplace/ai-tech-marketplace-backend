package com.aitechmarketplace.backend.payment.dto;

import java.math.BigDecimal;

public record PaymentVerificationResult(
        boolean isValidSignature,
        boolean isSuccess,
        String transactionReference,
        String providerTransactionId,
        BigDecimal amount,
        String responseCode,
        String message,
        String rawResponse
) {}
