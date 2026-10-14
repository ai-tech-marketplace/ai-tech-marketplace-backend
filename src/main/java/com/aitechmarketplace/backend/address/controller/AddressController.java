package com.aitechmarketplace.backend.address.controller;

import com.aitechmarketplace.backend.address.dto.AddressCreateRequest;
import com.aitechmarketplace.backend.address.dto.AddressResponse;
import com.aitechmarketplace.backend.address.dto.AddressUpdateRequest;
import com.aitechmarketplace.backend.address.service.AddressService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @RequestAttribute("userId") Long userId
    ) {
        List<AddressResponse> addresses = addressService.getCustomerAddresses(userId);
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddressCreateRequest request
    ) {
        AddressResponse created = addressService.createAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Address created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddressUpdateRequest request
    ) {
        AddressResponse updated = addressService.updateAddress(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Address updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        addressService.deleteAddress(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        AddressResponse updated = addressService.setDefaultAddress(userId, id);
        return ResponseEntity.ok(ApiResponse.success(updated, "Default address set successfully"));
    }
}
