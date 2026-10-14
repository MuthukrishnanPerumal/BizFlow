package com.aveline.bizflow.master.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    Optional<ProductBatch> findByProduct_IdAndBatchCode(Long productId, String batchCode);
}
