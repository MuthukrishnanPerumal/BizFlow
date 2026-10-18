package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.catalog.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "purchase_item")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 64)
    private String barcode;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "free_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal freeQuantity;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxPercent;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "stock_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal stockQuantity;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedQuantity = PurchaseMath.ZERO;

    @Column(name = "returned_free_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedFreeQuantity = PurchaseMath.ZERO;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    @BatchSize(size = 25)
    private final List<PurchaseItemSerial> serials = new ArrayList<>();

    static PurchaseItem create(
            Purchase purchase,
            int lineNumber,
            Product product,
            String barcode,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal purchasePrice,
            BigDecimal discountAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            BigDecimal unitCost,
            BigDecimal stockQuantity,
            List<String> serialNumbers) {
        PurchaseItem item = new PurchaseItem();
        item.purchase = purchase;
        item.lineNumber = lineNumber;
        item.product = product;
        item.barcode = barcode;
        item.batchCode = batchCode;
        item.expiryDate = expiryDate;
        item.quantity = quantity;
        item.freeQuantity = freeQuantity;
        item.purchasePrice = purchasePrice;
        item.discountAmount = discountAmount;
        item.taxPercent = taxPercent;
        item.taxAmount = taxAmount;
        item.lineTotal = lineTotal;
        item.unitCost = unitCost;
        item.stockQuantity = stockQuantity;
        item.returnedQuantity = PurchaseMath.ZERO;
        item.returnedFreeQuantity = PurchaseMath.ZERO;
        for (String serialNumber : serialNumbers) {
            item.serials.add(PurchaseItemSerial.receive(item, serialNumber));
        }
        return item;
    }

    BigDecimal remainingQuantity() {
        return quantity.subtract(returnedQuantity);
    }

    BigDecimal remainingFreeQuantity() {
        return freeQuantity.subtract(returnedFreeQuantity);
    }

    void addReturned(BigDecimal quantity, BigDecimal freeQuantity) {
        this.returnedQuantity = this.returnedQuantity.add(quantity);
        this.returnedFreeQuantity = this.returnedFreeQuantity.add(freeQuantity);
    }
}
