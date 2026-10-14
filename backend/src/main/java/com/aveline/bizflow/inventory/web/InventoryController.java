package com.aveline.bizflow.inventory.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.inventory.InventoryQueryService;
import com.aveline.bizflow.inventory.StockLocationService;
import com.aveline.bizflow.inventory.StockStatus;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.inventory.dto.InventoryDtos.BalanceResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.DashboardResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.LedgerEntryResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.LocationRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.LocationResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.OutOfStockResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ProductStockResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.StockPositionResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ValuationReportResponse;
import com.aveline.bizflow.master.RecordStatus;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final StockLocationService locationService;

    public InventoryController(InventoryQueryService inventoryQueryService, StockLocationService locationService) {
        this.inventoryQueryService = inventoryQueryService;
        this.locationService = locationService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<DashboardResponse> dashboard(@RequestParam(required = false) Long locationId) {
        return ApiResponse.ok(inventoryQueryService.dashboard(locationId));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<StockPositionResponse>> overview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) StockStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(inventoryQueryService.overview(search, categoryId, locationId, status, page, size, sort));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<LedgerEntryResponse>> ledger(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) StockTxnType transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(inventoryQueryService.ledger(
                search, productId, locationId, transactionType, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<StockPositionResponse>> lowStock(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(inventoryQueryService.lowStock(search, categoryId, locationId, page, size, sort));
    }

    @GetMapping("/out-of-stock")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<OutOfStockResponse>> outOfStock(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(inventoryQueryService.outOfStock(search, categoryId, locationId, page, size, sort));
    }

    @GetMapping("/valuation")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<ValuationReportResponse> valuation(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(inventoryQueryService.valuation(search, categoryId, locationId, page, size, sort));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<ProductStockResponse> product(
            @PathVariable Long productId,
            @RequestParam(required = false) Long locationId) {
        return ApiResponse.ok(inventoryQueryService.product(productId, locationId));
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<List<BalanceResponse>> balances(@RequestParam Long productId) {
        return ApiResponse.ok(inventoryQueryService.balances(productId));
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<List<LocationResponse>> locations() {
        return ApiResponse.ok(locationService.active().stream()
                .map(location -> new LocationResponse(
                        location.getId(),
                        location.getLocationCode(),
                        location.getName(),
                        location.isDefaultLocation(),
                        location.getStatus().name()))
                .toList());
    }

    @PostMapping("/locations")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(@Valid @RequestBody LocationRequest request) {
        var location = locationService.create(request.code(), request.name());
        LocationResponse body = new LocationResponse(
                location.getId(),
                location.getLocationCode(),
                location.getName(),
                location.isDefaultLocation(),
                RecordStatus.ACTIVE.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Location created", body));
    }
}
