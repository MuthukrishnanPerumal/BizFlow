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

public interface SupplierLedgerRepository extends JpaRepository<SupplierLedgerEntry, Long> {

    Page<SupplierLedgerEntry> findBySupplier_Id(Long supplierId, Pageable pageable);

    Optional<SupplierLedgerEntry> findBySupplier_IdAndEntryType(Long supplierId, LedgerEntryType entryType);

    long countBySupplier_IdAndEntryTypeNot(Long supplierId, LedgerEntryType entryType);

    List<SupplierLedgerEntry> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);

    @Query("select sum(e.debitAmount) from SupplierLedgerEntry e where e.supplier.id = :supplierId")
    BigDecimal sumDebit(@Param("supplierId") Long supplierId);

    @Query("select sum(e.creditAmount) from SupplierLedgerEntry e where e.supplier.id = :supplierId")
    BigDecimal sumCredit(@Param("supplierId") Long supplierId);
}
