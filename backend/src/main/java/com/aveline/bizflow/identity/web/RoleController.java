package com.aveline.bizflow.identity.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.identity.RoleService;
import com.aveline.bizflow.identity.dto.RoleDtos.CreateRoleRequest;
import com.aveline.bizflow.identity.dto.RoleDtos.RoleResponse;
import com.aveline.bizflow.identity.dto.RoleDtos.UpdateRoleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW','USER_VIEW')")
    public ApiResponse<PageResponse<RoleResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(roleService.list(search, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW','USER_VIEW')")
    public ApiResponse<RoleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Role created", roleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated", roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok("Role deleted", null);
    }
}
