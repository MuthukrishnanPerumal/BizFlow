package com.aveline.bizflow.inventory.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.inventory.AdjustmentDirection;
import com.aveline.bizflow.inventory.StockAdjustmentService;
import com.aveline.bizflow.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.AdjustmentResponse;
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
@RequestMapping("/api/v1/inventory/adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentService adjustmentService;

    public StockAdjustmentController(StockAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<AdjustmentResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) AdjustmentDirection direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(adjustmentService.list(
                search, productId, locationId, direction, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<AdjustmentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(adjustmentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> create(@Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Stock adjusted", adjustmentService.create(request)));
    }
}
