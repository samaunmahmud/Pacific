package com.pacific.marketplace.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid Jwt jwt,
        @Valid Cors cors,
        @Valid Reviews reviews,
        @Valid Shop shop,
        AdminBootstrap adminBootstrap,
        LegacyImport legacyImport) {

    public record Jwt(@NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
                      long expiryHours) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Reviews(long editWindowMinutes) {
    }

    public record Shop(String currency, BigDecimal shippingFlatRate, BigDecimal freeShippingThreshold,
                       int maxQuantityPerItem) {
    }

    public record AdminBootstrap(String username, String password) {
    }

    public record LegacyImport(String sqlitePath, int defaultStock) {
    }
}
