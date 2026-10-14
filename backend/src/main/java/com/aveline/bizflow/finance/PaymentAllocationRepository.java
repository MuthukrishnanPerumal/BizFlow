package com.aveline.bizflow.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    @Query("""
            select a from PaymentAllocation a
            where a.payment.id = :paymentId
            order by a.id asc
            """)
    List<PaymentAllocation> findByPaymentId(@Param("paymentId") Long paymentId);

    @Query("""
            select a from PaymentAllocation a
            where a.payment.id = :paymentId
              and a.status = :status
            order by a.documentId asc, a.id asc
            """)
    List<PaymentAllocation> findByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") AllocationStatus status);
}
