package com.aveline.bizflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StockTransfer t where t.id = :id")
    Optional<StockTransfer> lockById(@Param("id") Long id);

    @Query("""
            select distinct t from StockTransfer t
            join fetch t.fromLocation
            join fetch t.toLocation
            left join fetch t.lines line
            left join fetch line.product
            where t.id = :id
            """)
    Optional<StockTransfer> findDetailed(@Param("id") Long id);

    @Query(
            value = """
                    select t from StockTransfer t
                    join t.fromLocation source
                    join t.toLocation destination
                    where (:status is null or t.status = :status)
                      and (:fromLocationId is null or source.id = :fromLocationId)
                      and (:toLocationId is null or destination.id = :toLocationId)
                      and (:dateFrom is null or t.transferDate >= :dateFrom)
                      and (:dateTo is null or t.transferDate <= :dateTo)
                      and (
                        :search is null
                        or lower(t.transferNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(t.reason) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(source.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(destination.name) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(t) from StockTransfer t
                    join t.fromLocation source
                    join t.toLocation destination
                    where (:status is null or t.status = :status)
                      and (:fromLocationId is null or source.id = :fromLocationId)
                      and (:toLocationId is null or destination.id = :toLocationId)
                      and (:dateFrom is null or t.transferDate >= :dateFrom)
                      and (:dateTo is null or t.transferDate <= :dateTo)
                      and (
                        :search is null
                        or lower(t.transferNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(t.reason) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(source.name) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(destination.name) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<StockTransfer> search(
            @Param("search") String search,
            @Param("status") TransferStatus status,
            @Param("fromLocationId") Long fromLocationId,
            @Param("toLocationId") Long toLocationId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);
}
