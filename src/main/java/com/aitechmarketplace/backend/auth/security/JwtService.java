package com.aitechmarketplace.backend.auth.security;

import com.aitechmarketplace.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {

        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);

        List<String> roles = user.getRoles()
            .stream()
            .map(role -> role.getName().name())
            .toList();

        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact();
    }

    public Claims parseToken(String token) {

        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public boolean isTokenValid(
        String token,
        String username
    ) {
        Claims claims = parseToken(token);

        return username.equals(claims.getSubject())
            && claims.getExpiration().after(new Date());
    }
}