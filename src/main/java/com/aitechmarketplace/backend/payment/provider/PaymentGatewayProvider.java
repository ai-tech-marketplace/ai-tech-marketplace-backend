package com.aitechmarketplace.backend.payment.provider;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;

import java.util.Map;

public interface PaymentGatewayProvider {

    PaymentMethod getSupportedMethod();

    PaymentInitiateResponse initiatePayment(Order order, Payment payment, String clientIp, String returnUrl);

    PaymentVerificationResult verifyCallback(Map<String, String> queryParams, String rawPayload, String signatureHeader);
}
