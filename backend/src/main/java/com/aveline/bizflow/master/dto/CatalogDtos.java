package com.aveline.bizflow.master.dto;

import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record NamedRef(Long id, String code, String name) {
    }

    public record UnitRef(Long id, String name, String shortName, boolean decimalAllowed) {
    }

    public record CategoryRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull RecordStatus status
    ) {
    }

    public record CategoryUpdateRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record CategoryResponse(
            Long id,
            String code,
            String name,
            String description,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record BrandRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull RecordStatus status
    ) {
    }

    public record BrandUpdateRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record BrandResponse(
            Long id,
            String code,
            String name,
            String description,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record UnitRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank
            @Size(max = 16)
            @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "Short name must be 1 to 16 letters or numbers")
            String shortName,
            @Size(max = 500) String description,
            @NotNull Boolean decimalAllowed,
            @NotNull RecordStatus status
    ) {
    }

    public record UnitUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank
            @Size(max = 16)
            @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "Short name must be 1 to 16 letters or numbers")
            String shortName,
            @Size(max = 500) String description,
            @NotNull Boolean decimalAllowed,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record UnitResponse(
            Long id,
            String name,
            String shortName,
            String description,
            boolean decimalAllowed,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record ProductRequest(
            @NotBlank
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$", message = "Product code must be 2 to 64 characters using letters, numbers, dots, underscores, or hyphens")
            String productCode,
            @Size(max = 64)
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", message = "Enter a valid barcode")
            String barcode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 80) String shortName,
            @Size(max = 2000) String description,
            @NotNull @Positive Long categoryId,
            @NotNull @Positive Long brandId,
            @NotNull @Positive Long unitId,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 4) BigDecimal taxPercent,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal purchasePrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal sellingPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal mrp,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal minimumSellingPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal reorderLevel,
            @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal maximumStockLevel,
            @NotNull StockType stockType,
            @NotNull Boolean batchTracking,
            @NotNull Boolean serialTracking,
            @NotNull Boolean expiryTracking,
            @NotNull RecordStatus status
    ) {
    }

    public record ProductUpdateRequest(
            @NotBlank
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$", message = "Product code must be 2 to 64 characters using letters, numbers, dots, underscores, or hyphens")
            String productCode,
            @Size(max = 64)
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", message = "Enter a valid barcode")
            String barcode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 80) String shortName,
            @Size(max = 2000) String description,
            @NotNull @Positive Long categoryId,
            @NotNull @Positive Long brandId,
            @NotNull @Positive Long unitId,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 4) BigDecimal taxPercent,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal purchasePrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal sellingPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal mrp,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal minimumSellingPrice,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal reorderLevel,
            @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal maximumStockLevel,
            @NotNull StockType stockType,
            @NotNull Boolean batchTracking,
            @NotNull Boolean serialTracking,
            @NotNull Boolean expiryTracking,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record ProductResponse(
            Long id,
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            NamedRef category,
            NamedRef brand,
            UnitRef unit,
            BigDecimal taxPercent,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal minimumSellingPrice,
            BigDecimal reorderLevel,
            BigDecimal maximumStockLevel,
            StockType stockType,
            boolean batchTracking,
            boolean serialTracking,
            boolean expiryTracking,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }
}
