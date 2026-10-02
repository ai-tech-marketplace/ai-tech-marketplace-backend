package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.LoginRequest;
import com.aitechmarketplace.backend.auth.dto.LoginResponse;
import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.auth.security.JwtService;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        String passwordHash = passwordEncoder.encode(
            request.getPassword()
        );

        User user = userService.register(
            request.getEmail(),
            passwordHash,
            request.getFirstName(),
            request.getLastName()
        );

        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        User user = userService.findByEmail(request.email())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Invalid email or password"
                )
            );

        if (!passwordEncoder.matches(
            request.password(),
            user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                "Invalid email or password"
            );
        }

        if (!user.isActive()) {
            throw new IllegalStateException(
                "User account is inactive"
            );
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
            token,
            "Bearer",
            user.getId(),
            user.getEmail(),
            user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .toList()
        );
    }
}