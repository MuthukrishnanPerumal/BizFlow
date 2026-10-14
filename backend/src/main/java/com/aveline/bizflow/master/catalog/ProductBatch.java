package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
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

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "product_batch")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBatch extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_code", nullable = false, length = 64)
    private String batchCode;

    @Column(name = "manufactured_on")
    private LocalDate manufacturedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static ProductBatch open(Product product, String batchCode, LocalDate expiresOn) {
        ProductBatch batch = new ProductBatch();
        batch.product = product;
        batch.batchCode = batchCode;
        batch.expiresOn = expiresOn;
        batch.status = RecordStatus.ACTIVE;
        return batch;
    }

    public void noteExpiry(LocalDate expiresOn) {
        if (this.expiresOn == null) {
            this.expiresOn = expiresOn;
        }
    }
}
