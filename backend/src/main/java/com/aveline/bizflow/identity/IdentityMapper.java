package com.aveline.bizflow.identity;

import com.aveline.bizflow.identity.dto.RoleDtos.PermissionResponse;
import com.aveline.bizflow.identity.dto.RoleDtos.RoleResponse;
import com.aveline.bizflow.identity.dto.UserDtos.CurrentUserResponse;
import com.aveline.bizflow.identity.dto.UserDtos.RoleSummary;
import com.aveline.bizflow.identity.dto.UserDtos.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class IdentityMapper {

    public UserResponse toUser(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion(),
                user.isMustChangePassword(),
                roleSummaries(user));
    }

    public CurrentUserResponse toCurrentUser(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.isMustChangePassword(),
                roleSummaries(user),
                permissionCodes(user));
    }

    public RoleResponse toRole(AppRole role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .sorted(Comparator.comparing(AppPermission::getModuleCode).thenComparingInt(AppPermission::getSortOrder))
                .map(this::toPermission)
                .toList();
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                role.getVersion(),
                permissions);
    }

    public PermissionResponse toPermission(AppPermission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getModuleCode(),
                permission.getName(),
                permission.getDescription());
    }

    private List<RoleSummary> roleSummaries(AppUser user) {
        return user.getRoles().stream()
                .sorted(Comparator.comparing(AppRole::getCode))
                .map(role -> new RoleSummary(role.getId(), role.getCode(), role.getName()))
                .toList();
    }

    private List<String> permissionCodes(AppUser user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(AppPermission::getCode)
                .distinct()
                .sorted()
                .toList();
    }
}
