package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.persistence.AuditableEntity;
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
@Table(name = "payment_allocations")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 16)
    private DocumentKind documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_number", nullable = false, length = 32)
    private String documentNumber;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AllocationStatus status;

    public static PaymentAllocation post(
            Payment payment,
            DocumentKind documentType,
            Long documentId,
            String documentNumber,
            BigDecimal allocatedAmount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.payment = payment;
        allocation.documentType = documentType;
        allocation.documentId = documentId;
        allocation.documentNumber = documentNumber;
        allocation.allocatedAmount = allocatedAmount;
        allocation.status = AllocationStatus.POSTED;
        return allocation;
    }

    public void reverse() {
        this.status = AllocationStatus.REVERSED;
    }
}
