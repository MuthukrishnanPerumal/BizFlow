package com.aveline.bizflow.system.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.system.dto.CommercialDefaultsResponse;
import com.aveline.bizflow.system.dto.SettingsDtos.LetterheadResponse;
import com.aveline.bizflow.system.dto.SystemHealthResponse;
import com.aveline.bizflow.system.dto.SystemInfoResponse;
import com.aveline.bizflow.system.service.BusinessSettingsService;
import com.aveline.bizflow.system.service.SystemQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemQueryService systemQueryService;
    private final BusinessSettingsService businessSettingsService;

    public SystemController(SystemQueryService systemQueryService, BusinessSettingsService businessSettingsService) {
        this.systemQueryService = systemQueryService;
        this.businessSettingsService = businessSettingsService;
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> info() {
        return ApiResponse.ok(systemQueryService.info());
    }

    @GetMapping("/health")
    public ApiResponse<SystemHealthResponse> health() {
        return ApiResponse.ok(systemQueryService.health());
    }

    @GetMapping("/preferences")
    public ApiResponse<CommercialDefaultsResponse> preferences() {
        return ApiResponse.ok(systemQueryService.preferences());
    }

    @GetMapping("/letterhead")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<LetterheadResponse> letterhead() {
        return ApiResponse.ok(businessSettingsService.letterhead());
    }
}
