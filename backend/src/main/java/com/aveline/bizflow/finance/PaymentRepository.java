package com.aveline.bizflow.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySourceTypeAndSourceIdOrderByIdAsc(String sourceType, String sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    @Query("""
            select p from Payment p
            left join fetch p.customer
            left join fetch p.supplier
            where p.id = :id
            """)
    Optional<Payment> findDetailedById(@Param("id") Long id);

    @Query(
            value = """
                    select p from Payment p
                    left join p.customer c
                    left join p.supplier s
                    where p.direction = :direction
                      and (:sourceType is null or p.sourceType = :sourceType)
                      and (:status is null or p.status = :status)
                      and (:method is null or p.paymentMethod = :method)
                      and (:partyId is null or c.id = :partyId or s.id = :partyId)
                      and (:fromDate is null or p.paymentDate >= :fromDate)
                      and (:toDate is null or p.paymentDate <= :toDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (
                        :search is null
                        or lower(p.paymentNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.notes) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(p) from Payment p
                    left join p.customer c
                    left join p.supplier s
                    where p.direction = :direction
                      and (:sourceType is null or p.sourceType = :sourceType)
                      and (:status is null or p.status = :status)
                      and (:method is null or p.paymentMethod = :method)
                      and (:partyId is null or c.id = :partyId or s.id = :partyId)
                      and (:fromDate is null or p.paymentDate >= :fromDate)
                      and (:toDate is null or p.paymentDate <= :toDate)
                      and (:minAmount is null or p.amount >= :minAmount)
                      and (:maxAmount is null or p.amount <= :maxAmount)
                      and (
                        :search is null
                        or lower(p.paymentNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(p.notes) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<Payment> search(
            @Param("direction") PaymentDirection direction,
            @Param("sourceType") String sourceType,
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("partyId") Long partyId,
            @Param("search") String search,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

    @Query("""
            select new com.aveline.bizflow.finance.PostedTotal(
                coalesce(sum(p.amount), 0),
                coalesce(sum(p.unallocatedAmount), 0),
                count(p))
            from Payment p
            left join p.customer c
            left join p.supplier s
            where p.direction = :direction
              and p.status = com.aveline.bizflow.finance.PaymentStatus.POSTED
              and (:sourceType is null or p.sourceType = :sourceType)
              and (:method is null or p.paymentMethod = :method)
              and (:partyId is null or c.id = :partyId or s.id = :partyId)
              and (:fromDate is null or p.paymentDate >= :fromDate)
              and (:toDate is null or p.paymentDate <= :toDate)
              and (:minAmount is null or p.amount >= :minAmount)
              and (:maxAmount is null or p.amount <= :maxAmount)
              and (
                :search is null
                or lower(p.paymentNumber) like lower(concat('%', :search, '%')) escape '\\'
                or lower(p.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                or lower(p.notes) like lower(concat('%', :search, '%')) escape '\\'
                or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                or lower(c.customerCode) like lower(concat('%', :search, '%')) escape '\\'
                or lower(s.supplierName) like lower(concat('%', :search, '%')) escape '\\'
                or lower(s.supplierCode) like lower(concat('%', :search, '%')) escape '\\'
              )
            """)
    PostedTotal postedTotals(
            @Param("direction") PaymentDirection direction,
            @Param("sourceType") String sourceType,
            @Param("method") PaymentMethod method,
            @Param("partyId") Long partyId,
            @Param("search") String search,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);
}
