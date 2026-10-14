package com.aitechmarketplace.backend.order.dto;

import com.aitechmarketplace.backend.order.entity.Order;

public record ShippingAddressDto(
        String recipientName,
        String phone,
        String addressLine,
        String ward,
        String district,
        String city,
        String country
) {
    public static ShippingAddressDto from(Order order) {
        return new ShippingAddressDto(
                order.getShippingRecipientName(),
                order.getShippingPhone(),
                order.getShippingAddress(),
                order.getShippingWard(),
                order.getShippingDistrict(),
                order.getShippingCity(),
                order.getShippingCountry()
        );
    }
}
