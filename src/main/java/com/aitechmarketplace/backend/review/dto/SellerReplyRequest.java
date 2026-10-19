package com.aitechmarketplace.backend.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerReplyRequest(
        @NotBlank(message = "Reply text is required")
        @Size(max = 2000, message = "Reply must not exceed 2000 characters")
        String reply
) {}
