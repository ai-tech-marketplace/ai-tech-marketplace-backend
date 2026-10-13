package com.aitechmarketplace.backend.auth.dto;

import com.aitechmarketplace.backend.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String role
) {
    public static UserResponse from(User user) {
        String primaryRole = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .findFirst()
                .orElse("CUSTOMER");

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                primaryRole
        );
    }
}