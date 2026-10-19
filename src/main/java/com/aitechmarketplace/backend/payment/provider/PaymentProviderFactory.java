package com.aitechmarketplace.backend.payment.provider;

import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.order.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderFactory {

    private final Map<PaymentMethod, PaymentGatewayProvider> providerMap = new EnumMap<>(PaymentMethod.class);

    public PaymentProviderFactory(List<PaymentGatewayProvider> providers) {
        for (PaymentGatewayProvider provider : providers) {
            providerMap.put(provider.getSupportedMethod(), provider);
        }
    }

    public PaymentGatewayProvider getProvider(PaymentMethod method) {
        PaymentGatewayProvider provider = providerMap.get(method);
        if (provider == null) {
            throw new BadRequestException("Unsupported payment method: " + method);
        }
        return provider;
    }
}
