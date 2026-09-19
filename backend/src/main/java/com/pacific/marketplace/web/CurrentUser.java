package com.pacific.marketplace.web;

import com.pacific.marketplace.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user out of the JWT (subject = user id). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /** The user's id if the caller is a signed-in customer, otherwise null (anonymous or admin). */
    public static Long customerIdOrNull(Jwt jwt) {
        if (jwt == null || !"CUSTOMER".equals(jwt.getClaimAsString("role"))) return null;
        return id(jwt);
    }

    /** The caller's role, or null for anonymous visitors. */
    public static Role role(Jwt jwt) {
        return jwt == null ? null : Role.valueOf(jwt.getClaimAsString("role"));
    }
}
