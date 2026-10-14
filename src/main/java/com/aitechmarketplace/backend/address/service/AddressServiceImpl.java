package com.aitechmarketplace.backend.address.service;

import com.aitechmarketplace.backend.address.dto.AddressCreateRequest;
import com.aitechmarketplace.backend.address.dto.AddressResponse;
import com.aitechmarketplace.backend.address.dto.AddressUpdateRequest;
import com.aitechmarketplace.backend.address.entity.Address;
import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressServiceImpl(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getCustomerAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIdDesc(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    public AddressResponse createAddress(Long userId, AddressCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found", "USER_NOT_FOUND"));

        int currentCount = addressRepository.countByUserId(userId);
        if (currentCount >= MAX_ADDRESSES) {
            throw new BadRequestException("Maximum of 10 addresses allowed per user", "ADDRESS_LIMIT_EXCEEDED");
        }

        boolean shouldBeDefault = (currentCount == 0) || request.resolveIsDefault();

        if (shouldBeDefault) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        addressRepository.save(existingDefault);
                    });
        }

        Address address = new Address();
        address.setUser(user);
        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setAddressLine(request.addressLine());
        address.setWard(request.ward());
        address.setDistrict(request.district());
        address.setCity(request.city());
        address.setCountry(request.resolveCountry());
        address.setDefault(shouldBeDefault);

        Address saved = addressRepository.save(address);
        return AddressResponse.from(saved);
    }

    @Override
    public AddressResponse updateAddress(Long userId, Long addressId, AddressUpdateRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address not found", "ADDRESS_NOT_FOUND"));

        boolean requestedDefault = request.resolveIsDefault();
        if (requestedDefault && !address.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        addressRepository.save(existingDefault);
                    });
            address.setDefault(true);
        } else if (!requestedDefault && address.isDefault()) {
            // Cannot unset default if it's the only address
            int count = addressRepository.countByUserId(userId);
            if (count > 1) {
                address.setDefault(false);
            }
        }

        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setAddressLine(request.addressLine());
        address.setWard(request.ward());
        address.setDistrict(request.district());
        address.setCity(request.city());
        address.setCountry(request.resolveCountry());

        Address updated = addressRepository.save(address);
        return AddressResponse.from(updated);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address not found", "ADDRESS_NOT_FOUND"));

        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUserIdOrderByIdDesc(userId);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Override
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address not found", "ADDRESS_NOT_FOUND"));

        if (!address.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        addressRepository.save(existingDefault);
                    });
            address.setDefault(true);
            address = addressRepository.save(address);
        }

        return AddressResponse.from(address);
    }
}
