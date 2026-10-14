package com.aveline.bizflow.purchase.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.purchase.PurchaseReturnService;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseReturnRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnSummary;
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
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    public PurchaseReturnController(PurchaseReturnService purchaseReturnService) {
        this.purchaseReturnService = purchaseReturnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public ApiResponse<PageResponse<ReturnSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long purchaseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(purchaseReturnService.list(search, supplierId, purchaseId, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public ApiResponse<ReturnDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(purchaseReturnService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<ApiResponse<ReturnDetail>> create(@Valid @RequestBody PurchaseReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase return saved", purchaseReturnService.create(request)));
    }
}
