package com.aitechmarketplace.backend.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressUpdateRequest(
        @NotBlank(message = "Recipient name is required")
        @Size(min = 2, max = 100, message = "Recipient name must be between 2 and 100 characters")
        String recipientName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be between 10 and 15 digits")
        String phone,

        @NotBlank(message = "Address line is required")
        @Size(min = 5, max = 255, message = "Address line must be between 5 and 255 characters")
        String addressLine,

        @Size(max = 100, message = "Ward must not exceed 100 characters")
        String ward,

        @Size(max = 100, message = "District must not exceed 100 characters")
        String district,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        Boolean isDefault
) {
        public String resolveCountry() {
                return (country != null && !country.isBlank()) ? country : "Vietnam";
        }

        public boolean resolveIsDefault() {
                return isDefault != null && isDefault;
        }
}
