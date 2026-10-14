package com.aveline.bizflow.identity.dto;

import java.util.List;

public final class PermissionDtos {

    private PermissionDtos() {
    }

    public record PermissionGroupResponse(
            String module,
            String label,
            List<RoleDtos.PermissionResponse> permissions
    ) {
    }

    public record PermissionCatalogResponse(List<PermissionGroupResponse> groups) {
    }
}
