package com.aitechmarketplace.backend.payment.provider;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.config.MomoProperties;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.provider.momo.MomoProvider;
import com.aitechmarketplace.backend.payment.provider.momo.MomoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MomoProviderTest {

    private MomoProperties properties;
    private MomoProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MomoProperties();
        properties.setPartnerCode("MOMOTEST");
        properties.setAccessKey("TESTACCESSKEY");
        properties.setSecretKey("TESTSECRETKEY1234567890ABCDEF1234567890");
        properties.setEndpoint("https://test-payment.momo.vn/v2/gateway/api/create");
        properties.setReturnUrl("http://localhost:3000/checkout/result");
        properties.setIpnUrl("http://localhost:8080/api/v1/payments/momo/ipn");

        provider = new MomoProvider(properties, RestClient.builder().build());
    }

    @Test
    void testGetSupportedMethod() {
        assertThat(provider.getSupportedMethod()).isEqualTo(PaymentMethod.MOMO);
    }

    @Test
    void testVerifyCallback_ValidSignatureAndSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", properties.getPartnerCode());
        params.put("orderId", "ORD-123_P1_1729000000");
        params.put("requestId", "REQ-12345");
        params.put("amount", "350000");
        params.put("orderInfo", "Thanh toan don hang");
        params.put("orderType", "momo_wallet");
        params.put("transId", "2309192931");
        params.put("resultCode", "0");
        params.put("message", "Successful.");
        params.put("payType", "qr");
        params.put("responseTime", "1729000050000");
        params.put("extraData", "");

        String signature = MomoUtils.buildIpnSignature(
                properties.getAccessKey(),
                params.get("amount"),
                params.get("extraData"),
                params.get("message"),
                params.get("orderId"),
                params.get("orderInfo"),
                params.get("orderType"),
                params.get("partnerCode"),
                params.get("payType"),
                params.get("requestId"),
                params.get("responseTime"),
                params.get("resultCode"),
                params.get("transId"),
                properties.getSecretKey()
        );
        params.put("signature", signature);

        PaymentVerificationResult result = provider.verifyCallback(params, null, null);

        assertThat(result.isValidSignature()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.transactionReference()).isEqualTo("ORD-123_P1_1729000000");
        assertThat(result.providerTransactionId()).isEqualTo("2309192931");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("350000.00"));
        assertThat(result.responseCode()).isEqualTo("0");
    }

    @Test
    void testVerifyCallback_TamperedSignature_FailsVerification() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", properties.getPartnerCode());
        params.put("orderId", "ORD-123_P1_1729000000");
        params.put("requestId", "REQ-12345");
        params.put("amount", "350000");
        params.put("resultCode", "0");
        params.put("signature", "INVALID_TAMPERED_SIGNATURE");

        PaymentVerificationResult result = provider.verifyCallback(params, null, null);

        assertThat(result.isValidSignature()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testVerifyCallback_ValidSignatureButFailedResultCode_ReportsFailure() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", properties.getPartnerCode());
        params.put("orderId", "ORD-123_P1_1729000000");
        params.put("requestId", "REQ-12345");
        params.put("amount", "350000");
        params.put("orderInfo", "Thanh toan don hang");
        params.put("orderType", "momo_wallet");
        params.put("transId", "2309192931");
        params.put("resultCode", "1006"); // User denied payment
        params.put("message", "User denied transaction");
        params.put("payType", "qr");
        params.put("responseTime", "1729000050000");
        params.put("extraData", "");

        String signature = MomoUtils.buildIpnSignature(
                properties.getAccessKey(),
                params.get("amount"),
                params.get("extraData"),
                params.get("message"),
                params.get("orderId"),
                params.get("orderInfo"),
                params.get("orderType"),
                params.get("partnerCode"),
                params.get("payType"),
                params.get("requestId"),
                params.get("responseTime"),
                params.get("resultCode"),
                params.get("transId"),
                properties.getSecretKey()
        );
        params.put("signature", signature);

        PaymentVerificationResult result = provider.verifyCallback(params, null, null);

        assertThat(result.isValidSignature()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.responseCode()).isEqualTo("1006");
    }
}
