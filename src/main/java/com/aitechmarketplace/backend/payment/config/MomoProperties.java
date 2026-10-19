package com.aitechmarketplace.backend.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoProperties {

    private String partnerCode = "MOMOBKUN20180529";
    private String accessKey = "klm05TvNBzhg7h7j";
    private String secretKey = "at67qH6mk8w5Y1nAyMoYKMWACiEi2Aca";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private String returnUrl = "http://localhost:3000/checkout/result";
    private String ipnUrl = "http://localhost:8080/api/v1/payments/momo/ipn";

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getIpnUrl() {
        return ipnUrl;
    }

    public void setIpnUrl(String ipnUrl) {
        this.ipnUrl = ipnUrl;
    }
}
