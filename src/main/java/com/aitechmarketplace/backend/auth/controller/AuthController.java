package com.aitechmarketplace.backend.auth.controller;

import com.aitechmarketplace.backend.auth.dto.LoginRequest;
import com.aitechmarketplace.backend.auth.dto.LoginResponse;
import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.auth.service.AuthService;
import com.aitechmarketplace.backend.common.dto.ApiResponse;
import com.aitechmarketplace.backend.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
        AuthService authService
    ) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse response = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(
            ApiResponse.success(response, "Login successful")
        );
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(
        @AuthenticationPrincipal User user
    ) {
        UserResponse response = UserResponse.from(user);
        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }
}