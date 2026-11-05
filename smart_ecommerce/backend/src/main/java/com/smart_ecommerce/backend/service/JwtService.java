package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.config.SecurityConfigurationValidator;
import com.smart_ecommerce.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration) {

        this.secretKey = SecurityConfigurationValidator.validateJwtKey(secret);
        if (expiration <= 0 || expiration > Long.MAX_VALUE - System.currentTimeMillis()) {
            throw new IllegalArgumentException(
                    "JWT_EXPIRATION_MS must be positive and fit within the supported timestamp range");
        }

        this.expiration = expiration;
    }

    public String generateToken(User user) {

        long now = System.currentTimeMillis();
        String roles = user.getRoles()
                .stream()
                .map(role -> role.getName())
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(
                        new Date(Math.addExact(now, expiration))
                )
                .signWith(secretKey)
                .compact();
    }

    public UUID extractUserId(String token) {

        return UUID.fromString(
                extractAllClaims(token).getSubject()
        );
    }

    public boolean isTokenValid(String token) {

        try {
            Claims claims = extractAllClaims(token);

            return claims.getExpiration()
                    .after(new Date());

        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
