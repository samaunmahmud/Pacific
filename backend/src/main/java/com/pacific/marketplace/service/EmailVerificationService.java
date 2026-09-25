package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.EmailVerificationToken;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.EmailVerificationTokenRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.security.Throttles;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.AuthDtos.UserDto;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirming a new customer's email address. Sign-up sends a link holding a random token (only its hash is stored);
 * opening it marks the address confirmed. Until then the customer can browse, fill a cart and sign in, but not
 * order, sell or post questions and answers, so order emails and buyers' messages reach a real inbox.
 */
@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ApiException INVALID = ApiException.badRequest(
            "This confirmation link is invalid or has expired. Sign in and ask for a new one.");

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final NotificationService notifications;
    private final Throttles throttles;
    private final AppProperties props;

    public EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens,
                                    NotificationService notifications, Throttles throttles, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.notifications = notifications;
        this.throttles = throttles;
        this.props = props;
    }

    /** Emails a new customer their confirmation link (sign-up calls this in its transaction). */
    @Transactional
    public void sendLink(User user) {
        Instant now = Instant.now();
        tokens.voidOpen(user.getId(), now); // only the newest link works
        String raw = newToken();
        int hours = props.security().verifyTokenHours();
        tokens.save(new EmailVerificationToken(user, PasswordResetService.hash(raw), now.plus(Duration.ofHours(hours))));
        notifications.verifyEmail(user, raw, hours);
    }

    /** "Send it again": at most one email a minute, and a handful an hour. */
    @Transactional
    public void resend(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        if (user.getRole() != Role.CUSTOMER || user.isEmailVerified()) return;
        String key = String.valueOf(userId);
        throttles.verifyRequests.check(key);
        if (tokens.existsByUserIdAndCreatedAtAfter(userId, Instant.now().minusSeconds(60))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "We've just sent you a link. Please wait a minute before asking for another.");
        }
        throttles.verifyRequests.hit(key);
        sendLink(user);
    }

    /** Opens a confirmation link. Works signed in or not (people often open it on another device). */
    @Transactional
    public UserDto confirm(String rawToken) {
        EmailVerificationToken token = tokens.findByTokenHash(PasswordResetService.hash(rawToken.strip()))
                .orElseThrow(() -> INVALID);
        Long userId = token.getUser().getId();
        Instant now = Instant.now();
        if (tokens.consume(token.getId(), now) != 1) {
            // Opening the link twice (or on two devices) is harmless once the address is confirmed.
            User user = users.findById(userId).orElseThrow(() -> INVALID);
            if (user.isEmailVerified()) return UserDto.from(user);
            throw INVALID;
        }
        User user = users.findById(userId).orElseThrow(() -> INVALID);
        user.markEmailVerified();
        tokens.voidOpen(userId, now);
        return UserDto.from(user);
    }

    /** For support: an admin confirms a customer's address by hand (the email never arrived, say). */
    @Transactional
    public UserDto confirmByAdmin(String email) {
        User user = users.findByEmailIgnoreCase(email.strip()).filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> ApiException.notFound("There's no customer with that email."));
        user.markEmailVerified();
        tokens.voidOpen(user.getId(), Instant.now());
        return UserDto.from(user);
    }

    /** Refuses the action until the customer has confirmed their email. {@code what} finishes "Confirm your email to …". */
    @Transactional(readOnly = true)
    public void requireConfirmed(Long userId, String what) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        if (user.getRole() == Role.CUSTOMER && !user.isEmailVerified()) {
            throw ApiException.forbidden("Please confirm your email address to " + what
                    + ". We sent a link to " + user.getEmail() + ".");
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
