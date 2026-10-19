package com.aitechmarketplace.backend.payment.controller;

import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateRequest;
import com.aitechmarketplace.backend.payment.dto.PaymentInitiateResponse;
import com.aitechmarketplace.backend.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderId}/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
            @RequestAttribute("userId") Long userId,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody PaymentInitiateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = extractClientIp(httpServletRequest);
        PaymentInitiateResponse response = paymentService.initiatePayment(userId, orderId, request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, Object>> handleVnpayIpn(@RequestParam Map<String, String> queryParams) {
        Map<String, Object> response = paymentService.handleVnpayIpn(queryParams);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> handleVnpayReturn(@RequestParam Map<String, String> queryParams) {
        String txnRef = queryParams.getOrDefault("vnp_TxnRef", "");
        String orderNumber = txnRef.contains("_PAY_") ? txnRef.substring(0, txnRef.indexOf("_PAY_")) : txnRef;
        String status = "00".equals(queryParams.get("vnp_ResponseCode")) ? "SUCCESS" : "FAILED";
        String redirectUrl = "http://localhost:3000/checkout/result?orderNumber=" + orderNumber + "&status=" + status;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @PostMapping("/momo/ipn")
    public ResponseEntity<Map<String, Object>> handleMomoIpn(
            @RequestParam Map<String, String> queryParams,
            @RequestBody(required = false) String rawPayload
    ) {
        Map<String, Object> response = paymentService.handleMomoIpn(queryParams, rawPayload);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/momo/return")
    public ResponseEntity<Void> handleMomoReturn(@RequestParam Map<String, String> queryParams) {
        String orderId = queryParams.getOrDefault("orderId", "");
        String orderNumber = orderId.contains("_PAY_") ? orderId.substring(0, orderId.indexOf("_PAY_"))
                : (orderId.contains("_") ? orderId.split("_")[0] : orderId);
        String status = "0".equals(queryParams.get("resultCode")) ? "SUCCESS" : "FAILED";
        String redirectUrl = "http://localhost:3000/checkout/result?orderNumber=" + orderNumber + "&status=" + status;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader
    ) {
        Map<String, Object> response = paymentService.handleStripeWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stripe/return")
    public ResponseEntity<Void> handleStripeReturn(
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "status", defaultValue = "SUCCESS") String status
    ) {
        String redirectUrl = "http://localhost:3000/checkout/result?orderNumber=" + (orderNumber != null ? orderNumber : "") + "&status=" + status;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        if (clientIp == null || clientIp.isBlank() || "0:0:0:0:0:0:0:1".equals(clientIp)) {
            clientIp = "127.0.0.1";
        }
        return clientIp;
    }
}
