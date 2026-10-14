package com.aveline.bizflow.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    List<SaleReturn> findBySale_IdOrderByIdAsc(Long saleId);

    @Query("""
            select r from SaleReturn r
            join fetch r.sale s
            join fetch r.customer c
            where r.id = :id
            """)
    Optional<SaleReturn> findDetailedById(@Param("id") Long id);

    @Query(
            value = """
                    select r from SaleReturn r
                    join fetch r.sale s
                    join fetch r.customer c
                    where (:customerId is null or c.id = :customerId)
                      and (:saleId is null or s.id = :saleId)
                      and (:fromDate is null or r.returnDate >= :fromDate)
                      and (:toDate is null or r.returnDate <= :toDate)
                      and (
                        :search is null
                        or lower(r.returnNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.saleNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(r.reason) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(r) from SaleReturn r
                    join r.sale s
                    join r.customer c
                    where (:customerId is null or c.id = :customerId)
                      and (:saleId is null or s.id = :saleId)
                      and (:fromDate is null or r.returnDate >= :fromDate)
                      and (:toDate is null or r.returnDate <= :toDate)
                      and (
                        :search is null
                        or lower(r.returnNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(s.saleNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(c.customerName) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(r.reason) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<SaleReturn> search(
            @Param("search") String search,
            @Param("customerId") Long customerId,
            @Param("saleId") Long saleId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}
