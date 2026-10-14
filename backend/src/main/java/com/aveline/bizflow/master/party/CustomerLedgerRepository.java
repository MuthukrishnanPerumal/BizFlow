package com.aveline.bizflow.master.party;

import com.aveline.bizflow.master.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerLedgerRepository extends JpaRepository<CustomerLedgerEntry, Long> {

    Page<CustomerLedgerEntry> findByCustomer_Id(Long customerId, Pageable pageable);

    Optional<CustomerLedgerEntry> findByCustomer_IdAndEntryType(Long customerId, LedgerEntryType entryType);

    long countByCustomer_IdAndEntryTypeNot(Long customerId, LedgerEntryType entryType);

    List<CustomerLedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    @Query("select sum(e.debitAmount) from CustomerLedgerEntry e where e.customer.id = :customerId")
    BigDecimal sumDebit(@Param("customerId") Long customerId);

    @Query("select sum(e.creditAmount) from CustomerLedgerEntry e where e.customer.id = :customerId")
    BigDecimal sumCredit(@Param("customerId") Long customerId);
}
