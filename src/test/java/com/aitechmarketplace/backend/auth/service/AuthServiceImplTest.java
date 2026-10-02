package com.aitechmarketplace.backend.auth.service;

import com.aitechmarketplace.backend.auth.dto.RegisterRequest;
import com.aitechmarketplace.backend.auth.dto.UserResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

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
}
