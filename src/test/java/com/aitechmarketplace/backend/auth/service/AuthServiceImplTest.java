package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.LoginRequest;
import com.aitechmarketplace.backend.auth.dto.LoginResponse;
import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.auth.security.JwtService;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        request.setFirstName("Test");
        request.setLastName("User");
    }

    @Test
    void register_shouldHashPasswordAndCreateUser() {

        String passwordHash = "$2a$10$hashed-password";

        when(passwordEncoder.encode("12345678"))
            .thenReturn(passwordHash);

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordHash);
        user.setFirstName("Test");
        user.setLastName("User");

        when(userService.register(
            "test@example.com",
            passwordHash,
            "Test",
            "User"
        )).thenReturn(user);

        UserResponse response = authService.register(request);

        assertNotNull(response);

        assertEquals(
            "test@example.com",
            response.getEmail()
        );

        verify(passwordEncoder)
            .encode("12345678");

        verify(userService)
            .register(
                "test@example.com",
                passwordHash,
                "Test",
                "User"
            );
    }

    @Test
    void register_shouldPassHashedPasswordToUserService() {

        String passwordHash = "$2a$10$hashed-password";

        when(passwordEncoder.encode("12345678"))
            .thenReturn(passwordHash);

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

        ArgumentCaptor<String> passwordCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(userService).register(
            eq("test@example.com"),
            passwordCaptor.capture(),
            eq("Test"),
            eq("User")
        );

        assertEquals(
            passwordHash,
            passwordCaptor.getValue()
        );

        assertNotEquals(
            "12345678",
            passwordCaptor.getValue()
        );
    }

    @Test
    void login_shouldReturnJwtToken() {

        LoginRequest request = new LoginRequest(
            "test@example.com",
            "password123"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setActive(true);

        Role role = new Role();
        role.setName(RoleName.USER);
        user.getRoles().add(role);

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            "password123",
            "hashed-password"
        )).thenReturn(true);

        when(jwtService.generateToken(user))
            .thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertEquals(
            "jwt-token",
            response.token()
        );

        assertEquals(
            "Bearer",
            response.tokenType()
        );

        assertEquals(
            "test@example.com",
            response.email()
        );

        assertEquals(
            List.of("USER"),
            response.roles()
        );

        verify(passwordEncoder)
            .matches(
                "password123",
                "hashed-password"
            );

        verify(jwtService)
            .generateToken(user);
    }

    @Test
    void login_withUnknownEmail_shouldThrowException() {

        LoginRequest request = new LoginRequest(
            "unknown@example.com",
            "password123"
        );

        when(userService.findByEmail("unknown@example.com"))
            .thenReturn(Optional.empty());

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(request)
            );

        assertEquals(
            "Invalid email or password",
            exception.getMessage()
        );

        verify(passwordEncoder, never())
            .matches(anyString(), anyString());

        verify(jwtService, never())
            .generateToken(any());
    }

    @Test
    void login_withWrongPassword_shouldThrowException() {

        LoginRequest request = new LoginRequest(
            "test@example.com",
            "wrong-password"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setActive(true);

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            "wrong-password",
            "hashed-password"
        )).thenReturn(false);

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(request)
            );

        assertEquals(
            "Invalid email or password",
            exception.getMessage()
        );

        verify(jwtService, never())
            .generateToken(any());
    }

    @Test
    void login_withInactiveUser_shouldThrowException() {

        LoginRequest request = new LoginRequest(
            "test@example.com",
            "password123"
        );

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setActive(false);

        when(userService.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            "password123",
            "hashed-password"
        )).thenReturn(true);

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> authService.login(request)
            );

        assertEquals(
            "User account is inactive",
            exception.getMessage()
        );

        verify(jwtService, never())
            .generateToken(any());
    }
}