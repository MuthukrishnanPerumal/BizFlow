package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "purchase_return_item")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseReturnItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_item_id", nullable = false)
    private PurchaseItem purchaseItem;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "free_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal freeQuantity;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PurchaseReturnSerial> serials = new ArrayList<>();

    static PurchaseReturnItem create(
            PurchaseReturn purchaseReturn,
            PurchaseItem purchaseItem,
            int lineNumber,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            List<String> serials) {
        PurchaseReturnItem item = new PurchaseReturnItem();
        item.purchaseReturn = purchaseReturn;
        item.purchaseItem = purchaseItem;
        item.lineNumber = lineNumber;
        item.quantity = quantity;
        item.freeQuantity = freeQuantity;
        item.purchasePrice = purchaseItem.getPurchasePrice();
        item.discountAmount = discountAmount;
        item.taxAmount = taxAmount;
        item.lineTotal = lineTotal;
        for (String serial : serials) {
            item.serials.add(PurchaseReturnSerial.of(item, serial));
        }
        return item;
    }
}
