package com.restaurant.core.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Origin của frontend được gọi API (biến môi trường CORS_ALLOWED_ORIGINS, phân cách bằng dấu phẩy). */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
