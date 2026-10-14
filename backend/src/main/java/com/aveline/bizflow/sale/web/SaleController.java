package com.aveline.bizflow.sale.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.sale.SalePaymentType;
import com.aveline.bizflow.sale.SaleService;
import com.aveline.bizflow.sale.SaleStatus;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleUpdateRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.VersionRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public ApiResponse<PageResponse<SaleSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) SalePaymentType paymentType,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(saleService.list(
                search, status, paymentType, customerId, invoiceNumber, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public ApiResponse<SaleDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(saleService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public ResponseEntity<ApiResponse<SaleDetail>> create(@Valid @RequestBody SaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sale saved", saleService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_UPDATE')")
    public ApiResponse<SaleDetail> update(@PathVariable Long id, @Valid @RequestBody SaleUpdateRequest request) {
        return ApiResponse.ok("Sale updated", saleService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('SALES_UPDATE')")
    public ApiResponse<SaleDetail> confirm(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Sale completed", saleService.confirm(id, request.version()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALES_CANCEL')")
    public ApiResponse<SaleDetail> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Sale cancelled", saleService.cancel(id, request.version()));
    }
}
