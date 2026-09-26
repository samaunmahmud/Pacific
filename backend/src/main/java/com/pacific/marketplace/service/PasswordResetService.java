package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.PasswordResetToken;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.PasswordResetTokenRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.security.Throttles;
import com.pacific.marketplace.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgotten passwords. The customer is emailed a link holding a random token; only its hash is stored. The link works
 * once and for a limited time, and using it changes the password, cancels every other link, and signs out every
 * session (see the password version in the sign-in token). Asking for a link never reveals whether an email is
 * registered.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ApiException INVALID = ApiException.badRequest(
            "This reset link is invalid or has expired. Please ask for a new one.");

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final Throttles throttles;
    private final AppProperties props;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                NotificationService notifications, Throttles throttles, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.notifications = notifications;
        this.throttles = throttles;
        this.props = props;
    }

    /** Emails a reset link if the address belongs to a customer. Silent either way; limited per address. */
    @Transactional
    public void request(String email, String address) {
        String from = address == null ? "?" : address;
        throttles.resetRequests.check(from);
        throttles.resetRequests.hit(from);

        User user = users.findByEmailIgnoreCase(email.strip()).filter(u -> u.getRole() == Role.CUSTOMER).orElse(null);
        if (user == null) return;
        Instant now = Instant.now();
        if (tokens.existsByUserIdAndCreatedAtAfter(user.getId(), now.minusSeconds(60))) return; // one email a minute

        tokens.voidOpen(user.getId(), now); // only the newest link works
        String raw = newToken();
        int minutes = props.security().resetTokenMinutes();
        tokens.save(new PasswordResetToken(user, hash(raw), now.plus(Duration.ofMinutes(minutes))));
        notifications.passwordReset(user, raw, minutes);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(hash(rawToken.strip())).orElseThrow(() -> INVALID);
        Long userId = token.getUser().getId();
        // Checked before the link is used up, so a too-easy password doesn't cost the customer their link.
        PasswordPolicy.check(newPassword, token.getUser().getEmail(), token.getUser().getName());
        Instant now = Instant.now();
        if (tokens.consume(token.getId(), now) != 1) throw INVALID; // already used, or expired, or someone got there first
        User user = users.findById(userId).orElseThrow(() -> INVALID);
        user.changePassword(encoder.encode(newPassword));
        tokens.voidOpen(userId, now);
        notifications.passwordChanged(user);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
