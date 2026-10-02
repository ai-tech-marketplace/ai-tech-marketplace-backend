package com.aitechmarketplace.backend.user.service;

import com.aitechmarketplace.backend.user.entity.User;

import java.util.Optional;

public interface UserService {

    User register(
        String email,
        String passwordHash,
        String firstName,
        String lastName
    );

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}