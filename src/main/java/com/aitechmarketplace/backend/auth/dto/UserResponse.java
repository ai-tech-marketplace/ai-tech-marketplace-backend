package com.aitechmarketplace.backend.auth.dto;

import com.aitechmarketplace.backend.user.entity.User;

import java.util.Set;
import java.util.stream.Collectors;

public class UserResponse {

    private Long id;

    private String email;

    private String firstName;

    private String lastName;

    private boolean active;

    private Set<String> roles;

    public UserResponse() {
    }

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();

        response.id = user.getId();
        response.email = user.getEmail();
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.active = user.isActive();

        response.roles = user.getRoles()
            .stream()
            .map(role -> role.getName().name())
            .collect(Collectors.toSet());

        return response;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isActive() {
        return active;
    }

    public Set<String> getRoles() {
        return roles;
    }
}