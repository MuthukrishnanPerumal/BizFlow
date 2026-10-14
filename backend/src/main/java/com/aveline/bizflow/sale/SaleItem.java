package com.aveline.bizflow.sale;

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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "sale_item")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

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

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

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

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedQuantity = SaleMath.ZERO;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private final List<SaleItemSerial> serials = new ArrayList<>();

    static SaleItem create(
            Sale sale,
            int lineNumber,
            Product product,
            String barcode,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal sellingPrice,
            BigDecimal discountAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            List<String> serialNumbers) {
        SaleItem item = new SaleItem();
        item.sale = sale;
        item.lineNumber = lineNumber;
        item.product = product;
        item.barcode = barcode;
        item.batchCode = batchCode;
        item.expiryDate = expiryDate;
        item.quantity = quantity;
        item.sellingPrice = sellingPrice;
        item.discountAmount = discountAmount;
        item.taxPercent = taxPercent;
        item.taxAmount = taxAmount;
        item.lineTotal = lineTotal;
        item.unitCost = SaleMath.ZERO;
        item.returnedQuantity = SaleMath.ZERO;
        for (String serialNumber : serialNumbers) {
            item.serials.add(SaleItemSerial.issue(item, serialNumber));
        }
        return item;
    }

    void assignCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    BigDecimal remainingQuantity() {
        return quantity.subtract(returnedQuantity);
    }

    void addReturned(BigDecimal quantity) {
        this.returnedQuantity = this.returnedQuantity.add(quantity);
    }
}
