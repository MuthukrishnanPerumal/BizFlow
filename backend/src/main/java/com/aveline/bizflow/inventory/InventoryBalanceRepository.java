package com.aveline.bizflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from InventoryBalance b
            where b.location.id = :locationId
              and b.product.id = :productId
              and b.batchCode = :batchCode
              and ((:expiry is null and b.expiryDate is null) or b.expiryDate = :expiry)
            """)
    Optional<InventoryBalance> lockBucket(
            @Param("locationId") Long locationId,
            @Param("productId") Long productId,
            @Param("batchCode") String batchCode,
            @Param("expiry") LocalDate expiry);

    @Query("""
            select b from InventoryBalance b
            join fetch b.product
            join fetch b.location
            where b.product.id = :productId
            order by b.location.name, b.batchCode, b.expiryDate
            """)
    List<InventoryBalance> findByProductId(@Param("productId") Long productId);

    @Query("select coalesce(sum(b.quantityOnHand), 0) from InventoryBalance b where b.product.id = :productId")
    BigDecimal quantityOnHand(@Param("productId") Long productId);
}
