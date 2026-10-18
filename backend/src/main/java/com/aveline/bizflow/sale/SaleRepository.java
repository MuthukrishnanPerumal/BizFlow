package com.aveline.bizflow.sale;

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

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);

    @Query("""
            select distinct s from Sale s
            join fetch s.customer
            left join fetch s.items i
            left join fetch i.product p
            left join fetch p.unit
            where s.id = :id
            """)
    Optional<Sale> findDetailedById(@Param("id") Long id);

    @Query(
            value = """
                    select s from Sale s
                    join fetch s.customer c
                    where (:status is null or s.status = :status)
                      and (:paymentType is null or s.paymentType = :paymentType)
                      and (:customerId is null or c.id = :customerId)
                      and (:fromDate is null or s.invoiceDate >= :fromDate)
                      and (:toDate is null or s.invoiceDate <= :toDate)
                      and (:invoice is null or lower(s.saleNumber) like lower(concat('%', :invoice, '%')) escape '\\')
                      and (
                        :search is null
                        or lower(s.saleNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.phone) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(s) from Sale s
                    join s.customer c
                    where (:status is null or s.status = :status)
                      and (:paymentType is null or s.paymentType = :paymentType)
                      and (:customerId is null or c.id = :customerId)
                      and (:fromDate is null or s.invoiceDate >= :fromDate)
                      and (:toDate is null or s.invoiceDate <= :toDate)
                      and (:invoice is null or lower(s.saleNumber) like lower(concat('%', :invoice, '%')) escape '\\')
                      and (
                        :search is null
                        or lower(s.saleNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.phone) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<Sale> search(
            @Param("search") String search,
            @Param("status") SaleStatus status,
            @Param("paymentType") SalePaymentType paymentType,
            @Param("customerId") Long customerId,
            @Param("invoice") String invoice,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            select s from Sale s
            where s.customer.id = :customerId
              and s.balanceAmount > 0
              and s.status in :statuses
            order by s.invoiceDate asc, s.id asc
            """)
    List<Sale> findCollectible(
            @Param("customerId") Long customerId,
            @Param("statuses") Collection<SaleStatus> statuses);
}
