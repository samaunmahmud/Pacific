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
        String publicUrl,
        @Valid Payments payments,
        Mail mail,
        Security security,
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
                       int maxQuantityPerItem, int returnWindowDays) {
    }

    public record Payments(int pendingExpiryMinutes, boolean expiryJob, Stripe stripe, boolean simulatorEnabled) {
        public record Stripe(String secretKey, String webhookSecret, String apiBase) {
            public boolean configured() {
                return secretKey != null && !secretKey.isBlank();
            }
        }
    }

    /** from: the sender shown on emails. async: send on a background thread (tests turn this off). */
    public record Mail(String from, boolean async) {
    }

    /**
     * resetTokenMinutes: how long a password reset link works. loginMaxFailures: wrong passwords allowed per account
     * and address within loginWindowMinutes; ipMaxFailures: wrong passwords allowed from one address across accounts.
     * production: refuse to start with development-only settings switched on.
     */
    public record Security(int resetTokenMinutes, int loginMaxFailures, int loginWindowMinutes, int ipMaxFailures,
                           boolean production) {
    }

    public record AdminBootstrap(String username, String password) {
    }

    public record LegacyImport(String sqlitePath, int defaultStock) {
    }
}
