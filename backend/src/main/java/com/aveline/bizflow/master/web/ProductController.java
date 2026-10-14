package com.aveline.bizflow.master.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.ProductService;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductRequest;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductUpdateRequest;
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
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) StockType stockType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(productService.list(search, status, stockType, categoryId, brandId, page, size, sort));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<ProductResponse> lookup(@RequestParam String barcode) {
        return ApiResponse.ok(productService.lookupBarcode(barcode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.ok("Product updated", productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok("Product deleted", null);
    }
}
