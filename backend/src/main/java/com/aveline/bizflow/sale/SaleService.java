package com.aveline.bizflow.sale;

import com.aveline.bizflow.audit.AuditEvent;
import com.aveline.bizflow.audit.AuditEventRepository;
import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
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
import com.aveline.bizflow.master.catalog.ProductBatch;
import com.aveline.bizflow.master.catalog.ProductBatchRepository;
import com.aveline.bizflow.master.catalog.ProductRepository;
import com.aveline.bizflow.master.catalog.ProductSerialRepository;
import com.aveline.bizflow.master.party.Customer;
import com.aveline.bizflow.master.party.CustomerLedgerRepository;
import com.aveline.bizflow.master.party.CustomerRepository;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleLineRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleUpdateRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.TenderRequest;
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
public class SaleService {

    private static final Set<String> SORTS = Set.of(
            "invoiceDate",
            "saleNumber",
            "grandTotal",
            "paidAmount",
            "balanceAmount",
            "status",
            "createdAt",
            "customer.customerName");
    private static final Pattern BATCH = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLedgerRepository ledgerRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final ProductSerialRepository serialRepository;
    private final PaymentRepository paymentRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final AuditEventRepository auditEventRepository;
    private final DocumentNumberService documentNumberService;
    private final InventoryPostingService inventoryPostingService;
    private final SaleFinanceService financeService;
    private final AuditRecorder auditRecorder;
    private final EntityManager entityManager;

