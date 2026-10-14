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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
    }

    @Test
    void createAddress_whenFirstAddress_shouldAutomaticallyBeDefault() {
        AddressCreateRequest request = new AddressCreateRequest(
                "John Doe", "0900000000", "123 Example Street",
                "Ward 1", "District 1", "Hanoi", "Vietnam", false
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.countByUserId(1L)).thenReturn(0);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 10L);
            return a;
        });

        AddressResponse response = addressService.createAddress(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.recipientName()).isEqualTo("John Doe");
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void createAddress_whenAddressLimitExceeded_shouldThrowBadRequestException() {
        AddressCreateRequest request = new AddressCreateRequest(
                "John Doe", "0900000000", "123 Example Street",
                "Ward 1", "District 1", "Hanoi", "Vietnam", false
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.countByUserId(1L)).thenReturn(10);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                addressService.createAddress(1L, request)
        );

        assertThat(ex.getErrorCode()).isEqualTo("ADDRESS_LIMIT_EXCEEDED");
        verify(addressRepository, never()).save(any());
    }

    @Test
    void createAddress_whenNewAddressIsDefault_shouldClearPreviousDefault() {
        AddressCreateRequest request = new AddressCreateRequest(
                "Office", "0900000001", "456 Office Tower",
                "Ward 2", "District 2", "Hanoi", "Vietnam", true
        );

        Address previousDefault = new Address();
        previousDefault.setId(5L);
        previousDefault.setDefault(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.countByUserId(1L)).thenReturn(1);
        when(addressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(previousDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", 11L);
            }
            return a;
        });

        AddressResponse response = addressService.createAddress(1L, request);

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.isDefault()).isTrue();
        assertThat(previousDefault.isDefault()).isFalse();
        verify(addressRepository).save(previousDefault);
    }

    @Test
    void updateAddress_whenNotFound_shouldThrowNotFoundException() {
        AddressUpdateRequest request = new AddressUpdateRequest(
                "Updated", "0900000000", "123 Example Street",
                "Ward 1", "District 1", "Hanoi", "Vietnam", false
        );

        when(addressRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                addressService.updateAddress(1L, 99L, request)
        );

        assertThat(ex.getErrorCode()).isEqualTo("ADDRESS_NOT_FOUND");
    }

    @Test
    void setDefaultAddress_shouldSetSelectedAndClearPrevious() {
        Address currentDefault = new Address();
        currentDefault.setId(5L);
        currentDefault.setDefault(true);

        Address targetAddress = new Address();
        targetAddress.setId(8L);
        targetAddress.setDefault(false);
        targetAddress.setRecipientName("Target Recipient");
        targetAddress.setPhone("0900000000");
        targetAddress.setAddressLine("Target Address Line");
        targetAddress.setCity("Hanoi");
        targetAddress.setCountry("Vietnam");

        when(addressRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(targetAddress));
        when(addressRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(currentDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.setDefaultAddress(1L, 8L);

        assertThat(response.isDefault()).isTrue();
        assertThat(currentDefault.isDefault()).isFalse();
        verify(addressRepository).save(currentDefault);
        verify(addressRepository).save(targetAddress);
    }

    @Test
    void deleteAddress_shouldInvokeRepositoryDelete() {
        Address address = new Address();
        address.setId(10L);

        when(addressRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(address));

        addressService.deleteAddress(1L, 10L);

        verify(addressRepository).delete(address);
    }

    @Test
    void getCustomerAddresses_shouldReturnMappedList() {
        Address a1 = new Address();
        a1.setId(1L);
        a1.setRecipientName("User 1");
        a1.setPhone("0900000000");
        a1.setAddressLine("Line 1");
        a1.setCity("Hanoi");
        a1.setCountry("Vietnam");

        when(addressRepository.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of(a1));

        List<AddressResponse> list = addressService.getCustomerAddresses(1L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(1L);
    }
}
