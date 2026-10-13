package com.aitechmarketplace.backend.user.service;

import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.user.entity.Role;
import com.aitechmarketplace.backend.user.entity.RoleName;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.RoleRepository;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserServiceImpl(
        UserRepository userRepository,
        RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public User register(
        String email,
        String passwordHash,
        String fullName,
        String phone
    ) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(
                "Email already exists",
                "USER_ALREADY_EXISTS"
            );
        }

        Role customerRole = roleRepository
            .findByName(RoleName.CUSTOMER)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Default CUSTOMER role not found"
                )
            );

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setStatus("ACTIVE");

        user.getRoles().add(customerRole);

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}