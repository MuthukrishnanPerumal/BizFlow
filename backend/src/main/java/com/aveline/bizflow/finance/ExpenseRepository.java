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
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> lockById(@Param("id") Long id);

    @Query(
            value = """
                    select e from Expense e
                    where (:status is null or e.status = :status)
                      and (:category is null or e.category = :category)
                      and (:method is null or e.paymentMethod = :method)
                      and (:fromDate is null or e.expenseDate >= :fromDate)
                      and (:toDate is null or e.expenseDate <= :toDate)
                      and (:minAmount is null or e.amount >= :minAmount)
                      and (:maxAmount is null or e.amount <= :maxAmount)
                      and (
                        :search is null
                        or lower(e.expenseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.description) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.notes) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """,
            countQuery = """
                    select count(e) from Expense e
                    where (:status is null or e.status = :status)
                      and (:category is null or e.category = :category)
                      and (:method is null or e.paymentMethod = :method)
                      and (:fromDate is null or e.expenseDate >= :fromDate)
                      and (:toDate is null or e.expenseDate <= :toDate)
                      and (:minAmount is null or e.amount >= :minAmount)
                      and (:maxAmount is null or e.amount <= :maxAmount)
                      and (
                        :search is null
                        or lower(e.expenseNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.description) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                        or lower(e.notes) like lower(concat('%', :search, '%')) escape '\\'
                      )
                    """)
    Page<Expense> search(
            @Param("search") String search,
            @Param("status") ExpenseStatus status,
            @Param("category") ExpenseCategory category,
            @Param("method") PaymentMethod method,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.status = com.aveline.bizflow.finance.ExpenseStatus.POSTED
              and (:category is null or e.category = :category)
              and (:method is null or e.paymentMethod = :method)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
              and (:minAmount is null or e.amount >= :minAmount)
              and (:maxAmount is null or e.amount <= :maxAmount)
              and (
                :search is null
                or lower(e.expenseNumber) like lower(concat('%', :search, '%')) escape '\\'
                or lower(e.description) like lower(concat('%', :search, '%')) escape '\\'
                or lower(e.referenceNumber) like lower(concat('%', :search, '%')) escape '\\'
                or lower(e.notes) like lower(concat('%', :search, '%')) escape '\\'
              )
            """)
    BigDecimal postedTotal(
            @Param("search") String search,
            @Param("category") ExpenseCategory category,
            @Param("method") PaymentMethod method,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);
}
