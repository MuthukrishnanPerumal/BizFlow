package com.aveline.bizflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryCostLayerRepository extends JpaRepository<InventoryCostLayer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select layer from InventoryCostLayer layer
            where layer.location.id = :locationId
              and layer.product.id = :productId
              and layer.batchCode = :batchCode
              and ((:expiry is null and layer.expiryDate is null) or layer.expiryDate = :expiry)
              and layer.quantityRemaining > 0
            order by layer.id
            """)
    List<InventoryCostLayer> lockOpenLayers(
            @Param("locationId") Long locationId,
            @Param("productId") Long productId,
            @Param("batchCode") String batchCode,
            @Param("expiry") LocalDate expiry);
}
