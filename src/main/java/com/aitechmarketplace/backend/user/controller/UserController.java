package com.aitechmarketplace.backend.user.controller;

import com.aitechmarketplace.backend.auth.dto.UserResponse;
import com.aitechmarketplace.backend.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(
        @AuthenticationPrincipal User user
    ) {
        return UserResponse.from(user);
    }
}