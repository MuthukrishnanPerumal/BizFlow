package com.aveline.bizflow.master.party;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.master.LedgerEntryType;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.dto.PartyDtos.AccountSummary;
import com.aveline.bizflow.master.dto.PartyDtos.LedgerEntryResponse;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierRequest;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierResponse;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Service
@Transactional
public class SupplierService {

    private static final Set<String> SORTS = Set.of("supplierCode", "supplierName", "city", "status", "createdAt");
    private static final Set<String> LEDGER_SORTS = Set.of("entryDate", "createdAt");

    private final SupplierRepository supplierRepository;
    private final SupplierLedgerRepository ledgerRepository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierLedgerRepository ledgerRepository,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.supplierRepository = supplierRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String search, RecordStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<Supplier> result = supplierRepository.search(LikePatterns.contain(search), status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(supplier -> PartyMapper.toSupplier(supplier, null)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        Supplier supplier = load(id);
        return PartyMapper.toSupplier(supplier, account(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(Long id, int page, int size, String sort) {
        load(id);
        Pageable pageable = PageRequests.of(page, size, sort, LEDGER_SORTS, "entryDate");
        Page<SupplierLedgerEntry> result = ledgerRepository.findBySupplier_Id(id, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(PartyMapper::toLedger).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public SupplierResponse create(SupplierRequest request) {
        PartyProfile profile = profile(request);
        assertCode(profile.code(), 0L);
        Supplier supplier = Supplier.create(profile);
        save(supplier);
        syncOpening(supplier, profile.openingBalance(), true);
        auditRecorder.record(
                "SUPPLIER_CREATED",
                "create",
                AuditOutcome.SUCCESS,
                "supplier",
                String.valueOf(supplier.getId()),
                supplier.getSupplierCode());
        return PartyMapper.toSupplier(supplier, account(supplier.getId()));
    }

    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        Supplier supplier = load(id);
        MasterVersions.check(supplier.getVersion(), request.version());
        PartyProfile profile = profile(request);
        assertCode(profile.code(), supplier.getId());
        supplier.update(profile);
        save(supplier);
        syncOpening(supplier, profile.openingBalance(), false);
        auditRecorder.record(
                "SUPPLIER_UPDATED",
                "update",
                AuditOutcome.SUCCESS,
                "supplier",
                String.valueOf(supplier.getId()),
                supplier.getSupplierCode());
        return PartyMapper.toSupplier(supplier, account(supplier.getId()));
    }

    public void delete(Long id) {
        Supplier supplier = load(id);
        if (ledgerRepository.countBySupplier_IdAndEntryTypeNot(id, LedgerEntryType.OPENING) > 0) {
            throw new BusinessRuleException(
                    "SUPPLIER_IN_USE",
                    "This supplier has transactions. Set it to inactive instead of deleting it.");
        }
        String code = supplier.getSupplierCode();
        supplier.retire();
        save(supplier);
        auditRecorder.record("SUPPLIER_DELETED", "delete", AuditOutcome.SUCCESS, "supplier", String.valueOf(id), code);
    }

    private PartyProfile profile(SupplierRequest request) {
        return PartyProfiles.normalize(
                request.supplierCode(),
                "supplierCode",
                request.supplierName(),
                "supplierName",
                "Supplier name",
                request.companyName(),
                request.contactPerson(),
                request.phone(),
                request.alternatePhone(),
                request.email(),
                request.taxNumber(),
                request.address(),
                request.country(),
                request.state(),
                request.city(),
                request.pincode(),
                request.openingBalance(),
                request.creditLimit(),
                request.paymentTerms(),
                request.status());
    }

    private PartyProfile profile(SupplierUpdateRequest request) {
        return PartyProfiles.normalize(
                request.supplierCode(),
                "supplierCode",
                request.supplierName(),
                "supplierName",
                "Supplier name",
                request.companyName(),
                request.contactPerson(),
                request.phone(),
                request.alternatePhone(),
                request.email(),
                request.taxNumber(),
                request.address(),
                request.country(),
                request.state(),
                request.city(),
                request.pincode(),
                request.openingBalance(),
                request.creditLimit(),
                request.paymentTerms(),
                request.status());
    }

    private void syncOpening(Supplier supplier, BigDecimal opening, boolean creating) {
        BigDecimal debit = PartyProfiles.supplierDebit(opening);
        BigDecimal credit = PartyProfiles.supplierCredit(opening);
        SupplierLedgerEntry existing = ledgerRepository
                .findBySupplier_IdAndEntryType(supplier.getId(), LedgerEntryType.OPENING)
                .orElse(null);
        if (existing == null) {
            if (opening.signum() == 0) {
                return;
            }
            ledgerRepository.saveAndFlush(SupplierLedgerEntry.opening(
                    supplier,
                    LocalDate.now(clock),
                    debit,
                    credit));
            return;
        }
        if (creating) {
            return;
        }
        existing.replaceOpening(existing.getEntryDate(), debit, credit);
        ledgerRepository.saveAndFlush(existing);
    }

    private AccountSummary account(Long id) {
        return PartyMapper.supplierAccount(amount(ledgerRepository.sumDebit(id)), amount(ledgerRepository.sumCredit(id)));
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? PartyProfiles.scaledZero() : value;
    }

    private Supplier load(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
    }

    private void assertCode(String code, long excludingId) {
        if (supplierRepository.countLiveCode(code, excludingId) > 0) {
            throw new ConflictException("supplierCode", "A supplier with this code already exists");
        }
    }

    private void save(Supplier supplier) {
        try {
            supplierRepository.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("supplierCode", "A supplier with this code already exists");
        }
    }
}
