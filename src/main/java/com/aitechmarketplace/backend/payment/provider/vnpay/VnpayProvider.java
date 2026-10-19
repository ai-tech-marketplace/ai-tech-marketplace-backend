package com.aitechmarketplace.backend.payment.provider.vnpay;

import com.aitechmarketplace.backend.order.entity.Order;
import com.aitechmarketplace.backend.order.entity.Payment;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import com.aitechmarketplace.backend.payment.config.VnpayProperties;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentVerificationResult;
import com.aitechmarketplace.backend.payment.provider.PaymentGatewayProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class VnpayProvider implements PaymentGatewayProvider {

    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnpayProperties properties;

    public VnpayProvider(VnpayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public PaymentInitiateResponse initiatePayment(Order order, Payment payment, String clientIp, String returnUrl) {
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime expireTime = now.plusMinutes(15);

        BigDecimal vnpAmount = order.getTotalAmount().multiply(new BigDecimal(100));

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", properties.getTmnCode());
        vnpParams.put("vnp_Amount", vnpAmount.toBigInteger().toString());
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getTransactionReference());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderNumber());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl != null ? returnUrl : properties.getReturnUrl());
        vnpParams.put("vnp_IpAddr", clientIp != null ? clientIp : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", now.format(VNP_DATE_FORMAT));
        vnpParams.put("vnp_ExpireDate", expireTime.format(VNP_DATE_FORMAT));

        String hashData = VnpayUtils.buildHashData(vnpParams);
        String secureHash = VnpayUtils.hmacSHA512(properties.getHashSecret(), hashData);
        String paymentUrl = VnpayUtils.buildQueryUrl(properties.getPayUrl(), vnpParams, secureHash);

        return new PaymentInitiateResponse(
                payment.getTransactionReference(),
                paymentUrl,
                OffsetDateTime.now().plusMinutes(15)
        );
    }

    @Override
    public PaymentVerificationResult verifyCallback(Map<String, String> queryParams, String rawPayload, String signatureHeader) {
        if (queryParams == null || !queryParams.containsKey("vnp_SecureHash")) {
            return new PaymentVerificationResult(false, false, null, null, null, null, "Missing vnp_SecureHash", null);
        }

        String incomingSecureHash = queryParams.get("vnp_SecureHash");
        String hashData = VnpayUtils.buildHashData(queryParams);
        String expectedHash = VnpayUtils.hmacSHA512(properties.getHashSecret(), hashData);

        boolean isValidSignature = expectedHash.equalsIgnoreCase(incomingSecureHash);

        String txnRef = queryParams.get("vnp_TxnRef");
        String providerTxnId = queryParams.get("vnp_TransactionNo");
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");

        BigDecimal amount = null;
        if (queryParams.get("vnp_Amount") != null) {
            try {
                amount = new BigDecimal(queryParams.get("vnp_Amount")).divide(new BigDecimal(100));
            } catch (Exception ignored) {
            }
        }

        boolean isSuccess = isValidSignature && "00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null);
        String message = isSuccess ? "Confirm Success" : "Payment Failed or Canceled (Code: " + responseCode + ")";

        return new PaymentVerificationResult(
                isValidSignature,
                isSuccess,
                txnRef,
                providerTxnId,
                amount,
                responseCode,
                message,
                queryParams.toString()
        );
    }
}
