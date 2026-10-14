package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "inventory_cost_layer")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryCostLayer extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StockLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_code", nullable = false, length = 64)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(name = "quantity_remaining", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityRemaining;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    public static InventoryCostLayer open(
            StockLocation location,
            Product product,
            String batchCode,
            LocalDate expiryDate,
            LocalDate receivedOn,
            BigDecimal quantity,
            BigDecimal unitCost,
            String sourceType,
            String sourceId) {
        InventoryCostLayer layer = new InventoryCostLayer();
        layer.location = location;
        layer.product = product;
        layer.batchCode = batchCode;
        layer.expiryDate = expiryDate;
        layer.receivedOn = receivedOn;
        layer.quantityRemaining = quantity;
        layer.unitCost = unitCost;
        layer.sourceType = sourceType;
        layer.sourceId = sourceId;
        return layer;
    }

    public void consume(BigDecimal quantity) {
        this.quantityRemaining = this.quantityRemaining.subtract(quantity);
    }
}
