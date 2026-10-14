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
@Table(name = "inventory_balance")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBalance extends AuditableEntity {

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

    @Column(name = "quantity_on_hand", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityOnHand;

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    public void apply(BigDecimal quantityOnHand, BigDecimal unitCost) {
        this.quantityOnHand = quantityOnHand;
        this.unitCost = unitCost;
    }

    public void reserve(BigDecimal quantity) {
        this.reservedQuantity = this.reservedQuantity.add(quantity);
    }

    public void release(BigDecimal quantity) {
        this.reservedQuantity = this.reservedQuantity.subtract(quantity);
    }
}
