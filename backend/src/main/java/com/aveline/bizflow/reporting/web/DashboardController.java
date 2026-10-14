package com.aveline.bizflow.reporting.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.reporting.ReportPreset;
import com.aveline.bizflow.reporting.ReportingQueryService;
import com.aveline.bizflow.reporting.dto.ReportingDtos.DashboardResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class DashboardController {

    private final ReportingQueryService reportingQueryService;

    public DashboardController(ReportingQueryService reportingQueryService) {
        this.reportingQueryService = reportingQueryService;
    }

    @GetMapping("/api/v1/dashboard")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportingQueryService.dashboard(preset, from, to));
    }
}
