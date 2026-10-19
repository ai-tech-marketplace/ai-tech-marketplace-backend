package com.aitechmarketplace.backend.payment.provider;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.config.VnpayProperties;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.provider.vnpay.VnpayProvider;
import com.aitechmarketplace.backend.payment.provider.vnpay.VnpayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayProviderTest {

    private VnpayProperties properties;
    private VnpayProvider provider;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setTmnCode("TESTTMN1");
        properties.setHashSecret("TESTSECRETKEY1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF12");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:3000/checkout/result");

        provider = new VnpayProvider(properties);
    }

    @Test
    void testGetSupportedMethod() {
        assertThat(provider.getSupportedMethod()).isEqualTo(PaymentMethod.VNPAY);
    }

    @Test
    void testInitiatePayment_GeneratesValidUrlWithChecksum() {
        Order order = new Order();
        order.setOrderNumber("ORD-123456");
        order.setTotalAmount(new BigDecimal("250000.00"));

        Payment payment = new Payment();
        payment.setTransactionReference("ORD-123456_P1_1729000000");

        PaymentInitiateResponse response = provider.initiatePayment(order, payment, "127.0.0.1", null);

        assertThat(response).isNotNull();
        assertThat(response.transactionReference()).isEqualTo("ORD-123456_P1_1729000000");
        assertThat(response.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(response.paymentUrl()).contains("vnp_TmnCode=TESTTMN1");
        assertThat(response.paymentUrl()).contains("vnp_Amount=25000000"); // 250,000 * 100
        assertThat(response.paymentUrl()).contains("vnp_TxnRef=ORD-123456_P1_1729000000");
        assertThat(response.paymentUrl()).contains("vnp_SecureHash=");
    }

    @Test
    void testVerifyCallback_ValidChecksumAndSuccessResponse() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_TmnCode", "TESTTMN1");
        queryParams.put("vnp_Amount", "25000000");
        queryParams.put("vnp_TxnRef", "ORD-123456_P1_1729000000");
        queryParams.put("vnp_TransactionNo", "14299912");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");
        queryParams.put("vnp_OrderInfo", "Thanh toan don hang");

        String hashData = VnpayUtils.buildHashData(queryParams);
        String secureHash = VnpayUtils.hmacSHA512(properties.getHashSecret(), hashData);
        queryParams.put("vnp_SecureHash", secureHash);

        PaymentVerificationResult result = provider.verifyCallback(queryParams, null, null);

        assertThat(result.isValidSignature()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.transactionReference()).isEqualTo("ORD-123456_P1_1729000000");
        assertThat(result.providerTransactionId()).isEqualTo("14299912");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(result.responseCode()).isEqualTo("00");
    }

    @Test
    void testVerifyCallback_TamperedChecksum_FailsVerification() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_TmnCode", "TESTTMN1");
        queryParams.put("vnp_Amount", "25000000");
        queryParams.put("vnp_TxnRef", "ORD-123456_P1_1729000000");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");
        queryParams.put("vnp_SecureHash", "INVALID_TAMPERED_CHECKSUM");

        PaymentVerificationResult result = provider.verifyCallback(queryParams, null, null);

        assertThat(result.isValidSignature()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void testVerifyCallback_ValidChecksumButFailedCode_ReportsFailure() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_TmnCode", "TESTTMN1");
        queryParams.put("vnp_Amount", "25000000");
        queryParams.put("vnp_TxnRef", "ORD-123456_P1_1729000000");
        queryParams.put("vnp_TransactionNo", "14299912");
        queryParams.put("vnp_ResponseCode", "24"); // Customer canceled
        queryParams.put("vnp_TransactionStatus", "02"); // Failed

        String hashData = VnpayUtils.buildHashData(queryParams);
        String secureHash = VnpayUtils.hmacSHA512(properties.getHashSecret(), hashData);
        queryParams.put("vnp_SecureHash", secureHash);

        PaymentVerificationResult result = provider.verifyCallback(queryParams, null, null);

        assertThat(result.isValidSignature()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.responseCode()).isEqualTo("24");
    }
}
