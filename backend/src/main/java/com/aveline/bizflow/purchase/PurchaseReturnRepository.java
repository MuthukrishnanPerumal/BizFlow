package com.aveline.bizflow.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    List<PurchaseReturn> findByPurchase_IdOrderByIdAsc(Long purchaseId);

    @Query("""
            select r from PurchaseReturn r
            join fetch r.purchase p
            join fetch r.supplier s
            where r.id = :id
            """)
    java.util.Optional<PurchaseReturn> findDetailedById(@Param("id") Long id);

    @Query(
            value = """
                    select r from PurchaseReturn r
                    join fetch r.purchase p
                    join fetch r.supplier s
                    where (:supplierId is null or s.id = :supplierId)
                      and (:purchaseId is null or p.id = :purchaseId)
                      and (:fromDate is null or r.returnDate >= :fromDate)
                      and (:toDate is null or r.returnDate <= :toDate)
                      and (
                        :search is null
                        or lower(r.returnNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.purchaseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.invoiceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(r) from PurchaseReturn r
                    join r.purchase p
                    join r.supplier s
                    where (:supplierId is null or s.id = :supplierId)
                      and (:purchaseId is null or p.id = :purchaseId)
                      and (:fromDate is null or r.returnDate >= :fromDate)
                      and (:toDate is null or r.returnDate <= :toDate)
                      and (
                        :search is null
                        or lower(r.returnNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.purchaseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.invoiceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<PurchaseReturn> search(
            @Param("search") String search,
            @Param("supplierId") Long supplierId,
            @Param("purchaseId") Long purchaseId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}
