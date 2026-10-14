package com.aveline.bizflow.identity.dto;

import com.aveline.bizflow.identity.PasswordRules;
import com.aveline.bizflow.identity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,64}$", message = "Username must be 3 to 64 characters and use letters, numbers, dots, underscores, or hyphens")
            String username,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 32)
            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone,
            @NotBlank
            @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
            @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String password,
            @NotNull UserStatus status,
            @NotNull Set<Long> roleIds
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 32)
            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone,
            @Size(max = PasswordRules.MAX_LENGTH)
            @Pattern(regexp = "^$|" + PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String password,
            @NotNull UserStatus status,
            @NotNull Set<Long> roleIds,
            @NotNull Long version
    ) {
    }

    public record AssignRolesRequest(
            @NotNull Set<Long> roleIds,
            @NotNull Long version
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank
            @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
            @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String password,
            @NotNull Long version
    ) {
    }

    public record RoleSummary(Long id, String code, String name) {
    }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            UserStatus status,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            boolean mustChangePassword,
            java.util.List<RoleSummary> roles
    ) {
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            UserStatus status,
            Instant lastLoginAt,
            boolean mustChangePassword,
            java.util.List<RoleSummary> roles,
            java.util.List<String> permissions
    ) {
    }
}
