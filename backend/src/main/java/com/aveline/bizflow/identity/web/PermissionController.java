package com.aveline.bizflow.identity.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.identity.PermissionQueryService;
import com.aveline.bizflow.identity.dto.PermissionDtos.PermissionCatalogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionQueryService permissionQueryService;

    public PermissionController(PermissionQueryService permissionQueryService) {
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE')")
    public ApiResponse<PermissionCatalogResponse> catalog() {
        return ApiResponse.ok(permissionQueryService.catalog());
    }
}
