package com.aveline.bizflow.inventory;

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
@Table(name = "stock_transfer_line")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransferLine extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("serialNumber")
    private List<StockTransferSerial> serials = new ArrayList<>();

    public static StockTransferLine create(
            StockTransfer transfer,
            int lineNumber,
            Product product,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            List<String> serialNumbers) {
        StockTransferLine line = new StockTransferLine();
        line.transfer = transfer;
        line.lineNumber = lineNumber;
        line.product = product;
        line.batchCode = batchCode == null || batchCode.isBlank() ? null : batchCode;
        line.expiryDate = expiryDate;
        line.quantity = quantity;
        line.unitCost = InventoryMath.ZERO;
        line.totalValue = InventoryMath.ZERO;
        for (String serialNumber : serialNumbers) {
            line.serials.add(StockTransferSerial.capture(line, serialNumber));
        }
        return line;
    }

    public void assignCost(BigDecimal unitCost, BigDecimal totalValue) {
        this.unitCost = unitCost;
        this.totalValue = totalValue;
    }

    public List<String> serialNumbers() {
        return serials.stream().map(StockTransferSerial::getSerialNumber).toList();
    }
}
