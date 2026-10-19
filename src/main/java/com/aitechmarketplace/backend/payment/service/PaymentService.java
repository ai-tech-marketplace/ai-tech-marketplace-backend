package com.aitechmarketplace.backend.payment.service;

import com.aitechmarketplace.backend.payment.dto.PaymentInitiateRequest;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;

import java.util.Map;

public interface PaymentService {

    PaymentInitiateResponse initiatePayment(Long userId, Long orderId, PaymentInitiateRequest request, String clientIp);

    Map<String, Object> handleVnpayIpn(Map<String, String> queryParams);

    Map<String, Object> handleMomoIpn(Map<String, String> queryParams, String rawPayload);

    Map<String, Object> handleStripeWebhook(String rawPayload, String signatureHeader);
}
