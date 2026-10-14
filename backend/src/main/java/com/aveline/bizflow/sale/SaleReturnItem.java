package com.aveline.bizflow.sale;

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
@Table(name = "sales_return_item")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturnItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_return_id", nullable = false)
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<SaleReturnSerial> serials = new ArrayList<>();

    static SaleReturnItem create(
            SaleReturn saleReturn,
            SaleItem saleItem,
            int lineNumber,
            BigDecimal quantity,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            List<String> serialNumbers) {
        SaleReturnItem item = new SaleReturnItem();
        item.saleReturn = saleReturn;
        item.saleItem = saleItem;
        item.lineNumber = lineNumber;
        item.quantity = quantity;
        item.sellingPrice = saleItem.getSellingPrice();
        item.discountAmount = discountAmount;
        item.taxAmount = taxAmount;
        item.lineTotal = lineTotal;
        for (String serialNumber : serialNumbers) {
            item.serials.add(SaleReturnSerial.returned(item, serialNumber));
        }
        return item;
    }
}
