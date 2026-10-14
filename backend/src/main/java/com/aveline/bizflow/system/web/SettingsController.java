package com.aveline.bizflow.system.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.system.dto.DataResetDtos.DataResetRequest;
import com.aveline.bizflow.system.dto.DataResetDtos.DataResetResult;
import com.aveline.bizflow.system.dto.DataResetDtos.ResetTableView;
import com.aveline.bizflow.system.dto.SettingsDtos.BusinessSettingsRequest;
import com.aveline.bizflow.system.dto.SettingsDtos.BusinessSettingsResponse;
import com.aveline.bizflow.system.service.BusinessSettingsService;
import com.aveline.bizflow.system.service.DataResetService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final BusinessSettingsService businessSettingsService;
    private final DataResetService dataResetService;

    public SettingsController(BusinessSettingsService businessSettingsService, DataResetService dataResetService) {
        this.businessSettingsService = businessSettingsService;
        this.dataResetService = dataResetService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ApiResponse<BusinessSettingsResponse> current() {
        return ApiResponse.ok(businessSettingsService.current());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ApiResponse<BusinessSettingsResponse> update(@Valid @RequestBody BusinessSettingsRequest request) {
        return ApiResponse.ok("Settings saved", businessSettingsService.update(request));
    }

    @GetMapping("/data-reset")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ApiResponse<List<ResetTableView>> resetTables() {
        return ApiResponse.ok(dataResetService.list());
    }

    @PostMapping("/data-reset")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ApiResponse<DataResetResult> resetData(@Valid @RequestBody DataResetRequest request) {
        return ApiResponse.ok("Selected data deleted", dataResetService.reset(request.tables()));
    }
}
