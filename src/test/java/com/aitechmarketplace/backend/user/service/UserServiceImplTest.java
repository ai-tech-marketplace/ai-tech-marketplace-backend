package com.aitechmarketplace.backend.user.service;

import com.aitechmarketplace.backend.common.exception.ConflictException;
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

    private Role customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new Role(1L, RoleName.CUSTOMER);
    }

    @Test
    void register_shouldCreateUserSuccessfully() {
        String email = "test@example.com";
        String passwordHash = "hashed-password";
        String fullName = "Test User";
        String phone = "0123456789";

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customerRole));

        User savedUser = new User();
        savedUser.setEmail(email);
        savedUser.setPasswordHash(passwordHash);
        savedUser.setFullName(fullName);
        savedUser.setPhone(phone);
        savedUser.setStatus("ACTIVE");

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.register(email, passwordHash, fullName, phone);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(passwordHash, result.getPasswordHash());
        assertEquals(fullName, result.getFullName());
        assertEquals(phone, result.getPhone());
        assertEquals("ACTIVE", result.getStatus());

        verify(userRepository).existsByEmail(email);
        verify(roleRepository).findByName(RoleName.CUSTOMER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrowConflictExceptionWhenEmailAlreadyExists() {
        String email = "existing@example.com";

        when(userRepository.existsByEmail(email)).thenReturn(true);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> userService.register(email, "hashed-password", "Test User", "0123456789")
        );

        assertEquals("Email already exists", exception.getMessage());
        assertEquals("USER_ALREADY_EXISTS", exception.getErrorCode());

        verify(userRepository).existsByEmail(email);
        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowExceptionWhenCustomerRoleDoesNotExist() {
        String email = "test@example.com";

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.register(email, "hashed-password", "Test User", "0123456789")
        );

        assertEquals("Default CUSTOMER role not found", exception.getMessage());

        verify(userRepository).existsByEmail(email);
        verify(roleRepository).findByName(RoleName.CUSTOMER);
        verify(userRepository, never()).save(any());
    }
}
