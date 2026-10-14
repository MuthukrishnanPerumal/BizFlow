package com.aveline.bizflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    @Query(
            value = """
                    select a from StockAdjustment a
                    join a.product p
                    join a.location l
                    where (:productId is null or p.id = :productId)
                      and (:locationId is null or l.id = :locationId)
                      and (:direction is null or a.direction = :direction)
                      and (:dateFrom is null or a.adjustmentDate >= :dateFrom)
                      and (:dateTo is null or a.adjustmentDate <= :dateTo)
                      and (
                        :search is null
                        or lower(a.adjustmentNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.productCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(coalesce(a.referenceNumber, '')) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(a) from StockAdjustment a
                    join a.product p
                    join a.location l
                    where (:productId is null or p.id = :productId)
                      and (:locationId is null or l.id = :locationId)
                      and (:direction is null or a.direction = :direction)
                      and (:dateFrom is null or a.adjustmentDate >= :dateFrom)
                      and (:dateTo is null or a.adjustmentDate <= :dateTo)
                      and (
                        :search is null
                        or lower(a.adjustmentNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.productCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(coalesce(a.referenceNumber, '')) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<StockAdjustment> search(
            @Param("search") String search,
            @Param("productId") Long productId,
            @Param("locationId") Long locationId,
            @Param("direction") AdjustmentDirection direction,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);
}
