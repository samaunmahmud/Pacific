package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Please enter your name.") @Size(max = 120) String name,
            @NotBlank(message = "Please enter your email.") @Email(message = "Please enter a valid email.")
            @Size(max = 190) String email,
            // bcrypt only uses the first 72 bytes, so longer passwords would be silently truncated
            @NotBlank(message = "Please choose a password.")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Please fill in all fields.") @Size(max = 190) String identifier,
            @NotBlank(message = "Please fill in all fields.") @Size(max = 72) String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Please enter your email.") @Email(message = "Please enter a valid email.")
            @Size(max = 190) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "This reset link is incomplete.") @Size(max = 200) String token,
            @NotBlank(message = "Please choose a password.")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Please enter your current password.") @Size(max = 72) String currentPassword,
            @NotBlank(message = "Please choose a new password.")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String newPassword) {
    }

    public record UpdateProfileRequest(@NotBlank(message = "Please enter your name.") @Size(max = 120) String name) {
    }

    public record MessageResponse(String message) {
    }

    public record UserDto(Long id, String name, String email, String username, Role role) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getUsername(), u.getRole());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserDto user) {
    }
}
