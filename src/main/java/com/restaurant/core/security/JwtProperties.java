package com.restaurant.core.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình JWT. secret lấy từ biến môi trường JWT_SECRET, tối thiểu 32 byte (HS256). */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET phải được đặt và dài tối thiểu 32 byte");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "restaurant-core";
        }
        if (ttl == null) {
            ttl = Duration.ofHours(8);
        }
    }
}
