package com.aitechmarketplace.backend.user.service;

import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role(1L, RoleName.USER);
    }

    @Test
    void register_shouldCreateUserSuccessfully() {
        String email = "test@example.com";
        String passwordHash = "hashed-password";

        when(userRepository.existsByEmail(email))
            .thenReturn(false);

        when(roleRepository.findByName(RoleName.USER))
            .thenReturn(Optional.of(userRole));

        User savedUser = new User();
        savedUser.setEmail(email);
        savedUser.setPasswordHash(passwordHash);

        when(userRepository.save(any(User.class)))
            .thenReturn(savedUser);

        User result = userService.register(
            email,
            passwordHash,
            "Test",
            "User"
        );

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(passwordHash, result.getPasswordHash());

        verify(userRepository)
            .existsByEmail(email);

        verify(roleRepository)
            .findByName(RoleName.USER);

        verify(userRepository)
            .save(any(User.class));
    }

    @Test
    void register_shouldThrowExceptionWhenEmailAlreadyExists() {
        String email = "existing@example.com";

        when(userRepository.existsByEmail(email))
            .thenReturn(true);

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(
                    email,
                    "hashed-password",
                    "Test",
                    "User"
                )
            );

        assertEquals(
            "Email already exists",
            exception.getMessage()
        );

        verify(userRepository)
            .existsByEmail(email);

        verify(roleRepository, never())
            .findByName(any());

        verify(userRepository, never())
            .save(any());
    }

    @Test
    void register_shouldThrowExceptionWhenUserRoleDoesNotExist() {
        String email = "test@example.com";

        when(userRepository.existsByEmail(email))
            .thenReturn(false);

        when(roleRepository.findByName(RoleName.USER))
            .thenReturn(Optional.empty());

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> userService.register(
                    email,
                    "hashed-password",
                    "Test",
                    "User"
                )
            );

        assertEquals(
            "Default USER role not found",
            exception.getMessage()
        );

        verify(userRepository)
            .existsByEmail(email);

        verify(roleRepository)
            .findByName(RoleName.USER);

        verify(userRepository, never())
            .save(any());
    }
}
