package com.aveline.bizflow.finance;

import com.aveline.bizflow.audit.AuditEvent;
import com.aveline.bizflow.audit.AuditEventRepository;
import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.finance.dto.FinanceDtos.AllocationRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.AllocationView;
import com.aveline.bizflow.finance.dto.FinanceDtos.AuditView;
import com.aveline.bizflow.finance.dto.FinanceDtos.CustomerPaymentRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.CustomerPaymentUpdateRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.JournalLineView;
import com.aveline.bizflow.finance.dto.FinanceDtos.OpenInvoice;
import com.aveline.bizflow.finance.dto.FinanceDtos.PaymentDetail;
import com.aveline.bizflow.finance.dto.FinanceDtos.PaymentReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.PaymentSummary;
import com.aveline.bizflow.finance.dto.FinanceDtos.SupplierPaymentRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.SupplierPaymentUpdateRequest;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.party.Customer;
import com.aveline.bizflow.master.party.CustomerRepository;
import com.aveline.bizflow.master.party.Supplier;
import com.aveline.bizflow.master.party.SupplierRepository;
import com.aveline.bizflow.purchase.Purchase;
import com.aveline.bizflow.purchase.PurchaseRepository;
import com.aveline.bizflow.purchase.PurchaseStatus;
import com.aveline.bizflow.sale.Sale;
import com.aveline.bizflow.sale.SaleRepository;
import com.aveline.bizflow.sale.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class PartyPaymentService {

    private static final Set<String> SORTS = Set.of("paymentDate", "paymentNumber", "amount", "status");
    private static final List<SaleStatus> COLLECTIBLE = List.of(
            SaleStatus.CONFIRMED, SaleStatus.COMPLETED, SaleStatus.PARTIALLY_RETURNED, SaleStatus.RETURNED);
    private static final List<PurchaseStatus> SETTLEABLE = List.of(
            PurchaseStatus.CONFIRMED, PurchaseStatus.COMPLETED, PurchaseStatus.PARTIALLY_RETURNED, PurchaseStatus.RETURNED);

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final DocumentNumberService documentNumberService;
    private final LedgerPostingService ledgerPostingService;
    private final JournalService journalService;
    private final AuditRecorder auditRecorder;
    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public PartyPaymentService(
            PaymentRepository paymentRepository,
            PaymentAllocationRepository allocationRepository,
            CustomerRepository customerRepository,
            SupplierRepository supplierRepository,
            SaleRepository saleRepository,
            PurchaseRepository purchaseRepository,
            DocumentNumberService documentNumberService,
            LedgerPostingService ledgerPostingService,
            JournalService journalService,
            AuditRecorder auditRecorder,
            AuditEventRepository auditEventRepository,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.documentNumberService = documentNumberService;
        this.ledgerPostingService = ledgerPostingService;
        this.journalService = journalService;
        this.auditRecorder = auditRecorder;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaymentReport customerPayments(
            String search,
            PaymentStatus status,
            PaymentMethod method,
            Long customerId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String scope,
            int page,
            int size,
            String sort) {
        return search(PaymentDirection.IN, source(scope, FinancePolicy.CUSTOMER_PAYMENT), status, method, customerId, search, fromDate, toDate, minAmount, maxAmount, page, size, sort);
    }

    @Transactional(readOnly = true)
    public PaymentReport supplierPayments(
            String search,
            PaymentStatus status,
            PaymentMethod method,
            Long supplierId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String scope,
            int page,
            int size,
            String sort) {
        return search(PaymentDirection.OUT, source(scope, FinancePolicy.SUPPLIER_PAYMENT), status, method, supplierId, search, fromDate, toDate, minAmount, maxAmount, page, size, sort);
    }

    @Transactional(readOnly = true)
    public PaymentDetail customerPayment(Long id) {
        Payment payment = load(id);
        if (payment.getCustomer() == null || payment.getDirection() != PaymentDirection.IN) {
            throw new ResourceNotFoundException("Customer payment was not found");
        }
        return detail(payment, ledgerPostingService.customerOutstanding(payment.getCustomer().getId()));
    }

    @Transactional(readOnly = true)
    public PaymentDetail supplierPayment(Long id) {
        Payment payment = load(id);
        if (payment.getSupplier() == null || payment.getDirection() != PaymentDirection.OUT) {
            throw new ResourceNotFoundException("Supplier payment was not found");
        }
        return detail(payment, ledgerPostingService.supplierOutstanding(payment.getSupplier().getId()));
    }

    @Transactional(readOnly = true)
    public List<OpenInvoice> openSales(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
        LocalDate today = LocalDate.now(clock);
        return saleRepository.findCollectible(customerId, COLLECTIBLE).stream()
                .map(sale -> openSale(sale, customer.getPaymentTerms(), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpenInvoice> openPurchases(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
        LocalDate today = LocalDate.now(clock);
        return purchaseRepository.findSettleable(supplierId, SETTLEABLE).stream()
                .map(purchase -> openPurchase(purchase, supplier.getPaymentTerms(), today))
                .toList();
    }

    @Transactional
    public PaymentDetail createCustomerPayment(CustomerPaymentRequest request) {
        Customer customer = customerRepository.lockById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
        requireActive(customer.getStatus(), "customerId", "customer");
        Settlement settlement = customerSettlement(customer, request.amount(), request.allocations());
        Payment payment = saveCustomer(customer, request.paymentDate(), request.paymentMethod(), settlement.amount(), settlement.allocated(), request.referenceNumber(), request.notes());
        applyCustomer(payment, settlement.documents());
        ledgerPostingService.postCustomerReceipt(payment);
        audit("PAYMENT_CREATED", "create", payment, "Recorded receipt " + payment.getPaymentNumber());
        return customerPayment(payment.getId());
    }

    @Transactional
    public PaymentDetail updateCustomerPayment(Long id, CustomerPaymentUpdateRequest request) {
        Payment payment = lockStandalone(id, FinancePolicy.CUSTOMER_PAYMENT, "Customer payment was not found");
        MasterVersions.check(payment.getVersion(), request.version());
        if (payment.getCustomer() == null || !payment.getCustomer().getId().equals(request.customerId())) {
            throw new BusinessRuleException("PARTY", "customerId", "The customer on a receipt cannot be changed. Cancel it and record a new receipt.");
        }
        Customer customer = customerRepository.lockById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
        requireActive(customer.getStatus(), "customerId", "customer");
        releaseCustomer(payment);
        Settlement settlement = customerSettlement(customer, request.amount(), request.allocations());
        payment.revise(requireMethod(request.paymentMethod()), request.paymentDate(), settlement.amount(), settlement.allocated(), reference(request.referenceNumber()), notes(request.notes(), 500));
        applyCustomer(payment, settlement.documents());
        ledgerPostingService.postCustomerReceipt(payment);
        audit("PAYMENT_UPDATED", "update", payment, "Updated receipt " + payment.getPaymentNumber());
        return customerPayment(payment.getId());
    }

    @Transactional
    public PaymentDetail cancelCustomerPayment(Long id, Long version) {
        Payment payment = lockStandalone(id, FinancePolicy.CUSTOMER_PAYMENT, "Customer payment was not found");
        MasterVersions.check(payment.getVersion(), version);
        releaseCustomer(payment);
        payment.cancel();
        audit("PAYMENT_CANCELLED", "cancel", payment, "Cancelled receipt " + payment.getPaymentNumber());
        return customerPayment(payment.getId());
    }

    @Transactional
    public PaymentDetail createSupplierPayment(SupplierPaymentRequest request) {
        Supplier supplier = supplierRepository.lockById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
        requireActive(supplier.getStatus(), "supplierId", "supplier");
        Settlement settlement = supplierSettlement(supplier, request.amount(), request.allocations());
        Payment payment = saveSupplier(supplier, request.paymentDate(), request.paymentMethod(), settlement.amount(), settlement.allocated(), request.referenceNumber(), request.notes());
        applySupplier(payment, settlement.documents());
        ledgerPostingService.postSupplierPayment(payment);
        audit("PAYMENT_CREATED", "create", payment, "Recorded payment " + payment.getPaymentNumber());
        return supplierPayment(payment.getId());
    }

    @Transactional
    public PaymentDetail updateSupplierPayment(Long id, SupplierPaymentUpdateRequest request) {
        Payment payment = lockStandalone(id, FinancePolicy.SUPPLIER_PAYMENT, "Supplier payment was not found");
        MasterVersions.check(payment.getVersion(), request.version());
        if (payment.getSupplier() == null || !payment.getSupplier().getId().equals(request.supplierId())) {
            throw new BusinessRuleException("PARTY", "supplierId", "The supplier on a payment cannot be changed. Cancel it and record a new payment.");
        }
        Supplier supplier = supplierRepository.lockById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
        requireActive(supplier.getStatus(), "supplierId", "supplier");
        releaseSupplier(payment);
        Settlement settlement = supplierSettlement(supplier, request.amount(), request.allocations());
        payment.revise(requireMethod(request.paymentMethod()), request.paymentDate(), settlement.amount(), settlement.allocated(), reference(request.referenceNumber()), notes(request.notes(), 500));
        applySupplier(payment, settlement.documents());
        ledgerPostingService.postSupplierPayment(payment);
        audit("PAYMENT_UPDATED", "update", payment, "Updated payment " + payment.getPaymentNumber());
        return supplierPayment(payment.getId());
    }

    @Transactional
    public PaymentDetail cancelSupplierPayment(Long id, Long version) {
        Payment payment = lockStandalone(id, FinancePolicy.SUPPLIER_PAYMENT, "Supplier payment was not found");
        MasterVersions.check(payment.getVersion(), version);
        releaseSupplier(payment);
        payment.cancel();
        audit("PAYMENT_CANCELLED", "cancel", payment, "Cancelled payment " + payment.getPaymentNumber());
        return supplierPayment(payment.getId());
    }

    private PaymentReport search(
            PaymentDirection direction,
            String sourceType,
            PaymentStatus status,
            PaymentMethod method,
            Long partyId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        BigDecimal minimum = optionalMoney(minAmount, "minAmount", "Minimum amount");
        BigDecimal maximum = optionalMoney(maxAmount, "maxAmount", "Maximum amount");
        requireRange(minimum, maximum);
        String term = LikePatterns.contain(search);
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "paymentDate");
        Page<Payment> result = paymentRepository.search(direction, sourceType, status, method, partyId, term, fromDate, toDate, minimum, maximum, pageable);
        PostedTotal posted = paymentRepository.postedTotals(direction, sourceType, method, partyId, term, fromDate, toDate, minimum, maximum);
        return new PaymentReport(
                result.getContent().stream().map(this::summary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                posted.amount(),
                posted.advance(),
                posted.count());
    }

    private Settlement customerSettlement(Customer customer, BigDecimal rawAmount, List<AllocationRequest> allocations) {
        BigDecimal amount = FinanceMath.positive(rawAmount, "amount", "Amount");
        Map<Long, LockedSale> locked = lockSales(customer.getId(), allocations, List.of());
        return settlement(amount, allocations, locked);
    }

    private Settlement supplierSettlement(Supplier supplier, BigDecimal rawAmount, List<AllocationRequest> allocations) {
        BigDecimal amount = FinanceMath.positive(rawAmount, "amount", "Amount");
        Map<Long, LockedPurchase> locked = lockPurchases(supplier.getId(), allocations, List.of());
        return settlement(amount, allocations, locked);
    }

    private void releaseCustomer(Payment payment) {
        List<PaymentAllocation> posted = allocationRepository.findByPaymentIdAndStatus(payment.getId(), AllocationStatus.POSTED);
        Map<Long, LockedSale> locked = lockSales(payment.getCustomer().getId(), List.of(), posted);
        for (PaymentAllocation allocation : posted) {
            locked.get(allocation.getDocumentId()).reverse().accept(allocation.getAllocatedAmount());
            allocation.reverse();
        }
        ledgerPostingService.reverseCustomerReceipt(payment);
    }

    private void releaseSupplier(Payment payment) {
        List<PaymentAllocation> posted = allocationRepository.findByPaymentIdAndStatus(payment.getId(), AllocationStatus.POSTED);
        Map<Long, LockedPurchase> locked = lockPurchases(payment.getSupplier().getId(), List.of(), posted);
        for (PaymentAllocation allocation : posted) {
            locked.get(allocation.getDocumentId()).reverse().accept(allocation.getAllocatedAmount());
            allocation.reverse();
        }
        ledgerPostingService.reverseSupplierPayment(payment);
    }

    private void applyCustomer(Payment payment, List<AppliedDocument> documents) {
        for (AppliedDocument document : documents) {
            document.apply().accept(document.amount());
            allocationRepository.save(PaymentAllocation.post(
                    payment, DocumentKind.SALE, document.id(), document.number(), document.amount()));
        }
    }

    private void applySupplier(Payment payment, List<AppliedDocument> documents) {
        for (AppliedDocument document : documents) {
            document.apply().accept(document.amount());
            allocationRepository.save(PaymentAllocation.post(
                    payment, DocumentKind.PURCHASE, document.id(), document.number(), document.amount()));
        }
    }

    private Map<Long, LockedSale> lockSales(Long customerId, List<AllocationRequest> requested, List<PaymentAllocation> existing) {
        List<Long> ids = new ArrayList<>();
        for (AllocationRequest request : safe(requested)) {
            if (request.invoiceId() != null) {
                ids.add(request.invoiceId());
            }
        }
        for (PaymentAllocation allocation : existing) {
            ids.add(allocation.getDocumentId());
        }
        ids = ids.stream().distinct().sorted().toList();
        Map<Long, LockedSale> locked = new LinkedHashMap<>();
        for (Long id : ids) {
            Sale sale = saleRepository.lockById(id)
                    .orElseThrow(() -> new BusinessRuleException("INVOICE", "allocations", "Invoice was not found"));
            if (!sale.getCustomer().getId().equals(customerId)) {
                throw new BusinessRuleException("INVOICE", "allocations", "Invoice " + sale.getSaleNumber() + " belongs to another customer");
            }
            locked.put(id, new LockedSale(
                    sale.getId(),
                    sale.getSaleNumber(),
                    sale.getStatus(),
                    FinanceMath.openReceivable(sale.getGrandTotal(), sale.getPaidAmount(), sale.getReturnedValue(), sale.getRefundedAmount()),
                    sale::collect,
                    sale::reverseCollection));
        }
        return locked;
    }

    private Map<Long, LockedPurchase> lockPurchases(Long supplierId, List<AllocationRequest> requested, List<PaymentAllocation> existing) {
        List<Long> ids = new ArrayList<>();
        for (AllocationRequest request : safe(requested)) {
            if (request.invoiceId() != null) {
                ids.add(request.invoiceId());
            }
        }
        for (PaymentAllocation allocation : existing) {
            ids.add(allocation.getDocumentId());
        }
        ids = ids.stream().distinct().sorted().toList();
        Map<Long, LockedPurchase> locked = new LinkedHashMap<>();
        for (Long id : ids) {
            Purchase purchase = purchaseRepository.lockById(id)
                    .orElseThrow(() -> new BusinessRuleException("INVOICE", "allocations", "Bill was not found"));
            if (!purchase.getSupplier().getId().equals(supplierId)) {
                throw new BusinessRuleException("INVOICE", "allocations", "Bill " + purchase.getPurchaseNumber() + " belongs to another supplier");
            }
            locked.put(id, new LockedPurchase(
                    purchase.getId(),
                    purchase.getPurchaseNumber(),
                    purchase.getStatus(),
                    FinanceMath.openPayable(purchase.getGrandTotal(), purchase.getPaidAmount(), purchase.getReturnedValue()),
                    purchase::settle,
                    purchase::reverseSettlement));
        }
        return locked;
    }

    private Settlement settlement(BigDecimal amount, List<AllocationRequest> requested, Map<Long, ? extends LockedDocument> locked) {
        BigDecimal allocated = FinanceMath.ZERO;
        Set<Long> seen = new HashSet<>();
        List<AppliedDocument> documents = new ArrayList<>();
        for (AllocationRequest request : safe(requested)) {
            if (request.invoiceId() == null) {
                throw new BusinessRuleException("INVOICE", "allocations", "Choose an invoice to allocate");
            }
            if (!seen.add(request.invoiceId())) {
                throw new BusinessRuleException("DUPLICATE_INVOICE", "allocations", "Each invoice can be allocated only once on a payment");
            }
            LockedDocument document = locked.get(request.invoiceId());
            if (document == null || !document.openForAllocation()) {
                throw new BusinessRuleException("INVOICE", "allocations", "Only an open posted invoice can receive a payment");
            }
            BigDecimal line = FinanceMath.positive(request.amount(), "allocations", "Allocation");
            if (line.compareTo(document.open()) > 0) {
                throw new BusinessRuleException(
                        "ALLOCATION",
                        "allocations",
                        "Allocation exceeds the open balance on " + document.number());
            }
            allocated = allocated.add(line);
            documents.add(new AppliedDocument(document.id(), document.number(), line, document.apply()));
        }
        if (allocated.compareTo(amount) > 0) {
            throw new BusinessRuleException("ALLOCATION", "amount", "Allocated amount cannot exceed the payment");
        }
        return new Settlement(amount, allocated, documents);
    }

    private Payment saveCustomer(
            Customer customer,
            LocalDate paymentDate,
            PaymentMethod method,
            BigDecimal amount,
            BigDecimal allocated,
            String referenceNumber,
            String notes) {
        String number = documentNumberService.next(DocumentSeries.PAYMENT, paymentDate.getYear());
        return paymentRepository.save(Payment.settlement(
                number,
                null,
                customer,
                PaymentDirection.IN,
                requireMethod(method),
                paymentDate,
                amount,
                allocated,
                FinancePolicy.CUSTOMER_PAYMENT,
                reference(referenceNumber),
                notes(notes, 500)));
    }

    private Payment saveSupplier(
            Supplier supplier,
            LocalDate paymentDate,
            PaymentMethod method,
            BigDecimal amount,
            BigDecimal allocated,
            String referenceNumber,
            String notes) {
        String number = documentNumberService.next(DocumentSeries.PAYMENT, paymentDate.getYear());
        return paymentRepository.save(Payment.settlement(
                number,
                supplier,
                null,
                PaymentDirection.OUT,
                requireMethod(method),
                paymentDate,
                amount,
                allocated,
                FinancePolicy.SUPPLIER_PAYMENT,
                reference(referenceNumber),
                notes(notes, 500)));
    }

    private Payment lockStandalone(Long id, String sourceType, String missing) {
        Payment payment = paymentRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException(missing));
        if (!sourceType.equals(payment.getSourceType())) {
            throw new BusinessRuleException(
                    "SOURCE",
                    "id",
                    "This payment was recorded on a document. Change or cancel that document instead.");
        }
        if (payment.getStatus() != PaymentStatus.POSTED) {
            throw new BusinessRuleException("STATUS", "status", "Only a posted payment can be changed");
        }
        return payment;
    }

    private Payment load(Long id) {
        return paymentRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
    }

    private PaymentDetail detail(Payment payment, BigDecimal outstanding) {
        List<AllocationView> allocations = allocationRepository.findByPaymentId(payment.getId()).stream()
                .map(row -> new AllocationView(
                        row.getDocumentId(),
                        row.getDocumentType().name(),
                        row.getDocumentNumber(),
                        row.getAllocatedAmount(),
                        row.getStatus()))
                .toList();
        JournalEntry journal = journalService.latest(payment.getSourceType(), String.valueOf(payment.getId()));
        if (journal == null && !payment.standalone()) {
            journal = journalService.latest(payment.getSourceType(), payment.getSourceId());
        }
        List<AuditView> audits = auditEventRepository
                .findByResourceTypeAndResourceIdOrderByCreatedAtDesc("payment", String.valueOf(payment.getId()))
                .stream()
                .map(this::auditView)
                .toList();
        boolean customer = payment.getCustomer() != null;
        return new PaymentDetail(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getPaymentDate(),
                customer ? payment.getCustomer().getId() : payment.getSupplier().getId(),
                customer ? payment.getCustomer().getCustomerCode() : payment.getSupplier().getSupplierCode(),
                customer ? payment.getCustomer().getCustomerName() : payment.getSupplier().getSupplierName(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getAllocatedAmount(),
                payment.getUnallocatedAmount(),
                payment.getStatus(),
                payment.getReferenceNumber(),
                payment.getNotes(),
                payment.getSourceType(),
                payment.getSourceId(),
                payment.standalone() && payment.getStatus() == PaymentStatus.POSTED,
                outstanding,
                journal == null ? null : journal.getStatus(),
                allocations,
                journal == null ? List.of() : journalLines(journal),
                audits,
                payment.getVersion());
    }

    private PaymentSummary summary(Payment payment) {
        boolean customer = payment.getCustomer() != null;
        return new PaymentSummary(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getPaymentDate(),
                customer ? payment.getCustomer().getId() : payment.getSupplier().getId(),
                customer ? payment.getCustomer().getCustomerCode() : payment.getSupplier().getSupplierCode(),
                customer ? payment.getCustomer().getCustomerName() : payment.getSupplier().getSupplierName(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getAllocatedAmount(),
                payment.getUnallocatedAmount(),
                payment.getStatus(),
                payment.getReferenceNumber(),
                payment.getSourceType(),
                payment.standalone() && payment.getStatus() == PaymentStatus.POSTED);
    }

    private OpenInvoice openSale(Sale sale, String terms, LocalDate today) {
        LocalDate due = PaymentTerms.dueDate(sale.getInvoiceDate(), terms);
        return new OpenInvoice(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getInvoiceDate(),
                sale.getGrandTotal(),
                sale.getPaidAmount(),
                sale.getBalanceAmount(),
                due,
                due != null && due.isBefore(today),
                due != null);
    }

    private OpenInvoice openPurchase(Purchase purchase, String terms, LocalDate today) {
        LocalDate due = PaymentTerms.dueDate(purchase.getPurchaseDate(), terms);
        return new OpenInvoice(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                purchase.getPurchaseDate(),
                purchase.getGrandTotal(),
                purchase.getPaidAmount(),
                purchase.getBalanceAmount(),
                due,
                due != null && due.isBefore(today),
                due != null);
    }

    private List<JournalLineView> journalLines(JournalEntry entry) {
        return entry.getLines().stream()
                .map(line -> new JournalLineView(
                        line.getLineNumber(),
                        line.getAccountCode(),
                        GlAccount.fromCode(line.getAccountCode()).label(),
                        line.getDebitAmount(),
                        line.getCreditAmount()))
                .toList();
    }

    private AuditView auditView(AuditEvent event) {
        return new AuditView(
                event.getEventType(),
                event.getActor(),
                event.getAction(),
                event.getOutcome().name(),
                event.getDetail(),
                event.getCreatedAt());
    }

    private void audit(String eventType, String action, Payment payment, String detail) {
        auditRecorder.record(eventType, action, AuditOutcome.SUCCESS, "payment", String.valueOf(payment.getId()), detail);
    }

    private static String source(String scope, String standalone) {
        return scope != null && "ALL".equalsIgnoreCase(scope.trim()) ? null : standalone;
    }

    private static PaymentMethod requireMethod(PaymentMethod method) {
        FinancePolicy.requireSettlementMethod(method);
        return method;
    }

    private static void requireActive(RecordStatus status, String field, String label) {
        if (status != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("INACTIVE", field, "Choose an active " + label);
        }
    }

    private static String reference(String value) {
        return MasterText.optional(value, 64, "referenceNumber", "Reference number");
    }

    private static String notes(String value, int max) {
        return MasterText.optional(value, max, "notes", "Notes");
    }

    private static BigDecimal optionalMoney(BigDecimal value, String field, String label) {
        return value == null ? null : MasterText.money(value, field, label, true);
    }

    private static void requireRange(BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new BusinessRuleException("AMOUNT_RANGE", "minAmount", "Minimum amount cannot exceed the maximum");
        }
    }

    private static List<AllocationRequest> safe(List<AllocationRequest> allocations) {
        return allocations == null ? List.of() : allocations;
    }

    private record Settlement(BigDecimal amount, BigDecimal allocated, List<AppliedDocument> documents) {
    }

    private record AppliedDocument(Long id, String number, BigDecimal amount, Consumer<BigDecimal> apply) {
    }

    private interface LockedDocument {
        Long id();

        String number();

        BigDecimal open();

        boolean openForAllocation();

        Consumer<BigDecimal> apply();
    }

    private record LockedSale(
            Long id,
            String number,
            SaleStatus status,
            BigDecimal open,
            Consumer<BigDecimal> apply,
            Consumer<BigDecimal> reverse) implements LockedDocument {

        @Override
        public boolean openForAllocation() {
            return COLLECTIBLE.contains(status);
        }
    }

    private record LockedPurchase(
            Long id,
            String number,
            PurchaseStatus status,
            BigDecimal open,
            Consumer<BigDecimal> apply,
            Consumer<BigDecimal> reverse) implements LockedDocument {

        @Override
        public boolean openForAllocation() {
            return SETTLEABLE.contains(status);
        }
    }
}
