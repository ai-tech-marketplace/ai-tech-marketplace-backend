package com.aitechmarketplace.backend.payment.dto.momo;

public record MomoCreatePaymentRequest(
        String partnerCode,
        String requestId,
        long amount,
        String orderId,
        String orderInfo,
        String redirectUrl,
        String ipnUrl,
        String requestType,
        String extraData,
        String lang,
        String signature
) {}
