package com.aveline.bizflow.finance.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.finance.PositionQueryService;
import com.aveline.bizflow.finance.PositionStatus;
import com.aveline.bizflow.finance.dto.FinanceDtos.PayableReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.ReceivableReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
public class PositionController {

    private final PositionQueryService positionQueryService;

    public PositionController(PositionQueryService positionQueryService) {
        this.positionQueryService = positionQueryService;
    }

    @GetMapping("/api/v1/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_VIEW')")
    public ApiResponse<ReceivableReport> receivables(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) PositionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(positionQueryService.receivables(
                customerId, search, dateFrom, dateTo, status, minAmount, maxAmount, page, size, sort));
    }

    @GetMapping("/api/v1/payables")
    @PreAuthorize("hasAuthority('PAYABLE_VIEW')")
    public ApiResponse<PayableReport> payables(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) PositionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(positionQueryService.payables(
                supplierId, search, dateFrom, dateTo, status, minAmount, maxAmount, page, size, sort));
    }
}
