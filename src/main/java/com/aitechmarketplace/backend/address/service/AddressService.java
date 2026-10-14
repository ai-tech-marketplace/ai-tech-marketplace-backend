package com.aitechmarketplace.backend.address.service;

import com.aitechmarketplace.backend.address.dto.AddressCreateRequest;
import com.aitechmarketplace.backend.address.dto.AddressResponse;
import com.aitechmarketplace.backend.address.dto.AddressUpdateRequest;

import java.util.List;

public interface AddressService {

    List<AddressResponse> getCustomerAddresses(Long userId);

    AddressResponse createAddress(Long userId, AddressCreateRequest request);

    AddressResponse updateAddress(Long userId, Long addressId, AddressUpdateRequest request);

    void deleteAddress(Long userId, Long addressId);

    AddressResponse setDefaultAddress(Long userId, Long addressId);
}
