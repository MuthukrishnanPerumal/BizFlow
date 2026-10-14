package com.aveline.bizflow.identity.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.identity.UserService;
import com.aveline.bizflow.identity.UserStatus;
import com.aveline.bizflow.identity.dto.UserDtos.AssignRolesRequest;
import com.aveline.bizflow.identity.dto.UserDtos.CreateUserRequest;
import com.aveline.bizflow.identity.dto.UserDtos.ResetPasswordRequest;
import com.aveline.bizflow.identity.dto.UserDtos.UpdateUserRequest;
import com.aveline.bizflow.identity.dto.UserDtos.UserResponse;
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
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(userService.list(search, status, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok("User updated", userService.update(id, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok("Roles updated", userService.assignRoles(id, request));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ApiResponse<UserResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok("Password reset", userService.resetPassword(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok("User deleted", null);
    }
}
