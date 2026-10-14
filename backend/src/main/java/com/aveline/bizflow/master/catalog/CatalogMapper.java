package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.master.dto.CatalogDtos.BrandResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.CategoryResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.NamedRef;
import com.aveline.bizflow.master.dto.CatalogDtos.ProductResponse;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitRef;
import com.aveline.bizflow.master.dto.CatalogDtos.UnitResponse;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toCategory(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion());
    }

    public static BrandResponse toBrand(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getCode(),
                brand.getName(),
                brand.getDescription(),
                brand.getStatus(),
                brand.getCreatedAt(),
                brand.getUpdatedAt(),
                brand.getVersion());
    }

    public static UnitResponse toUnit(UnitOfMeasure unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getShortName(),
                unit.getDescription(),
                unit.isDecimalAllowed(),
                unit.getStatus(),
                unit.getCreatedAt(),
                unit.getUpdatedAt(),
                unit.getVersion());
    }

    public static ProductResponse toProduct(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getBarcode(),
                product.getName(),
                product.getShortName(),
                product.getDescription(),
                new NamedRef(product.getCategory().getId(), product.getCategory().getCode(), product.getCategory().getName()),
                new NamedRef(product.getBrand().getId(), product.getBrand().getCode(), product.getBrand().getName()),
                new UnitRef(
                        product.getUnit().getId(),
                        product.getUnit().getName(),
                        product.getUnit().getShortName(),
                        product.getUnit().isDecimalAllowed()),
                product.getTaxPercent(),
                product.getPurchasePrice(),
                product.getSellingPrice(),
                product.getMrp(),
                product.getMinimumSellingPrice(),
                product.getReorderLevel(),
                product.getMaximumStockLevel(),
                product.getStockType(),
                product.isBatchTracking(),
                product.isSerialTracking(),
                product.isExpiryTracking(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion());
    }
}
