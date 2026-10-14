package com.aveline.bizflow.sale.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.sale.SaleReturnService;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleReturnRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sales-returns")
public class SaleReturnController {

    private final SaleReturnService saleReturnService;

    public SaleReturnController(SaleReturnService saleReturnService) {
        this.saleReturnService = saleReturnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public ApiResponse<PageResponse<ReturnSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long saleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(saleReturnService.list(search, customerId, saleId, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public ApiResponse<ReturnDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(saleReturnService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public ResponseEntity<ApiResponse<ReturnDetail>> create(@Valid @RequestBody SaleReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sales return saved", saleReturnService.create(request)));
    }
}
