package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.notify.NotificationService;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.security.Throttles;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.AuthDtos.AuthResponse;
import com.pacific.marketplace.web.dto.AuthDtos.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A signed-in customer looking after their own account. */
@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final NotificationService notifications;
    private final Throttles throttles;

    public AccountService(UserRepository users, PasswordEncoder encoder, AuthService auth,
                          NotificationService notifications, Throttles throttles) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
        this.notifications = notifications;
        this.throttles = throttles;
    }

    /**
     * Changes the password after checking the current one. Every other session is signed out; the caller gets a fresh
     * token so this one carries on. A wrong current password is a 400, not a 401: a 401 makes the app sign the person
     * out, and mistyping isn't a reason to.
     */
    @Transactional
    public AuthResponse changePassword(Long userId, String current, String next) {
        String key = "u" + userId;
        throttles.passwordChange.check(key);
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        if (!encoder.matches(current, user.getPasswordHash())) {
            throttles.passwordChange.hit(key);
            throw ApiException.badRequest("Your current password isn't right.");
        }
        if (encoder.matches(next, user.getPasswordHash())) {
            throw ApiException.badRequest("Please choose a different password from your current one.");
        }
        PasswordPolicy.check(next, user.getEmail(), user.getName());
        throttles.passwordChange.clear(key);
        user.changePassword(encoder.encode(next));
        users.flush();
        notifications.passwordChanged(user);
        return auth.issue(user);
    }

    /**
     * Signs out every device (a lost phone, a shared computer): tokens carry the account's session version, so
     * bumping it makes them all invalid. The device asking gets a fresh token and stays signed in.
     */
    @Transactional
    public AuthResponse signOutEverywhereElse(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        user.endAllSessions();
        users.flush();
        return auth.issue(user);
    }

    @Transactional
    public UserDto rename(Long userId, String name) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        user.rename(name.strip());
        return UserDto.from(user);
    }
}
