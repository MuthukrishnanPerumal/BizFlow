package com.aveline.bizflow.sale;

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
@Table(name = "sale_tender")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleTender extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private SaleTenderMethod paymentMethod;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    static SaleTender create(
            Sale sale,
            int lineNumber,
            SaleTenderMethod paymentMethod,
            BigDecimal amount,
            String referenceNumber) {
        SaleTender tender = new SaleTender();
        tender.sale = sale;
        tender.lineNumber = lineNumber;
        tender.paymentMethod = paymentMethod;
        tender.amount = amount;
        tender.referenceNumber = referenceNumber;
        return tender;
    }
}
