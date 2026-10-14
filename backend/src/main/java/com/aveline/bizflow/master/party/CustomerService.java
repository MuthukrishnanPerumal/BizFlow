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
import com.aveline.bizflow.master.dto.PartyDtos.CustomerRequest;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerResponse;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerUpdateRequest;
import com.aveline.bizflow.master.dto.PartyDtos.LedgerEntryResponse;
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
public class CustomerService {

    private static final Set<String> SORTS = Set.of("customerCode", "customerName", "city", "status", "createdAt");
    private static final Set<String> LEDGER_SORTS = Set.of("entryDate", "createdAt");

    private final CustomerRepository customerRepository;
    private final CustomerLedgerRepository ledgerRepository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerLedgerRepository ledgerRepository,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, RecordStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "createdAt");
        Page<Customer> result = customerRepository.search(LikePatterns.contain(search), status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(customer -> PartyMapper.toCustomer(customer, null)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return PartyMapper.toCustomer(load(id), account(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(Long id, int page, int size, String sort) {
        load(id);
        Pageable pageable = PageRequests.of(page, size, sort, LEDGER_SORTS, "entryDate");
        Page<CustomerLedgerEntry> result = ledgerRepository.findByCustomer_Id(id, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(PartyMapper::toLedger).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public CustomerResponse create(CustomerRequest request) {
        PartyProfile profile = profile(request);
        assertCode(profile.code(), 0L);
        Customer customer = Customer.create(profile);
        save(customer);
        syncOpening(customer, profile.openingBalance());
        auditRecorder.record(
                "CUSTOMER_CREATED",
                "create",
                AuditOutcome.SUCCESS,
                "customer",
                String.valueOf(customer.getId()),
                customer.getCustomerCode());
        return PartyMapper.toCustomer(customer, account(customer.getId()));
    }

    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        Customer customer = load(id);
        MasterVersions.check(customer.getVersion(), request.version());
        PartyProfile profile = profile(request);
        assertCode(profile.code(), customer.getId());
        customer.update(profile);
        save(customer);
        syncOpening(customer, profile.openingBalance());
        auditRecorder.record(
                "CUSTOMER_UPDATED",
                "update",
                AuditOutcome.SUCCESS,
                "customer",
                String.valueOf(customer.getId()),
                customer.getCustomerCode());
        return PartyMapper.toCustomer(customer, account(customer.getId()));
    }

    public void delete(Long id) {
        Customer customer = load(id);
        if (ledgerRepository.countByCustomer_IdAndEntryTypeNot(id, LedgerEntryType.OPENING) > 0) {
            throw new BusinessRuleException(
                    "CUSTOMER_IN_USE",
                    "This customer has transactions. Set it to inactive instead of deleting it.");
        }
        String code = customer.getCustomerCode();
        customer.retire();
        save(customer);
        auditRecorder.record("CUSTOMER_DELETED", "delete", AuditOutcome.SUCCESS, "customer", String.valueOf(id), code);
    }

    private PartyProfile profile(CustomerRequest request) {
        return PartyProfiles.normalize(
                request.customerCode(),
                "customerCode",
                request.customerName(),
                "customerName",
                "Customer name",
                request.companyName(),
                null,
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

    private PartyProfile profile(CustomerUpdateRequest request) {
        return PartyProfiles.normalize(
                request.customerCode(),
                "customerCode",
                request.customerName(),
                "customerName",
                "Customer name",
                request.companyName(),
                null,
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

    private void syncOpening(Customer customer, BigDecimal opening) {
        BigDecimal debit = PartyProfiles.customerDebit(opening);
        BigDecimal credit = PartyProfiles.customerCredit(opening);
        CustomerLedgerEntry existing = ledgerRepository
                .findByCustomer_IdAndEntryType(customer.getId(), LedgerEntryType.OPENING)
                .orElse(null);
        if (existing == null) {
            if (opening.signum() == 0) {
                return;
            }
            ledgerRepository.saveAndFlush(CustomerLedgerEntry.opening(customer, LocalDate.now(clock), debit, credit));
            return;
        }
        existing.replaceOpening(existing.getEntryDate(), debit, credit);
        ledgerRepository.saveAndFlush(existing);
    }

    private AccountSummary account(Long id) {
        return PartyMapper.customerAccount(amount(ledgerRepository.sumDebit(id)), amount(ledgerRepository.sumCredit(id)));
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? PartyProfiles.scaledZero() : value;
    }

    private Customer load(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
    }

    private void assertCode(String code, long excludingId) {
        if (customerRepository.countLiveCode(code, excludingId) > 0) {
            throw new ConflictException("customerCode", "A customer with this code already exists");
        }
    }

    private void save(Customer customer) {
        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("customerCode", "A customer with this code already exists");
        }
    }
}
