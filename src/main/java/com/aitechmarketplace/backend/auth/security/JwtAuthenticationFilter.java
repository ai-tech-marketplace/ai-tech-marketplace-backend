package com.aitechmarketplace.backend.auth.security;

import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        UserService userService
    ) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (
            authHeader == null ||
            !authHeader.startsWith("Bearer ")
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        if (token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtService.extractUsername(token);

            if (
                email != null &&
                SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null
            ) {

                userService.findByEmail(email)
                    .filter(User::isActive)
                    .filter(user ->
                        jwtService.isTokenValid(token, email)
                    )
                    .ifPresent(this::authenticate);
            }

        } catch (Exception e) {
            // Invalid/expired JWT.
            // Continue without authentication.
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {

        var authorities = user.getRoles()
            .stream()
            .map(role ->
                new SimpleGrantedAuthority(
                    "ROLE_" + role.getName().name()
                )
            )
            .toList();

        var authentication =
            new UsernamePasswordAuthenticationToken(
                user,
                null,
                authorities
            );

        SecurityContextHolder
            .getContext()
            .setAuthentication(authentication);
    }
}