package com.aveline.bizflow.inventory.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.inventory.StockTransferService;
import com.aveline.bizflow.inventory.TransferStatus;
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferRequest;
import com.aveline.bizflow.inventory.dto.InventoryDtos.TransferResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.VersionRequest;
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
@RequestMapping("/api/v1/inventory/transfers")
public class StockTransferController {

    private final StockTransferService transferService;

    public StockTransferController(StockTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<PageResponse<TransferResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long fromLocationId,
            @RequestParam(required = false) Long toLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(transferService.list(
                search, status, fromLocationId, toLocationId, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public ApiResponse<TransferResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(transferService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ResponseEntity<ApiResponse<TransferResponse>> create(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Stock transfer saved", transferService.create(request)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ApiResponse<TransferResponse> confirm(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Transfer confirmed", transferService.confirm(id, request.version()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ApiResponse<TransferResponse> complete(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Transfer completed", transferService.complete(id, request.version()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ApiResponse<TransferResponse> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Transfer cancelled", transferService.cancel(id, request.version()));
    }
}
