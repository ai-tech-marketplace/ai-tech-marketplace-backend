package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);
}