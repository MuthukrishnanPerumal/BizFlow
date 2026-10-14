package com.aveline.bizflow.purchase;

import com.aveline.bizflow.audit.AuditEvent;
import com.aveline.bizflow.audit.AuditEventRepository;
import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ConflictException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.finance.Payment;
import com.aveline.bizflow.finance.PaymentRepository;
import com.aveline.bizflow.inventory.InventoryPostingService;
import com.aveline.bizflow.inventory.InventoryPostingService.Movement;
import com.aveline.bizflow.inventory.StockLedgerEntry;
import com.aveline.bizflow.inventory.StockLedgerRepository;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import com.aveline.bizflow.master.catalog.ProductRepository;
import com.aveline.bizflow.master.catalog.ProductSerialRepository;
import com.aveline.bizflow.master.party.Supplier;
import com.aveline.bizflow.master.party.SupplierLedgerRepository;
import com.aveline.bizflow.master.party.SupplierRepository;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseLineRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseSummary;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseUpdateRequest;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseService {

    private static final Set<String> SORTS = Set.of(
            "purchaseDate",
            "purchaseNumber",
            "invoiceNumber",
            "grandTotal",
            "paidAmount",
            "balanceAmount",
            "status",
            "createdAt",
            "supplier.supplierName");
    private static final Pattern BATCH = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository returnRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierLedgerRepository ledgerRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository serialRepository;
    private final PaymentRepository paymentRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final AuditEventRepository auditEventRepository;
    private final DocumentNumberService documentNumberService;
    private final InventoryPostingService inventoryPostingService;
    private final PurchaseFinanceService financeService;
    private final AuditRecorder auditRecorder;
    private final EntityManager entityManager;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            PurchaseReturnRepository returnRepository,
            SupplierRepository supplierRepository,
            SupplierLedgerRepository ledgerRepository,
            ProductRepository productRepository,
            ProductSerialRepository serialRepository,
            PaymentRepository paymentRepository,
            StockLedgerRepository stockLedgerRepository,
            AuditEventRepository auditEventRepository,
            DocumentNumberService documentNumberService,
            InventoryPostingService inventoryPostingService,
            PurchaseFinanceService financeService,
            AuditRecorder auditRecorder,
            EntityManager entityManager) {
        this.purchaseRepository = purchaseRepository;
        this.returnRepository = returnRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerRepository = ledgerRepository;
        this.productRepository = productRepository;
        this.serialRepository = serialRepository;
        this.paymentRepository = paymentRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.auditEventRepository = auditEventRepository;
        this.documentNumberService = documentNumberService;
        this.inventoryPostingService = inventoryPostingService;
        this.financeService = financeService;
        this.auditRecorder = auditRecorder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummary> list(
            String search,
            PurchaseStatus status,
            Long supplierId,
            String invoiceNumber,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sort) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "purchaseDate");
        Page<Purchase> result = purchaseRepository.search(
                LikePatterns.contain(search),
                status,
                supplierId,
                LikePatterns.contain(invoiceNumber),
                fromDate,
                toDate,
                pageable);
        return new PageResponse<>(
                result.getContent().stream().map(PurchaseMapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PurchaseDetail get(Long id) {
        return detail(load(id));
    }

    public PurchaseDetail create(PurchaseRequest request) {
        Supplier supplier = requireActive(lockSupplier(request.supplierId()));
        Prepared prepared = prepare(request, null);
        assertInvoice(supplier.getId(), prepared.invoiceNumber(), 0L);
        Purchase purchase = Purchase.draft(documentNumberService.next(DocumentSeries.PURCHASE, request.purchaseDate().getYear()));
        purchase.apply(
                supplier,
                prepared.invoiceNumber(),
                request.purchaseDate(),
                request.paymentType(),
                prepared.referenceNumber(),
                prepared.notes(),
                prepared.quote(),
                Boolean.TRUE.equals(request.confirm()));
        addItems(purchase, prepared);
        purchaseRepository.saveAndFlush(purchase);
        if (Boolean.TRUE.equals(request.confirm())) {
            post(purchase);
        }
        audit("PURCHASE_CREATED", "create", purchase);
        return detail(purchase);
    }

    public PurchaseDetail update(Long id, PurchaseUpdateRequest request) {
        Purchase purchase = purchaseRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase was not found"));
        MasterVersions.check(purchase.getVersion(), request.version());
        PurchaseWorkflow.assertEditable(purchase.getStatus());
        if (hasReturns(purchase)) {
            throw new BusinessRuleException("STATUS", "status", "A purchase with returns cannot be edited");
        }
        lockPair(purchase.getSupplier().getId(), request.supplierId());
        Supplier supplier = requireActive(supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found")));
        boolean post = PurchaseWorkflow.isPosted(purchase.getStatus()) || Boolean.TRUE.equals(request.confirm());
        Prepared prepared = prepare(new PurchaseRequest(
                request.supplierId(),
                request.invoiceNumber(),
                request.purchaseDate(),
                request.paymentType(),
                request.referenceNumber(),
                request.notes(),
                request.billDiscount(),
                request.additionalCharges(),
                request.roundOff(),
                request.paidAmount(),
                post,
                request.items()), purchase);
        assertInvoice(supplier.getId(), prepared.invoiceNumber(), purchase.getId());
        if (PurchaseWorkflow.isPosted(purchase.getStatus())) {
            reverse(purchase);
        }
        purchase.clearItems();
        entityManager.flush();
        purchase.apply(
                supplier,
                prepared.invoiceNumber(),
                request.purchaseDate(),
                request.paymentType(),
                prepared.referenceNumber(),
                prepared.notes(),
                prepared.quote(),
                post);
        addItems(purchase, prepared);
        entityManager.flush();
        if (post) {
            post(purchase);
        }
        audit("PURCHASE_UPDATED", "update", purchase);
        return detail(purchase);
    }

    public PurchaseDetail confirm(Long id, Long version) {
        Purchase purchase = purchaseRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase was not found"));
        MasterVersions.check(purchase.getVersion(), version);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessRuleException("STATUS", "status", "Only a draft purchase can be confirmed");
        }
        requireActive(lockSupplier(purchase.getSupplier().getId()));
        for (PurchaseItem item : purchase.getItems()) {
            if (item.getProduct().getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "items", item.getProduct().getName() + " is inactive");
            }
        }
        post(purchase);
        purchase.markPosted();
        audit("PURCHASE_CONFIRMED", "confirm", purchase);
        return detail(purchase);
    }

    public PurchaseDetail cancel(Long id, Long version) {
        Purchase purchase = purchaseRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase was not found"));
        MasterVersions.check(purchase.getVersion(), version);
        PurchaseWorkflow.assertCancellable(purchase.getStatus());
        if (hasReturns(purchase)) {
            throw new BusinessRuleException("STATUS", "status", "Cancel the returns before cancelling this purchase");
        }
        lockSupplier(purchase.getSupplier().getId());
        if (PurchaseWorkflow.isPosted(purchase.getStatus())) {
            reverse(purchase);
        }
        purchase.markCancelled();
        audit("PURCHASE_CANCELLED", "cancel", purchase);
        return detail(purchase);
    }

    private void post(Purchase purchase) {
        for (PurchaseItem item : purchase.getItems()) {
            inventoryPostingService.receive(movement(purchase, item, StockTxnType.PURCHASE, "Purchase " + purchase.getPurchaseNumber()));
        }
        financeService.postPurchase(purchase);
    }

    private void reverse(Purchase purchase) {
        for (PurchaseItem item : new ArrayList<>(purchase.getItems())) {
            inventoryPostingService.issue(movement(
                    purchase,
                    item,
                    StockTxnType.PURCHASE_REVERSAL,
                    "Reversal of " + purchase.getPurchaseNumber()));
        }
        financeService.reversePurchase(purchase);
    }

    private Movement movement(Purchase purchase, PurchaseItem item, StockTxnType type, String remarks) {
        BigDecimal stockValue = item.getQuantity()
                .multiply(item.getPurchasePrice())
                .setScale(4, RoundingMode.HALF_UP)
                .subtract(item.getDiscountAmount());
        if (stockValue.signum() < 0) {
            stockValue = PurchaseMath.ZERO;
        }
        return new Movement(
                item.getProduct(),
                item.getBatchCode(),
                item.getExpiryDate(),
                item.getStockQuantity(),
                item.getUnitCost(),
                stockValue,
                type,
                PurchaseFinanceService.PURCHASE,
                String.valueOf(purchase.getId()),
                purchase.getPurchaseDate(),
                remarks,
                item.getSerials().stream().map(PurchaseItemSerial::getSerialNumber).toList(),
                null,
                purchase.getPurchaseNumber(),
                true,
                null);
    }

    private void addItems(Purchase purchase, Prepared prepared) {
        int lineNumber = 1;
        for (PreparedLine line : prepared.lines()) {
            PurchaseMath.LineQuote quote = line.quote();
            purchase.addItem(PurchaseItem.create(
                    purchase,
                    lineNumber++,
                    line.product(),
                    line.barcode(),
                    line.batchCode(),
                    line.expiryDate(),
                    line.quantity(),
                    line.freeQuantity(),
                    line.price(),
                    quote.discount(),
                    line.taxPercent(),
                    quote.tax(),
                    quote.total(),
                    quote.unitCost(),
                    quote.stockQuantity(),
                    line.serials()));
        }
    }

    private Prepared prepare(PurchaseRequest request, Purchase editing) {
        String invoice = MasterText.required(request.invoiceNumber(), 64, "invoiceNumber", "Invoice number")
                .toUpperCase(Locale.ROOT);
        String reference = MasterText.optional(request.referenceNumber(), 64, "referenceNumber", "Reference number");
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        List<Long> productIds = request.items().stream().map(PurchaseLineRequest::productId).distinct().toList();
        Map<Long, Product> products = productRepository.findDetailedByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<PurchaseMath.LineInput> inputs = new ArrayList<>();
        List<PurchaseLineRequest> requests = request.items();
        for (PurchaseLineRequest line : requests) {
            Product product = products.get(line.productId());
            if (product == null) {
                throw new BusinessRuleException("PRODUCT", "items", "One of the products was not found");
            }
            if (product.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "items", product.getName() + " is inactive");
            }
            inputs.add(new PurchaseMath.LineInput(
                    line.quantity(), line.freeQuantity(), line.purchasePrice(), line.discount(), line.tax()));
        }
        PurchaseMath.DocumentQuote quote = PurchaseMath.quote(
                inputs, request.billDiscount(), request.additionalCharges(), request.roundOff(), request.paidAmount());
        List<PreparedLine> lines = new ArrayList<>();
        Set<String> serialsInDocument = new HashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            PurchaseLineRequest line = requests.get(index);
            Product product = products.get(line.productId());
            PurchaseMath.LineQuote lineQuote = quote.lines().get(index);
            lines.add(normalize(product, line, lineQuote, request.purchaseDate(), index, editing, serialsInDocument));
        }
        return new Prepared(invoice, reference, notes, quote, lines);
    }

    private PreparedLine normalize(
            Product product,
            PurchaseLineRequest line,
            PurchaseMath.LineQuote quote,
            LocalDate purchaseDate,
            int index,
            Purchase editing,
            Set<String> serialsInDocument) {
        String field = "items[" + index + "]";
        BigDecimal quantity = scaleQuantity(line.quantity(), product, field + ".quantity", "Quantity");
        BigDecimal free = line.freeQuantity() == null
                ? PurchaseMath.ZERO
                : scaleQuantity(line.freeQuantity(), product, field + ".freeQuantity", "Free quantity");
        String batch = batch(line.batch(), field + ".batch");
        LocalDate expiry = line.expiryDate();
        if (product.getStockType() == StockType.BATCH || product.isBatchTracking()) {
            if (batch == null) {
                throw new BusinessRuleException("BATCH", field + ".batch", "Batch is required for " + product.getName());
            }
        } else if (batch != null) {
            throw new BusinessRuleException("BATCH", field + ".batch", product.getName() + " does not use batches");
        }
        if (product.isExpiryTracking()) {
            if (expiry == null) {
                throw new BusinessRuleException("EXPIRY", field + ".expiryDate", "Expiry date is required for " + product.getName());
            }
            if (expiry.isBefore(purchaseDate)) {
                throw new BusinessRuleException("EXPIRY", field + ".expiryDate", "Expiry date cannot be before the purchase date");
            }
        } else if (expiry != null) {
            throw new BusinessRuleException("EXPIRY", field + ".expiryDate", product.getName() + " does not track expiry");
        }
        List<String> serials = serials(line.serials(), product, quote.stockQuantity(), field, editing, serialsInDocument);
        String barcode = MasterText.barcode(line.barcode());
        if (barcode != null && product.getBarcode() != null && !barcode.equalsIgnoreCase(product.getBarcode())) {
            throw new BusinessRuleException("BARCODE", field + ".barcode", "Barcode does not match the selected product");
        }
        if (barcode == null) {
            barcode = product.getBarcode();
        }
        BigDecimal price = MasterText.money(line.purchasePrice(), field + ".purchasePrice", "Purchase price", true);
        BigDecimal tax = MasterText.money(line.tax(), field + ".tax", "Tax", true);
        return new PreparedLine(product, barcode, batch, expiry, quantity, free, price, tax, serials, quote);
    }

    private List<String> serials(
            List<String> raw,
            Product product,
            BigDecimal stockQuantity,
            String field,
            Purchase editing,
            Set<String> serialsInDocument) {
        List<String> serials = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                String serial = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
                if (!BATCH.matcher(serial).matches()) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Enter a valid serial number");
                }
                if (!serialsInDocument.add(product.getId() + ":" + serial)) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " is repeated on this purchase");
                }
                serials.add(serial);
            }
        }
        if (product.isSerialTracking()) {
            int expected;
            try {
                expected = stockQuantity.stripTrailingZeros().intValueExact();
            } catch (ArithmeticException ex) {
                throw new BusinessRuleException("SERIAL", field + ".serials", "Serial products must use whole quantities");
            }
            if (serials.size() != expected) {
                throw new BusinessRuleException(
                        "SERIAL",
                        field + ".serials",
                        "Enter one serial number for each quantity, including free quantity");
            }
            for (String serial : serials) {
                serialRepository.findByProduct_IdAndSerialNumber(product.getId(), serial).ifPresent(existing -> {
                    if (existing.getStatus() == RecordStatus.ACTIVE && !heldBy(editing, product.getId(), serial)) {
                        throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " is already in stock");
                    }
                });
            }
        } else if (!serials.isEmpty()) {
            throw new BusinessRuleException("SERIAL", field + ".serials", product.getName() + " does not use serial numbers");
        }
        return List.copyOf(serials);
    }

    private boolean heldBy(Purchase purchase, Long productId, String serial) {
        if (purchase == null) {
            return false;
        }
        for (PurchaseItem item : purchase.getItems()) {
            if (!item.getProduct().getId().equals(productId)) {
                continue;
            }
            for (PurchaseItemSerial row : item.getSerials()) {
                if (!row.isReturned() && serial.equals(row.getSerialNumber())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String batch(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (!BATCH.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "BATCH",
                    field,
                    "Batch may contain letters, numbers, dots, underscores, or hyphens");
        }
        return value;
    }

    private BigDecimal scaleQuantity(BigDecimal value, Product product, String field, String label) {
        BigDecimal scaled = MasterText.money(value, field, label, true);
        if (!product.getUnit().isDecimalAllowed() && scaled.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("WHOLE_QUANTITY", field, label + " must be a whole number for this unit");
        }
        return scaled;
    }

    private void assertInvoice(Long supplierId, String invoiceNumber, long purchaseId) {
        if (purchaseRepository.countLiveInvoice(supplierId, invoiceNumber, purchaseId) > 0) {
            throw new ConflictException("invoiceNumber", "This supplier invoice number is already in use");
        }
    }

    private Supplier lockSupplier(Long id) {
        return supplierRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
    }

    private void lockPair(Long left, Long right) {
        if (left.equals(right)) {
            lockSupplier(left);
            return;
        }
        if (left < right) {
            lockSupplier(left);
            lockSupplier(right);
        } else {
            lockSupplier(right);
            lockSupplier(left);
        }
    }

    private Supplier requireActive(Supplier supplier) {
        if (supplier.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("SUPPLIER_INACTIVE", "supplierId", "Choose an active supplier");
        }
        return supplier;
    }

    private boolean hasReturns(Purchase purchase) {
        return purchase.getItems().stream().anyMatch(item ->
                item.getReturnedQuantity().signum() > 0 || item.getReturnedFreeQuantity().signum() > 0);
    }

    private Purchase load(Long id) {
        return purchaseRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase was not found"));
    }

    private PurchaseDetail detail(Purchase purchase) {
        String id = String.valueOf(purchase.getId());
        List<PurchaseReturn> returns = returnRepository.findByPurchase_IdOrderByIdAsc(purchase.getId());
        List<StockLedgerEntry> stock = new ArrayList<>(stockLedgerRepository.findByReferenceTypeAndReferenceIdOrderByIdAsc(
                PurchaseFinanceService.PURCHASE, id));
        if (!returns.isEmpty()) {
            List<String> returnIds = returns.stream().map(row -> String.valueOf(row.getId())).toList();
            stock.addAll(stockLedgerRepository.findByReferenceTypeAndReferenceIdInOrderByIdAsc(
                    PurchaseFinanceService.PURCHASE_RETURN, returnIds));
        }
        List<Payment> payments = paymentRepository.findBySourceTypeAndSourceIdOrderByIdAsc(PurchaseFinanceService.PURCHASE, id);
        List<AuditEvent> audits = auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("purchase", id);
        return PurchaseMapper.toDetail(purchase, supplierBalance(purchase.getSupplier().getId()), payments, stock, returns, audits);
    }

    private BigDecimal supplierBalance(Long supplierId) {
        BigDecimal debit = ledgerRepository.sumDebit(supplierId);
        BigDecimal credit = ledgerRepository.sumCredit(supplierId);
        BigDecimal safeDebit = debit == null ? PurchaseMath.ZERO : debit;
        BigDecimal safeCredit = credit == null ? PurchaseMath.ZERO : credit;
        return safeCredit.subtract(safeDebit);
    }

    private void audit(String event, String action, Purchase purchase) {
        auditRecorder.record(event, action, AuditOutcome.SUCCESS, "purchase", String.valueOf(purchase.getId()), purchase.getPurchaseNumber());
    }

    private record Prepared(
            String invoiceNumber,
            String referenceNumber,
            String notes,
            PurchaseMath.DocumentQuote quote,
            List<PreparedLine> lines) {
    }

    private record PreparedLine(
            Product product,
            String barcode,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal price,
            BigDecimal taxPercent,
            List<String> serials,
            PurchaseMath.LineQuote quote) {
    }
}
