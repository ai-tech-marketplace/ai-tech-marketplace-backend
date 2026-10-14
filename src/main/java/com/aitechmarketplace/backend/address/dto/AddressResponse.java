package com.aitechmarketplace.backend.address.dto;

import com.aitechmarketplace.backend.address.entity.Address;

public record AddressResponse(
        Long id,
        String recipientName,
        String phone,
        String addressLine,
        String ward,
        String district,
        String city,
        String country,
        boolean isDefault
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getAddressLine(),
                address.getWard(),
                address.getDistrict(),
                address.getCity(),
                address.getCountry(),
                address.isDefault()
        );
    }
}
