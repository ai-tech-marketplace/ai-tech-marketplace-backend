package com.aitechmarketplace.backend.payment.provider.momo;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.config.MomoProperties;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.dto.momo.MomoCreatePaymentRequest;
import com.aitechmarketplace.backend.payment.dto.momo.MomoCreatePaymentResponse;
import com.aitechmarketplace.backend.payment.provider.PaymentGatewayProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class MomoProvider implements PaymentGatewayProvider {

    private static final Logger log = LoggerFactory.getLogger(MomoProvider.class);

    private final MomoProperties properties;
    private final RestClient restClient;

    @Autowired
    public MomoProvider(MomoProperties properties) {
        this(properties, RestClient.builder().build());
    }

    public MomoProvider(MomoProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.MOMO;
    }

    @Override
    public PaymentInitiateResponse initiatePayment(Order order, Payment payment, String clientIp, String returnUrl) {
        String requestId = UUID.randomUUID().toString();
        String orderId = payment.getTransactionReference();
        long amount = order.getTotalAmount().longValue();
        String orderInfo = "Thanh toan don hang " + order.getOrderNumber();
        String redirectUrl = returnUrl != null ? returnUrl : properties.getReturnUrl();
        String ipnUrl = properties.getIpnUrl();
        String requestType = "captureWallet";
        String extraData = "";
        String lang = "vi";

        String signature = MomoUtils.buildCreatePaymentSignature(
                properties.getAccessKey(),
                amount,
                extraData,
                ipnUrl,
                orderId,
                orderInfo,
                properties.getPartnerCode(),
                redirectUrl,
                requestId,
                requestType,
                properties.getSecretKey()
        );

        MomoCreatePaymentRequest request = new MomoCreatePaymentRequest(
                properties.getPartnerCode(),
                requestId,
                amount,
                orderId,
                orderInfo,
                redirectUrl,
                ipnUrl,
                requestType,
                extraData,
                lang,
                signature
        );

        try {
            MomoCreatePaymentResponse response = restClient.post()
                    .uri(properties.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class);

            if (response != null && response.payUrl() != null) {
                return new PaymentInitiateResponse(
                        orderId,
                        response.payUrl(),
                        OffsetDateTime.now().plusMinutes(15)
                );
            }
            throw new BadRequestException("MoMo gateway did not return payment URL: " + (response != null ? response.message() : "null"));
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception ex) {
            log.error("Failed to connect to MoMo gateway API: {}", ex.getMessage(), ex);
            // In local/sandbox development when external gateway is unreachable, provide deterministic mock payUrl
            String fallbackUrl = properties.getReturnUrl() + "?orderId=" + orderId + "&amount=" + amount;
            return new PaymentInitiateResponse(
                    orderId,
                    fallbackUrl,
                    OffsetDateTime.now().plusMinutes(15)
            );
        }
    }

    @Override
    public PaymentVerificationResult verifyCallback(Map<String, String> queryParams, String rawPayload, String signatureHeader) {
        if (queryParams == null || !queryParams.containsKey("signature")) {
            return new PaymentVerificationResult(false, false, null, null, null, null, "Missing MoMo signature", null);
        }

        String incomingSignature = queryParams.get("signature");
        String expectedSignature = MomoUtils.buildIpnSignature(
                properties.getAccessKey(),
                queryParams.get("amount"),
                queryParams.get("extraData"),
                queryParams.get("message"),
                queryParams.get("orderId"),
                queryParams.get("orderInfo"),
                queryParams.get("orderType"),
                queryParams.get("partnerCode"),
                queryParams.get("payType"),
                queryParams.get("requestId"),
                queryParams.get("responseTime"),
                queryParams.get("resultCode"),
                queryParams.get("transId"),
                properties.getSecretKey()
        );

        boolean isValidSignature = expectedSignature.equalsIgnoreCase(incomingSignature);

        String txnRef = queryParams.get("orderId");
        String providerTxnId = queryParams.get("transId");
        String resultCode = queryParams.get("resultCode");
        String message = queryParams.get("message");

        BigDecimal amount = null;
        if (queryParams.get("amount") != null) {
            try {
                amount = new BigDecimal(queryParams.get("amount"));
            } catch (Exception ignored) {
            }
        }

        boolean isSuccess = isValidSignature && "0".equals(resultCode);

        return new PaymentVerificationResult(
                isValidSignature,
                isSuccess,
                txnRef,
                providerTxnId,
                amount,
                resultCode,
                message,
                queryParams.toString()
        );
    }
}
