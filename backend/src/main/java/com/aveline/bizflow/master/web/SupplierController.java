package com.aveline.bizflow.master.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.dto.PartyDtos.LedgerEntryResponse;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierRequest;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierResponse;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierUpdateRequest;
import com.aveline.bizflow.master.party.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public ApiResponse<PageResponse<SupplierResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(supplierService.list(search, status, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public ApiResponse<SupplierResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(supplierService.get(id));
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    public ApiResponse<PageResponse<LedgerEntryResponse>> ledger(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(supplierService.ledger(id, page, size, sort));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Supplier created", supplierService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_UPDATE')")
    public ApiResponse<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.ok("Supplier updated", supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ApiResponse.ok("Supplier deleted", null);
    }
}
