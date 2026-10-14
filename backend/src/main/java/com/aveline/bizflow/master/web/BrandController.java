package com.aveline.bizflow.master.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.catalog.BrandService;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.BrandUpdateRequest;
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
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<PageResponse<BrandResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(brandService.list(search, status, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<BrandResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(brandService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Brand created", brandService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ApiResponse<BrandResponse> update(@PathVariable Long id, @Valid @RequestBody BrandUpdateRequest request) {
        return ApiResponse.ok("Brand updated", brandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ApiResponse.ok("Brand deleted", null);
    }
}
