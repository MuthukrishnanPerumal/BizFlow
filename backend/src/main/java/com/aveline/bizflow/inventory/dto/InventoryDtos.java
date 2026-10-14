package com.aveline.bizflow.inventory.dto;

import com.aveline.bizflow.inventory.AdjustmentDirection;
import com.aveline.bizflow.inventory.AdjustmentReason;
import com.aveline.bizflow.inventory.StockStatus;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.inventory.TransferStatus;
import com.aveline.bizflow.inventory.ValuationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record BalanceResponse(
            Long productId,
            String productCode,
            String productName,
            String batch,
            LocalDate expiryDate,
            BigDecimal quantityOnHand,
            BigDecimal unitCost,
            Long locationId,
            String locationCode,
            String locationName,
            boolean defaultLocation,
            BigDecimal reservedQuantity,
            BigDecimal availableQuantity
    ) {
    }

    public record LocationResponse(
            Long id,
            String code,
            String name,
            boolean defaultLocation,
            String status
    ) {
    }

    public record LocationRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record StockPositionResponse(
            Long productId,
            String productCode,
            String barcode,
            String productName,
            String categoryName,
            BigDecimal currentStock,
            BigDecimal reservedStock,
            BigDecimal availableStock,
            BigDecimal averageCost,
            BigDecimal sellingPrice,
            BigDecimal stockValue,
            BigDecimal reorderLevel,
            BigDecimal maximumStockLevel,
            StockStatus status,
            BigDecimal shortageQuantity
    ) {
    }

    public record OutOfStockResponse(
            Long productId,
            String productCode,
            String barcode,
            String productName,
            String categoryName,
            BigDecimal currentStock,
            BigDecimal reorderLevel,
            BigDecimal shortageQuantity,
            LocalDate lastPurchase,
            LocalDate lastSale,
            StockStatus status
    ) {
    }

    public record ValuationRowResponse(
            Long productId,
            String productCode,
            String productName,
            String categoryName,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal averageValue,
            BigDecimal fifoValue,
            BigDecimal officialValue
    ) {
    }

    public record ValuationSummaryResponse(
            ValuationMethod method,
            long skuCount,
            BigDecimal quantity,
            BigDecimal averageValue,
            BigDecimal fifoValue,
            BigDecimal officialValue
    ) {
    }

    public record ValuationReportResponse(
            ValuationSummaryResponse summary,
            List<ValuationRowResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record LedgerEntryResponse(
            Long id,
            LocalDate date,
            Long productId,
            String productCode,
            String productName,
            StockTxnType transactionType,
            String referenceNumber,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal runningBalance,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String user,
            String remarks,
            Long locationId,
            String locationCode,
            String locationName,
            String batch
    ) {
    }

    public record MovementMixResponse(
            StockTxnType transactionType,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            long movementCount
    ) {
    }

    public record DashboardResponse(
            ValuationMethod valuationMethod,
            boolean allowNegativeStock,
            long skuCount,
            BigDecimal unitsOnHand,
            BigDecimal unitsReserved,
            BigDecimal unitsAvailable,
            BigDecimal inventoryValue,
            long inStock,
            long lowStock,
            long outOfStock,
            long overStock,
            BigDecimal healthPercent,
            List<StockPositionResponse> lowStockAlerts,
            List<OutOfStockResponse> outOfStockAlerts,
            List<LedgerEntryResponse> recentMovements,
            List<MovementMixResponse> movement
    ) {
    }

    public record ProductStockResponse(
            StockPositionResponse position,
            List<BalanceResponse> buckets,
            List<LedgerEntryResponse> movements,
            LocalDate lastPurchase,
            LocalDate lastSale
    ) {
    }

    public record AdjustmentRequest(
            @NotNull Long productId,
            Long locationId,
            @Size(max = 64) String batch,
            LocalDate expiryDate,
            @NotNull AdjustmentDirection direction,
            @NotNull BigDecimal quantity,
            BigDecimal unitCost,
            @NotNull AdjustmentReason reason,
            @Size(max = 64) String referenceNumber,
            @NotNull LocalDate adjustmentDate,
            @Size(max = 1000) String notes,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record AdjustmentResponse(
            Long id,
            String adjustmentNumber,
            LocalDate adjustmentDate,
            AdjustmentDirection direction,
            AdjustmentReason reason,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String referenceNumber,
            String notes,
            String batch,
            LocalDate expiryDate,
            Long productId,
            String productCode,
            String productName,
            Long locationId,
            String locationCode,
            String locationName,
            String user,
            Long version
    ) {
    }

    public record TransferLineRequest(
            @NotNull Long productId,
            @Size(max = 64) String batch,
            LocalDate expiryDate,
            @NotNull BigDecimal quantity,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record TransferRequest(
            @NotNull Long fromLocationId,
            @NotNull Long toLocationId,
            @NotNull LocalDate transferDate,
            @NotBlank @Size(max = 200) String reason,
            @Size(max = 1000) String notes,
            @NotNull Boolean complete,
            @NotEmpty @Valid List<TransferLineRequest> lines
    ) {
    }

    public record TransferLineResponse(
            Long id,
            int lineNumber,
            Long productId,
            String productCode,
            String productName,
            String batch,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalValue,
            List<String> serials
    ) {
    }

    public record TransferResponse(
            Long id,
            String transferNumber,
            LocalDate transferDate,
            Long fromLocationId,
            String fromLocationCode,
            String fromLocationName,
            Long toLocationId,
            String toLocationCode,
            String toLocationName,
            String reason,
            String notes,
            TransferStatus status,
            String user,
            Long version,
            List<TransferLineResponse> lines
    ) {
    }

    public record VersionRequest(@NotNull Long version) {
    }
}
