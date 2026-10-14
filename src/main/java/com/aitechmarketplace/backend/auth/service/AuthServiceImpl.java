package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.LoginRequest;
import com.aitechmarketplace.backend.auth.dto.LoginResponse;
import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.auth.security.JwtService;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.UnauthorizedException;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = userService.register(
            request.getEmail(),
            passwordHash,
            request.getFullName(),
            request.getPhone()
        );

        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.email())
            .orElseThrow(() ->
                new UnauthorizedException(
                    "Invalid email or password",
                    "AUTH_INVALID_CREDENTIALS"
                )
            );

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(
                "Invalid email or password",
                "AUTH_INVALID_CREDENTIALS"
            );
        }

        if (!user.isActive()) {
            throw new BadRequestException(
                "User account is inactive",
                "AUTH_ACCOUNT_INACTIVE"
            );
        }

        String token = jwtService.generateToken(user);
        String refreshToken = UUID.randomUUID().toString();

        return new LoginResponse(
            token,
            refreshToken,
            "Bearer",
            3600,
            UserResponse.from(user)
        );
    }
}