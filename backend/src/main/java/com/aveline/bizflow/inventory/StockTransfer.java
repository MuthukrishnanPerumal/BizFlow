package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "stock_transfer")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransfer extends AuditableEntity {

    @Column(name = "transfer_number", nullable = false, length = 32)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_location_id", nullable = false)
    private StockLocation fromLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_location_id", nullable = false)
    private StockLocation toLocation;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<StockTransferLine> lines = new ArrayList<>();

    public static StockTransfer draft(
            String transferNumber,
            StockLocation fromLocation,
            StockLocation toLocation,
            LocalDate transferDate,
            String reason,
            String notes) {
        StockTransfer transfer = new StockTransfer();
        transfer.transferNumber = transferNumber;
        transfer.fromLocation = fromLocation;
        transfer.toLocation = toLocation;
        transfer.transferDate = transferDate;
        transfer.reason = reason;
        transfer.notes = notes;
        transfer.status = TransferStatus.DRAFT;
        return transfer;
    }

    public StockTransferLine addLine(StockTransferLine line) {
        lines.add(line);
        return line;
    }

    public void confirm() {
        this.status = TransferStatus.CONFIRMED;
    }

    public void complete() {
        this.status = TransferStatus.COMPLETED;
    }

    public void cancel() {
        this.status = TransferStatus.CANCELLED;
    }
}
