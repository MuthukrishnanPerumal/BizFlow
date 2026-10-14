package com.aveline.bizflow.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record CreateRoleRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "Role code must be uppercase letters, numbers, or underscores")
            String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @NotNull Set<Long> permissionIds
    ) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @NotNull Set<Long> permissionIds,
            @NotNull Long version
    ) {
    }

    public record PermissionResponse(
            Long id,
            String code,
            String module,
            String name,
            String description
    ) {
    }

    public record RoleResponse(
            Long id,
            String code,
            String name,
            String description,
            boolean systemRole,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            java.util.List<PermissionResponse> permissions
    ) {
    }
}
