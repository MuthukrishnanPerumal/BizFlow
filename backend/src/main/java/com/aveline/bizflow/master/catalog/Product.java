package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "products")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends AuditableEntity {

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", length = 80)
    private String shortName;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    @Column(name = "tax_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxPercent;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal mrp;

    @Column(name = "minimum_selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumSellingPrice;

    @Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
    private BigDecimal reorderLevel;

    @Column(name = "maximum_stock_level", precision = 19, scale = 4)
    private BigDecimal maximumStockLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_type", nullable = false, length = 16)
    private StockType stockType;

    @Column(name = "batch_tracking", nullable = false, columnDefinition = "bit(1)")
    private boolean batchTracking;

    @Column(name = "serial_tracking", nullable = false, columnDefinition = "bit(1)")
    private boolean serialTracking;

    @Column(name = "expiry_tracking", nullable = false, columnDefinition = "bit(1)")
    private boolean expiryTracking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static Product create(
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            Category category,
            Brand brand,
            UnitOfMeasure unit,
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
            RecordStatus status) {
        Product product = new Product();
        product.apply(
                productCode,
                barcode,
                name,
                shortName,
                description,
                category,
                brand,
                unit,
                taxPercent,
                purchasePrice,
                sellingPrice,
                mrp,
                minimumSellingPrice,
                reorderLevel,
                maximumStockLevel,
                stockType,
                batchTracking,
                serialTracking,
                expiryTracking,
                status);
        return product;
    }

    public void update(
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            Category category,
            Brand brand,
            UnitOfMeasure unit,
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
            RecordStatus status) {
        apply(
                productCode,
                barcode,
                name,
                shortName,
                description,
                category,
                brand,
                unit,
                taxPercent,
                purchasePrice,
                sellingPrice,
                mrp,
                minimumSellingPrice,
                reorderLevel,
                maximumStockLevel,
                stockType,
                batchTracking,
                serialTracking,
                expiryTracking,
                status);
    }

    public void retire() {
        markDeleted();
    }

    private void apply(
            String productCode,
            String barcode,
            String name,
            String shortName,
            String description,
            Category category,
            Brand brand,
            UnitOfMeasure unit,
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
            RecordStatus status) {
        this.productCode = productCode;
        this.barcode = barcode;
        this.name = name;
        this.shortName = shortName;
        this.description = description;
        this.category = category;
        this.brand = brand;
        this.unit = unit;
        this.taxPercent = taxPercent;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.mrp = mrp;
        this.minimumSellingPrice = minimumSellingPrice;
        this.reorderLevel = reorderLevel;
        this.maximumStockLevel = maximumStockLevel;
        this.stockType = stockType;
        this.batchTracking = batchTracking;
        this.serialTracking = serialTracking;
        this.expiryTracking = expiryTracking;
        this.status = status;
    }
}