    public SaleService(
            SaleRepository saleRepository,
            SaleReturnRepository returnRepository,
            CustomerRepository customerRepository,
            CustomerLedgerRepository ledgerRepository,
            ProductRepository productRepository,
            ProductBatchRepository batchRepository,
            ProductSerialRepository serialRepository,
            PaymentRepository paymentRepository,
            StockLedgerRepository stockLedgerRepository,
            AuditEventRepository auditEventRepository,
            DocumentNumberService documentNumberService,
            InventoryPostingService inventoryPostingService,
            SaleFinanceService financeService,
            AuditRecorder auditRecorder,
            EntityManager entityManager) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
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
    public PageResponse<SaleSummary> list(
            String search,
            SaleStatus status,
            SalePaymentType paymentType,
            Long customerId,
            String invoiceNumber,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sort) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "invoiceDate");
        Page<Sale> result = saleRepository.search(
                LikePatterns.contain(search),
                status,
                paymentType,
                customerId,
                LikePatterns.contain(invoiceNumber),
                fromDate,
                toDate,
                pageable);
        return new PageResponse<>(
                result.getContent().stream().map(SaleMapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SaleDetail get(Long id) {
        return detail(load(id));
    }

    public SaleDetail create(SaleRequest request) {
        Customer customer = requireActive(lockCustomer(request.customerId()));
        Prepared prepared = prepare(request, null);
        Sale sale = Sale.draft(documentNumberService.next(DocumentSeries.SALE, request.invoiceDate().getYear()));
        sale.apply(
                customer,
                request.invoiceDate(),
                request.paymentType(),
                prepared.referenceNumber(),
                prepared.notes(),
                prepared.quote(),
                Boolean.TRUE.equals(request.confirm()));
        addLines(sale, prepared);
        saleRepository.saveAndFlush(sale);
        if (Boolean.TRUE.equals(request.confirm())) {
            post(sale);
        }
        audit("SALE_CREATED", "create", sale);
        return detail(sale);
    }

    public SaleDetail update(Long id, SaleUpdateRequest request) {
        Sale sale = saleRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale was not found"));
        MasterVersions.check(sale.getVersion(), request.version());
        SaleWorkflow.assertEditable(sale.getStatus());
        if (hasReturns(sale)) {
            throw new BusinessRuleException("STATUS", "status", "A sale with returns cannot be edited");
        }
        lockPair(sale.getCustomer().getId(), request.customerId());
        Customer customer = requireActive(customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found")));
        boolean post = SaleWorkflow.isPosted(sale.getStatus()) || Boolean.TRUE.equals(request.confirm());
        Prepared prepared = prepare(new SaleRequest(
                request.customerId(),
                request.invoiceDate(),
                request.paymentType(),
                request.referenceNumber(),
                request.notes(),
                request.orderDiscount(),
                request.additionalCharges(),
                request.roundOff(),
                request.paidAmount(),
                post,
                request.items(),
                request.tenders()), sale);
        if (SaleWorkflow.isPosted(sale.getStatus())) {
            reverse(sale);
        }
        sale.clearLines();
        entityManager.flush();
        sale.apply(
                customer,
                request.invoiceDate(),
                request.paymentType(),
                prepared.referenceNumber(),
                prepared.notes(),
                prepared.quote(),
                post);
        addLines(sale, prepared);
        entityManager.flush();
        if (post) {
            post(sale);
        }
        audit("SALE_UPDATED", "update", sale);
        return detail(sale);
    }

    public SaleDetail confirm(Long id, Long version) {
        Sale sale = saleRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale was not found"));
        MasterVersions.check(sale.getVersion(), version);
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("STATUS", "status", "Only a draft sale can be completed");
        }
        requireActive(lockCustomer(sale.getCustomer().getId()));
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct().getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "items", item.getProduct().getName() + " is inactive");
            }
        }
        post(sale);
        sale.markPosted();
        audit("SALE_CONFIRMED", "confirm", sale);
        return detail(sale);
    }

    public SaleDetail cancel(Long id, Long version) {
        Sale sale = saleRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale was not found"));
        MasterVersions.check(sale.getVersion(), version);
        SaleWorkflow.assertCancellable(sale.getStatus());
        if (hasReturns(sale)) {
            throw new BusinessRuleException("STATUS", "status", "Cancel the returns before cancelling this sale");
        }
        lockCustomer(sale.getCustomer().getId());
        if (SaleWorkflow.isPosted(sale.getStatus())) {
            reverse(sale);
        }
        sale.markCancelled();
        audit("SALE_CANCELLED", "cancel", sale);
        return detail(sale);
    }

    private void post(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct().getStockType() == StockType.NONE) {
                continue;
            }
            BigDecimal cost = onHandCost(item);
            item.assignCost(cost);
            BigDecimal stockValue = item.getQuantity().multiply(cost).setScale(4, RoundingMode.HALF_UP);
            inventoryPostingService.issue(movement(sale, item, stockValue, StockTxnType.SALE, "Sale " + sale.getSaleNumber()));
        }
        financeService.postSale(sale);
    }

    private void reverse(Sale sale) {
        for (SaleItem item : new ArrayList<>(sale.getItems())) {
            if (item.getProduct().getStockType() == StockType.NONE) {
                continue;
            }
            BigDecimal stockValue = item.getQuantity().multiply(item.getUnitCost()).setScale(4, RoundingMode.HALF_UP);
            inventoryPostingService.receive(movement(
                    sale,
                    item,
                    stockValue,
                    StockTxnType.SALE_REVERSAL,
                    "Reversal of " + sale.getSaleNumber()));
        }
        financeService.reverseSale(sale);
    }

    private BigDecimal onHandCost(SaleItem item) {
        try {
            return inventoryPostingService.quoteIssueUnitCost(
                    item.getProduct(),
                    item.getBatchCode(),
                    item.getExpiryDate(),
                    item.getQuantity());
        } catch (BusinessRuleException ex) {
            if ("INSUFFICIENT_STOCK".equals(ex.getCode())) {
                throw new BusinessRuleException(
                        "INSUFFICIENT_STOCK",
                        "items",
                        item.getProduct().getName() + " does not have enough stock");
            }
            throw ex;
        }
    }

    private Movement movement(Sale sale, SaleItem item, BigDecimal stockValue, StockTxnType type, String remarks) {
        return new Movement(
                item.getProduct(),
                item.getBatchCode(),
                item.getExpiryDate(),
                item.getQuantity(),
                item.getUnitCost(),
                stockValue,
                type,
                SaleFinanceService.SALE,
                String.valueOf(sale.getId()),
                sale.getInvoiceDate(),
                remarks,
                item.getSerials().stream().map(SaleItemSerial::getSerialNumber).toList(),
                null,
                sale.getSaleNumber(),
                true,
                null);
    }

    private void addLines(Sale sale, Prepared prepared) {
        int lineNumber = 1;
        for (PreparedLine line : prepared.lines()) {
            SaleMath.LineQuote quote = line.quote();
            sale.addItem(SaleItem.create(
                    sale,
                    lineNumber++,
                    line.product(),
                    line.barcode(),
                    line.batchCode(),
                    line.expiryDate(),
                    line.quantity(),
                    line.price(),
                    quote.discount(),
                    line.taxPercent(),
                    quote.tax(),
                    quote.total(),
                    line.serials()));
        }
        int tenderNumber = 1;
        for (ResolvedTender tender : prepared.tenders()) {
            sale.addTender(SaleTender.create(sale, tenderNumber++, tender.method(), tender.amount(), tender.referenceNumber()));
        }
    }

    private Prepared prepare(SaleRequest request, Sale editing) {
        String reference = MasterText.optional(request.referenceNumber(), 64, "referenceNumber", "Reference number");
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        List<Long> productIds = request.items().stream().map(SaleLineRequest::productId).distinct().toList();
        Map<Long, Product> products = productRepository.findDetailedByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<SaleMath.LineInput> inputs = new ArrayList<>();
        List<SaleLineRequest> requests = request.items();
        for (SaleLineRequest line : requests) {
            Product product = products.get(line.productId());
            if (product == null) {
                throw new BusinessRuleException("PRODUCT", "items", "One of the products was not found");
            }
            if (product.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "items", product.getName() + " is inactive");
            }
            inputs.add(new SaleMath.LineInput(line.quantity(), line.sellingPrice(), line.discount(), line.tax()));
        }
        SaleMath.DocumentQuote quote = SaleMath.quote(
                inputs, request.orderDiscount(), request.additionalCharges(), request.roundOff(), request.paidAmount());
        List<PreparedLine> lines = new ArrayList<>();
        Set<String> serialsInDocument = new HashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            SaleLineRequest line = requests.get(index);
            Product product = products.get(line.productId());
            lines.add(normalize(product, line, quote.lines().get(index), index, editing, serialsInDocument));
        }
        List<ResolvedTender> tenders = tenders(request.paymentType(), quote.paidAmount(), reference, request.tenders());
        return new Prepared(reference, notes, quote, lines, tenders);
    }

    private PreparedLine normalize(
            Product product,
            SaleLineRequest line,
            SaleMath.LineQuote quote,
            int index,
            Sale editing,
            Set<String> serialsInDocument) {
        String field = "items[" + index + "]";
        BigDecimal quantity = scaleQuantity(line.quantity(), product, field + ".quantity", "Quantity");
        String batch = batch(line.batch(), field + ".batch");
        LocalDate expiry = line.expiryDate();
        if (product.getStockType() == StockType.BATCH || product.isBatchTracking()) {
            if (batch == null) {
                throw new BusinessRuleException("BATCH", field + ".batch", "Batch is required for " + product.getName());
            }
            ProductBatch recorded = batchRepository.findByProduct_IdAndBatchCode(product.getId(), batch)
                    .orElseThrow(() -> new BusinessRuleException(
                            "BATCH",
                            field + ".batch",
                            "Batch " + batch + " is not available for " + product.getName()));
            if (recorded.getStatus() != RecordStatus.ACTIVE) {
                throw new BusinessRuleException("BATCH", field + ".batch", "Batch " + batch + " is not active");
            }
            if (product.isExpiryTracking()) {
                if (expiry == null) {
                    throw new BusinessRuleException("EXPIRY", field + ".expiryDate", "Expiry date is required for " + product.getName());
                }
                if (recorded.getExpiresOn() != null && !recorded.getExpiresOn().equals(expiry)) {
                    throw new BusinessRuleException(
                            "BATCH_EXPIRY",
                            field + ".expiryDate",
                            "Batch " + batch + " is recorded with a different expiry date");
                }
            } else if (expiry != null) {
                throw new BusinessRuleException("EXPIRY", field + ".expiryDate", product.getName() + " does not track expiry");
            }
        } else if (batch != null) {
            throw new BusinessRuleException("BATCH", field + ".batch", product.getName() + " does not use batches");
        } else if (expiry != null) {
            throw new BusinessRuleException("EXPIRY", field + ".expiryDate", product.getName() + " does not track expiry");
        }
        BigDecimal price = MasterText.money(line.sellingPrice(), field + ".sellingPrice", "Selling price", true);
        if (price.compareTo(product.getMrp()) > 0) {
            throw new BusinessRuleException("PRICE", field + ".sellingPrice", "Selling price cannot be above MRP");
        }
        if (price.compareTo(product.getMinimumSellingPrice()) < 0) {
            throw new BusinessRuleException(
                    "PRICE",
                    field + ".sellingPrice",
                    "Selling price cannot be below the minimum selling price");
        }
        BigDecimal floor = product.getMinimumSellingPrice().multiply(quantity).setScale(4, RoundingMode.HALF_UP);
        if (quote.gross().subtract(quote.discount()).compareTo(floor) < 0) {
            throw new BusinessRuleException(
                    "DISCOUNT",
                    field + ".discount",
                    "Discount cannot take the line below the minimum selling price");
        }
        List<String> serials = serials(line.serials(), product, quantity, field, editing, serialsInDocument);
        String barcode = MasterText.barcode(line.barcode());
        if (barcode != null && product.getBarcode() != null && !barcode.equalsIgnoreCase(product.getBarcode())) {
            throw new BusinessRuleException("BARCODE", field + ".barcode", "Barcode does not match the selected product");
        }
        if (barcode == null) {
            barcode = product.getBarcode();
        }
        BigDecimal tax = MasterText.money(line.tax(), field + ".tax", "Tax", true);
        return new PreparedLine(product, barcode, batch, expiry, quantity, price, tax, serials, quote);
    }

    private List<ResolvedTender> tenders(
            SalePaymentType paymentType,
            BigDecimal paid,
            String headerReference,
            List<TenderRequest> requested) {
        List<TenderRequest> rows = requested == null ? List.of() : requested;
        if (paymentType == SalePaymentType.CREDIT) {
            if (paid.signum() != 0 || !rows.isEmpty()) {
                throw new BusinessRuleException(
                        "PAYMENT",
                        "paymentType",
                        "A credit sale cannot include a collected amount. Use cash, card, UPI, bank transfer, or mixed payments.");
            }
            return List.of();
        }
        if (paymentType == SalePaymentType.MIXED) {
            if (paid.signum() == 0) {
                if (!rows.isEmpty()) {
                    throw new BusinessRuleException("PAYMENT", "tenders", "Remove payment lines when nothing is collected");
                }
                return List.of();
            }
            if (rows.isEmpty()) {
                throw new BusinessRuleException("PAYMENT", "tenders", "Add a payment line for each amount collected");
            }
            List<ResolvedTender> tenders = new ArrayList<>();
            BigDecimal total = SaleMath.ZERO;
            int index = 0;
            for (TenderRequest row : rows) {
                String field = "tenders[" + index + "]";
                BigDecimal amount = MasterText.money(row.amount(), field + ".amount", "Amount", true);
                if (amount.signum() <= 0) {
                    throw new BusinessRuleException("PAYMENT", field + ".amount", "Each payment amount must be greater than zero");
                }
                String reference = MasterText.optional(row.referenceNumber(), 64, field + ".referenceNumber", "Reference number");
                tenders.add(new ResolvedTender(row.method(), amount, reference));
                total = total.add(amount);
                index++;
            }
            if (total.compareTo(paid) != 0) {
                throw new BusinessRuleException("PAYMENT", "tenders", "Payment lines must add up to the paid amount");
            }
            return List.copyOf(tenders);
        }
        if (!rows.isEmpty()) {
            throw new BusinessRuleException("PAYMENT", "tenders", "Payment lines are used only for mixed payments");
        }
        if (paid.signum() == 0) {
            return List.of();
        }
        return List.of(new ResolvedTender(SaleTenderMethod.valueOf(paymentType.name()), paid, headerReference));
    }

    private List<String> serials(
            List<String> raw,
            Product product,
            BigDecimal quantity,
            String field,
            Sale editing,
            Set<String> serialsInDocument) {
        List<String> serials = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                String serial = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
                if (!BATCH.matcher(serial).matches()) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Enter a valid serial number");
                }
                if (!serialsInDocument.add(product.getId() + ":" + serial)) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " is repeated on this sale");
                }
                serials.add(serial);
            }
        }
        if (product.isSerialTracking()) {
            int expected;
            try {
                expected = quantity.stripTrailingZeros().intValueExact();
            } catch (ArithmeticException ex) {
                throw new BusinessRuleException("SERIAL", field + ".serials", "Serial products must use whole quantities");
            }
            if (serials.size() != expected) {
                throw new BusinessRuleException("SERIAL", field + ".serials", "Enter one serial number for each quantity");
            }
            for (String serial : serials) {
                var existing = serialRepository.findByProduct_IdAndSerialNumber(product.getId(), serial).orElse(null);
                boolean held = heldBy(editing, product.getId(), serial);
                if (existing == null || (existing.getStatus() != RecordStatus.ACTIVE && !held)) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " is not in stock");
                }
            }
        } else if (!serials.isEmpty()) {
            throw new BusinessRuleException("SERIAL", field + ".serials", product.getName() + " does not use serial numbers");
        }
        return List.copyOf(serials);
    }

    private boolean heldBy(Sale sale, Long productId, String serial) {
        if (sale == null) {
            return false;
        }
        for (SaleItem item : sale.getItems()) {
            if (!item.getProduct().getId().equals(productId)) {
                continue;
            }
            for (SaleItemSerial row : item.getSerials()) {
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

    private Customer lockCustomer(Long id) {
        return customerRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
    }

    private void lockPair(Long left, Long right) {
        if (left.equals(right)) {
            lockCustomer(left);
            return;
        }
        if (left < right) {
            lockCustomer(left);
            lockCustomer(right);
        } else {
            lockCustomer(right);
            lockCustomer(left);
        }
    }

    private Customer requireActive(Customer customer) {
        if (customer.getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("CUSTOMER_INACTIVE", "customerId", "Choose an active customer");
        }
        return customer;
    }

    private boolean hasReturns(Sale sale) {
        return sale.getItems().stream().anyMatch(item -> item.getReturnedQuantity().signum() > 0);
    }

    private Sale load(Long id) {
        return saleRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale was not found"));
    }

    private SaleDetail detail(Sale sale) {
        String id = String.valueOf(sale.getId());
        List<SaleReturn> returns = returnRepository.findBySale_IdOrderByIdAsc(sale.getId());
        List<StockLedgerEntry> stock = new ArrayList<>(stockLedgerRepository.findByReferenceTypeAndReferenceIdOrderByIdAsc(
                SaleFinanceService.SALE, id));
        if (!returns.isEmpty()) {
            List<String> returnIds = returns.stream().map(row -> String.valueOf(row.getId())).toList();
            stock.addAll(stockLedgerRepository.findByReferenceTypeAndReferenceIdInOrderByIdAsc(
                    SaleFinanceService.SALES_RETURN, returnIds));
        }
        List<Payment> payments = paymentRepository.findBySourceTypeAndSourceIdOrderByIdAsc(SaleFinanceService.SALE, id);
        List<AuditEvent> audits = auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("sale", id);
        return SaleMapper.toDetail(sale, customerBalance(sale.getCustomer().getId()), payments, stock, returns, audits);
    }

    private BigDecimal customerBalance(Long customerId) {
        BigDecimal debit = ledgerRepository.sumDebit(customerId);
        BigDecimal credit = ledgerRepository.sumCredit(customerId);
        BigDecimal safeDebit = debit == null ? SaleMath.ZERO : debit;
        BigDecimal safeCredit = credit == null ? SaleMath.ZERO : credit;
        return safeDebit.subtract(safeCredit);
    }

    private void audit(String event, String action, Sale sale) {
        auditRecorder.record(event, action, AuditOutcome.SUCCESS, "sale", String.valueOf(sale.getId()), sale.getSaleNumber());
    }

    private record Prepared(
            String referenceNumber,
            String notes,
            SaleMath.DocumentQuote quote,
            List<PreparedLine> lines,
            List<ResolvedTender> tenders) {
    }

    private record PreparedLine(
            Product product,
            String barcode,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal taxPercent,
            List<String> serials,
            SaleMath.LineQuote quote) {
    }

    private record ResolvedTender(SaleTenderMethod method, BigDecimal amount, String referenceNumber) {
    }
}
