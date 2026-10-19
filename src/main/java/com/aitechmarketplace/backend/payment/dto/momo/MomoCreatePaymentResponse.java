package com.aitechmarketplace.backend.payment.dto.momo;

public record MomoCreatePaymentResponse(
        String partnerCode,
        String orderId,
        String requestId,
        long amount,
        long responseTime,
        String message,
        int resultCode,
        String payUrl,
        String deeplink,
        String qrCodeUrl
) {}
