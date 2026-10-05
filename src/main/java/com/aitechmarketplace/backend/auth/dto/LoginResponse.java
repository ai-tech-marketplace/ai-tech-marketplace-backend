package com.aitechmarketplace.backend.auth.dto;

import java.util.List;

public record LoginResponse(
    String token,
    String tokenType,
    Long userId,
    String email,
    List<String> roles
) {
}