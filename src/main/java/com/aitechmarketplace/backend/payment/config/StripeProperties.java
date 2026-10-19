package com.aitechmarketplace.backend.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payment.stripe")
public class StripeProperties {

    private String apiKey = "sk_test_mock_stripe_key_1234567890";
    private String webhookSecret = "whsec_mock_stripe_secret_1234567890";
    private String successUrl = "http://localhost:3000/checkout/result?orderNumber={ORDER_NUMBER}&status=SUCCESS";
    private String cancelUrl = "http://localhost:3000/checkout/result?orderNumber={ORDER_NUMBER}&status=CANCELLED";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
