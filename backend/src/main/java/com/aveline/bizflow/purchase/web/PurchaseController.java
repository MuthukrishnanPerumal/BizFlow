package com.aveline.bizflow.purchase.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.purchase.PurchaseService;
import com.aveline.bizflow.purchase.PurchaseStatus;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseSummary;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseUpdateRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.VersionRequest;
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
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public ApiResponse<PageResponse<PurchaseSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(purchaseService.list(search, status, supplierId, invoiceNumber, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public ApiResponse<PurchaseDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(purchaseService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<ApiResponse<PurchaseDetail>> create(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase saved", purchaseService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public ApiResponse<PurchaseDetail> update(@PathVariable Long id, @Valid @RequestBody PurchaseUpdateRequest request) {
        return ApiResponse.ok("Purchase updated", purchaseService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public ApiResponse<PurchaseDetail> confirm(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Purchase confirmed", purchaseService.confirm(id, request.version()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CANCEL')")
    public ApiResponse<PurchaseDetail> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Purchase cancelled", purchaseService.cancel(id, request.version()));
    }
}
