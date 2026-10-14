package com.aveline.bizflow.master.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.catalog.UnitService;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitUpdateRequest;
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
@RequestMapping("/api/v1/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<PageResponse<UnitResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(unitService.list(search, status, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<UnitResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(unitService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<UnitResponse>> create(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Unit created", unitService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ApiResponse<UnitResponse> update(@PathVariable Long id, @Valid @RequestBody UnitUpdateRequest request) {
        return ApiResponse.ok("Unit updated", unitService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return ApiResponse.ok("Unit deleted", null);
    }
}
