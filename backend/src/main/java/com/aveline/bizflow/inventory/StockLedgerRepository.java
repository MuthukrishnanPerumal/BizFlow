package com.aveline.bizflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockLedgerRepository extends JpaRepository<StockLedgerEntry, Long> {

    List<StockLedgerEntry> findByReferenceTypeAndReferenceIdOrderByIdAsc(String referenceType, String referenceId);

    List<StockLedgerEntry> findByReferenceTypeAndReferenceIdInOrderByIdAsc(String referenceType, Collection<String> referenceIds);
}
