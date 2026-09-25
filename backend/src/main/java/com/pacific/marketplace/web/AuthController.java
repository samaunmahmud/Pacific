package com.pacific.marketplace.web;

import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.service.AuthService;
import com.pacific.marketplace.service.PasswordResetService;
import com.pacific.marketplace.service.EmailVerificationService;
import com.pacific.marketplace.web.dto.AuthDtos.ForgotPasswordRequest;
import com.pacific.marketplace.web.dto.AuthDtos.MessageResponse;
import com.pacific.marketplace.web.dto.AuthDtos.ResetPasswordRequest;
import com.pacific.marketplace.web.dto.AuthDtos.AuthResponse;
import com.pacific.marketplace.web.dto.AuthDtos.LoginRequest;
import com.pacific.marketplace.web.dto.AuthDtos.RegisterRequest;
import com.pacific.marketplace.web.dto.AuthDtos.UserDto;
import com.pacific.marketplace.web.dto.AuthDtos.VerifyEmailRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final PasswordResetService resets;
    private final EmailVerificationService verification;

    public AuthController(AuthService auth, PasswordResetService resets, EmailVerificationService verification) {
        this.auth = auth;
        this.resets = resets;
        this.verification = verification;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req.identifier(), req.password(), Role.CUSTOMER, http.getRemoteAddr());
    }

    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req.identifier(), req.password(), Role.ADMIN, http.getRemoteAddr());
    }

    /** Always the same answer, whether or not the email is registered, so this can't be used to find out who is. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        resets.request(req.email(), http.getRemoteAddr());
        return new MessageResponse("If there's an account for that email, we've sent a link to reset the password.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        resets.reset(req.token(), req.password());
    }

    /** Opens the link from the sign-up email. No sign-in needed: people often open it on another device. */
    @PostMapping("/verify-email")
    public UserDto verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return verification.confirm(req.token());
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@AuthenticationPrincipal Jwt jwt) {
        verification.resend(CurrentUser.id(jwt));
        return new MessageResponse("We've sent you a new link. It can take a minute to arrive.");
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(CurrentUser.id(jwt));
    }
}
