package com.aitechmarketplace.backend.payment.provider.momo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class MomoUtils {

    private MomoUtils() {}

    public static String hmacSHA256(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key and data must not be null");
            }
            Mac hmac256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac256.init(secretKey);
            byte[] result = hmac256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC SHA256", ex);
        }
    }

    public static String buildCreatePaymentSignature(
            String accessKey,
            long amount,
            String extraData,
            String ipnUrl,
            String orderId,
            String orderInfo,
            String partnerCode,
            String redirectUrl,
            String requestId,
            String requestType,
            String secretKey
    ) {
        String raw = "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + (extraData != null ? extraData : "") +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + requestType;
        return hmacSHA256(secretKey, raw);
    }

    public static String buildIpnSignature(
            String accessKey,
            String amount,
            String extraData,
            String message,
            String orderId,
            String orderInfo,
            String orderType,
            String partnerCode,
            String payType,
            String requestId,
            String responseTime,
            String resultCode,
            String transId,
            String secretKey
    ) {
        String raw = "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + (extraData != null ? extraData : "") +
                "&message=" + message +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&orderType=" + orderType +
                "&partnerCode=" + partnerCode +
                "&payType=" + payType +
                "&requestId=" + requestId +
                "&responseTime=" + responseTime +
                "&resultCode=" + resultCode +
                "&transId=" + transId;
        return hmacSHA256(secretKey, raw);
    }
}
