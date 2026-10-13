package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.LoginRequest;
import com.aitechmarketplace.backend.auth.dto.LoginResponse;
import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.auth.security.JwtService;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("12345678");
        request.setFullName("Test User");
        request.setPhone("0123456789");
    }

    @Test
    void register_shouldHashPasswordAndCreateUser() {
        String passwordHash = "$2a$10$hashed-password";

        when(passwordEncoder.encode("12345678")).thenReturn(passwordHash);

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordHash);
        user.setFullName("Test User");
        user.setPhone("0123456789");

        when(userService.register(
            "test@example.com",
            passwordHash,
            "Test User",
            "0123456789"
        )).thenReturn(user);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.email());
        assertEquals("Test User", response.fullName());

        verify(passwordEncoder).encode("12345678");
        verify(userService).register(
            "test@example.com",
            passwordHash,
            "Test User",
            "0123456789"
        );
    }

    @Test
    void register_shouldPassHashedPasswordToUserService() {
        String passwordHash = "$2a$10$hashed-password";

        when(passwordEncoder.encode("12345678")).thenReturn(passwordHash);

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordHash);

        when(userService.register(
            anyString(),
            anyString(),
            anyString(),
            anyString()
        )).thenReturn(user);

        authService.register(request);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);

        verify(userService).register(
            eq("test@example.com"),
            passwordCaptor.capture(),
            eq("Test User"),
            eq("0123456789")
        );

        assertEquals(passwordHash, passwordCaptor.getValue());
    }

    @Test
    void login_shouldReturnJwtToken() {
        LoginRequest loginRequest = new LoginRequest(
            "test@example.com",
            "password123"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setFullName("Test User");
        user.setStatus("ACTIVE");

        Role role = new Role();
        role.setName(RoleName.CUSTOMER);
        user.getRoles().add(role);

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("password123", "hashed-password"))
            .thenReturn(true);

        when(jwtService.generateToken(user))
            .thenReturn("jwt-token");

        LoginResponse response = authService.login(loginRequest);

        assertEquals("jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertNotNull(response.refreshToken());
        assertEquals(3600, response.expiresIn());
        assertEquals("test@example.com", response.user().email());
        assertEquals("CUSTOMER", response.user().role());

        verify(passwordEncoder).matches("password123", "hashed-password");
        verify(jwtService).generateToken(user);
    }

    @Test
    void login_withUnknownEmail_shouldThrowException() {
        LoginRequest loginRequest = new LoginRequest(
            "unknown@example.com",
            "password123"
        );

        when(userService.findByEmail("unknown@example.com"))
            .thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.login(loginRequest)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getErrorCode());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_withWrongPassword_shouldThrowException() {
        LoginRequest loginRequest = new LoginRequest(
            "test@example.com",
            "wrong-password"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus("ACTIVE");

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("wrong-password", "hashed-password"))
            .thenReturn(false);

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.login(loginRequest)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getErrorCode());

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_withInactiveUser_shouldThrowException() {
        LoginRequest loginRequest = new LoginRequest(
            "test@example.com",
            "password123"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus("INACTIVE");

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("password123", "hashed-password"))
            .thenReturn(true);

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.login(loginRequest)
        );

        assertEquals("User account is inactive", exception.getMessage());
        assertEquals("AUTH_ACCOUNT_INACTIVE", exception.getErrorCode());

        verify(jwtService, never()).generateToken(any());
    }
}