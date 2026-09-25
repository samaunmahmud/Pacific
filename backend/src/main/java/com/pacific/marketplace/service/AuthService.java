package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.security.Throttles;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.AuthDtos.AuthResponse;
import com.pacific.marketplace.web.dto.AuthDtos.RegisterRequest;
import com.pacific.marketplace.web.dto.AuthDtos.UserDto;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid credentials.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final Throttles throttles;
    private final EmailVerificationService verification;
    /** Compared against when the account doesn't exist, so response time doesn't reveal which emails are registered. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, AppProperties props,
                       Throttles throttles, EmailVerificationService verification) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.throttles = throttles;
        this.verification = verification;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with that email already exists.");
        }
        User user = new User(req.name().strip(), email, null, encoder.encode(req.password()), Role.CUSTOMER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An account with that email already exists.");
        }
        verification.sendLink(user);
        return issue(user);
    }

    /**
     * @param portal which login page was used: customers sign in with email, admins with their Admin ID.
     *               An account can only sign in through the portal that matches its role.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(String identifier, String password, Role portal, String address) {
        String id = identifier.strip();
        String from = address == null ? "?" : address;
        String key = from + "|" + portal + ":" + id.toLowerCase(Locale.ROOT);
        // refused before any password is checked, so a blocked client can't keep guessing
        throttles.loginByAddress.check(from);
        throttles.login.check(key);
        User user = (portal == Role.ADMIN ? users.findByUsernameIgnoreCase(id) : users.findByEmailIgnoreCase(id))
                .filter(u -> u.getRole() == portal)
                .orElse(null);
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            throttles.login.hit(key);
            throttles.loginByAddress.hit(from);
            throw ApiException.unauthorized(portal == Role.ADMIN
                    ? "Invalid Admin ID or password."
                    : "Invalid email or password.");
        }
        throttles.login.clear(key);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId).map(UserDto::from)
                .orElseThrow(() -> ApiException.unauthorized(BAD_CREDENTIALS));
    }

    public AuthResponse issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofHours(props.jwt().expiryHours()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("pacific-marketplace")
                .issuedAt(now)
                .expiresAt(expires)
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .claim("pwv", user.getPasswordVersion()) // a password change invalidates every older token
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthResponse(token, expires, UserDto.from(user));
    }
}
