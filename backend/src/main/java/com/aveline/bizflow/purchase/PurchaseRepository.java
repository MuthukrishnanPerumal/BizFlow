package com.aveline.bizflow.purchase;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> lockById(@Param("id") Long id);

    @Query("""
            select distinct p from Purchase p
            join fetch p.supplier
            left join fetch p.items i
            left join fetch i.product pr
            left join fetch pr.unit
            where p.id = :id
            """)
    Optional<Purchase> findDetailedById(@Param("id") Long id);

    @Query(value = """
            select count(*) from purchases
            where is_deleted = 0
              and status <> 'CANCELLED'
              and supplier_id = :supplierId
              and invoice_number = :invoiceNumber
              and id <> :id
            """, nativeQuery = true)
    long countLiveInvoice(
            @Param("supplierId") long supplierId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("id") long id);

    @Query(
            value = """
                    select p from Purchase p
                    join fetch p.supplier s
                    where (:status is null or p.status = :status)
                      and (:supplierId is null or s.id = :supplierId)
                      and (:fromDate is null or p.purchaseDate >= :fromDate)
                      and (:toDate is null or p.purchaseDate <= :toDate)
                      and (:invoice is null or lower(p.invoiceNumber) like lower(concat('%', :invoice, '%')) escape '\\')
                      and (
                        :search is null
                        or lower(p.purchaseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.invoiceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(p) from Purchase p
                    join p.supplier s
                    where (:status is null or p.status = :status)
                      and (:supplierId is null or s.id = :supplierId)
                      and (:fromDate is null or p.purchaseDate >= :fromDate)
                      and (:toDate is null or p.purchaseDate <= :toDate)
                      and (:invoice is null or lower(p.invoiceNumber) like lower(concat('%', :invoice, '%')) escape '\\')
                      and (
                        :search is null
                        or lower(p.purchaseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.invoiceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<Purchase> search(
            @Param("search") String search,
            @Param("status") PurchaseStatus status,
            @Param("supplierId") Long supplierId,
            @Param("invoice") String invoice,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            select p from Purchase p
            where p.supplier.id = :supplierId
              and p.balanceAmount > 0
              and p.status in :statuses
            order by p.purchaseDate asc, p.id asc
            """)
    List<Purchase> findSettleable(
            @Param("supplierId") Long supplierId,
            @Param("statuses") Collection<PurchaseStatus> statuses);
}
