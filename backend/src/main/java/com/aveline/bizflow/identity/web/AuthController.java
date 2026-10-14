package com.aveline.bizflow.identity.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.identity.AuthService;
import com.aveline.bizflow.identity.dto.AuthDtos.ChangePasswordRequest;
import com.aveline.bizflow.identity.dto.AuthDtos.LoginRequest;
import com.aveline.bizflow.identity.dto.AuthDtos.LoginResponse;
import com.aveline.bizflow.identity.dto.UserDtos.CurrentUserResponse;
import com.aveline.bizflow.security.AccountPrincipal;
import com.aveline.bizflow.security.CurrentAccounts;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Signed in", authService.login(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        AccountPrincipal principal = CurrentAccounts.require();
        authService.logout(principal);
        return ApiResponse.ok("Signed out", null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser() {
        return ApiResponse.ok(authService.currentUser(CurrentAccounts.require().userId()));
    }

    @PostMapping("/change-password")
    public ApiResponse<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(
                "Password changed",
                authService.changePassword(CurrentAccounts.require().userId(), request));
    }
}
