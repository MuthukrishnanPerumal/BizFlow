package com.aveline.bizflow.identity.dto;

import com.aveline.bizflow.identity.PasswordRules;
import com.aveline.bizflow.identity.dto.UserDtos.CurrentUserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String username,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank
            @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
            @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
            String newPassword
    ) {
    }

    public record LoginResponse(
            String tokenType,
            String accessToken,
            java.time.Instant expiresAt,
            CurrentUserResponse user
    ) {
    }
}
