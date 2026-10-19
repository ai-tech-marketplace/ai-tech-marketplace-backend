package com.aitechmarketplace.backend.payment.provider;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.config.StripeProperties;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.provider.stripe.StripeProvider;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StripeProviderTest {

    private StripeProperties properties;
    private StripeProvider provider;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setApiKey("sk_test_1234567890abcdef");
        properties.setWebhookSecret("whsec_test_secret_1234567890abcdef");
        properties.setSuccessUrl("http://localhost:3000/checkout/result?orderNumber={ORDER_NUMBER}&status=SUCCESS");
        properties.setCancelUrl("http://localhost:3000/checkout/result?orderNumber={ORDER_NUMBER}&status=CANCELLED");

        provider = new StripeProvider(properties);
    }

    @Test
    void testGetSupportedMethod() {
        assertThat(provider.getSupportedMethod()).isEqualTo(PaymentMethod.STRIPE);
    }

    @Test
    void testInitiatePayment_GeneratesPaymentInitiateResponse() {
        Order order = new Order();
        order.setOrderNumber("ORD-987654");
        order.setTotalAmount(new BigDecimal("1500000.00"));

        Payment payment = new Payment();
        payment.setTransactionReference("ORD-987654_P1_1729000000");

        PaymentInitiateResponse response = provider.initiatePayment(order, payment, "127.0.0.1", null);

        assertThat(response).isNotNull();
        assertThat(response.transactionReference()).isEqualTo("ORD-987654_P1_1729000000");
        assertThat(response.paymentUrl()).isNotBlank();
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void testVerifyCallback_MissingSignature_ReturnsInvalid() {
        PaymentVerificationResult result = provider.verifyCallback(null, "{}", null);

        assertThat(result.isValidSignature()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testVerifyCallback_TamperedSignature_ReturnsInvalid() {
        String payload = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\"}";
        String invalidSig = "t=1729000000,v1=invalidsignature";

        PaymentVerificationResult result = provider.verifyCallback(null, payload, invalidSig);

        assertThat(result.isValidSignature()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testVerifyCallback_ValidStripeSignature_ProcessesSessionCompleted() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000L;
        String payload = "{\"id\":\"evt_test_checkout\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_12345\",\"object\":\"checkout.session\",\"amount_total\":1500000,\"client_reference_id\":\"ORD-987654_P1_1729000000\",\"currency\":\"vnd\",\"payment_intent\":\"pi_test_abcdef\",\"status\":\"complete\"}}}";

        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(properties.getWebhookSecret(), signedPayload);
        String sigHeader = "t=" + timestamp + ",v1=" + signature;

        PaymentVerificationResult result = provider.verifyCallback(null, payload, sigHeader);

        assertThat(result.isValidSignature()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.transactionReference()).isEqualTo("ORD-987654_P1_1729000000");
        assertThat(result.providerTransactionId()).isEqualTo("pi_test_abcdef");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("1500000"));
    }
}
