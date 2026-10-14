package com.aveline.bizflow.purchase;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.inventory.InventoryPostingService;
import com.aveline.bizflow.inventory.InventoryPostingService.Movement;
import com.aveline.bizflow.inventory.StockLedgerRepository;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import com.aveline.bizflow.master.party.SupplierLedgerRepository;
import com.aveline.bizflow.master.party.SupplierRepository;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseReturnRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnLineRequest;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnSummary;
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
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class PurchaseReturnService {

    private static final Set<String> SORTS = Set.of("returnDate", "returnNumber", "grandTotal", "createdAt");
    private static final Pattern SERIAL = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository returnRepository;
    private final PurchaseReturnItemRepository returnItemRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierLedgerRepository ledgerRepository;
    private final DocumentNumberService documentNumberService;
    private final InventoryPostingService inventoryPostingService;
    private final PurchaseFinanceService financeService;
    private final StockLedgerRepository stockLedgerRepository;
    private final AuditRecorder auditRecorder;

    public PurchaseReturnService(
            PurchaseRepository purchaseRepository,
            PurchaseReturnRepository returnRepository,
            PurchaseReturnItemRepository returnItemRepository,
            SupplierRepository supplierRepository,
            SupplierLedgerRepository ledgerRepository,
            DocumentNumberService documentNumberService,
            InventoryPostingService inventoryPostingService,
            PurchaseFinanceService financeService,
            StockLedgerRepository stockLedgerRepository,
            AuditRecorder auditRecorder) {
        this.purchaseRepository = purchaseRepository;
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerRepository = ledgerRepository;
        this.documentNumberService = documentNumberService;
        this.inventoryPostingService = inventoryPostingService;
        this.financeService = financeService;
        this.stockLedgerRepository = stockLedgerRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnSummary> list(
            String search,
            Long supplierId,
            Long purchaseId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sort) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "returnDate");
        Page<PurchaseReturn> result = returnRepository.search(
                LikePatterns.contain(search), supplierId, purchaseId, fromDate, toDate, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(PurchaseMapper::toReturnSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ReturnDetail get(Long id) {
        return detail(load(id));
    }

    public ReturnDetail create(PurchaseReturnRequest request) {
        Purchase purchase = purchaseRepository.lockById(request.purchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase was not found"));
        MasterVersions.check(purchase.getVersion(), request.purchaseVersion());
        PurchaseWorkflow.assertReturnable(purchase.getStatus());
        supplierRepository.lockById(purchase.getSupplier().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier was not found"));
        if (request.returnDate().isBefore(purchase.getPurchaseDate())) {
            throw new BusinessRuleException("DATE", "returnDate", "Return date cannot be before the purchase date");
        }
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        Set<Long> seen = new HashSet<>();
        List<PricedReturn> priced = new ArrayList<>();
        BigDecimal merchandise = PurchaseMath.ZERO;
        for (int index = 0; index < request.items().size(); index++) {
            ReturnLineRequest line = request.items().get(index);
            if (!seen.add(line.purchaseItemId())) {
                throw new BusinessRuleException("DUPLICATE_LINE", "items", "Each purchase line can be returned only once per document");
            }
            PurchaseItem item = purchase.getItems().stream()
                    .filter(row -> row.getId().equals(line.purchaseItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("ITEM", "items", "The purchase line was not found on this invoice"));
            PricedReturn pricedLine = price(item, line, index);
            item.addReturned(pricedLine.quantity(), pricedLine.freeQuantity());
            priced.add(pricedLine);
            merchandise = merchandise.add(pricedLine.total());
        }
        boolean allReturned = purchase.getItems().stream().allMatch(item ->
                item.remainingQuantity().signum() == 0 && item.remainingFreeQuantity().signum() == 0);
        BigDecimal goods = purchase.getItems().stream()
                .map(PurchaseItem::getLineTotal)
                .reduce(PurchaseMath.ZERO, BigDecimal::add);
        BigDecimal remainingDocument = purchase.getGrandTotal().subtract(purchase.getReturnedValue());
        if (remainingDocument.signum() < 0) {
            remainingDocument = PurchaseMath.ZERO;
        }
        BigDecimal grand = documentValue(allReturned, goods, merchandise, purchase.getGrandTotal(), remainingDocument);
        BigDecimal returnedValue = purchase.getReturnedValue().add(grand);
        BigDecimal balance = purchase.getGrandTotal().subtract(purchase.getPaidAmount()).subtract(returnedValue);
        if (balance.signum() < 0) {
            balance = PurchaseMath.ZERO;
        }
        purchase.applyReturn(returnedValue, balance, PurchaseWorkflow.afterReturn(true, allReturned));
        PurchaseReturn purchaseReturn = PurchaseReturn.open(
                documentNumberService.next(DocumentSeries.PURCHASE_RETURN, request.returnDate().getYear()),
                purchase,
                request.returnDate(),
                notes);
        int lineNumber = 1;
        for (PricedReturn line : priced) {
            purchaseReturn.addItem(PurchaseReturnItem.create(
                    purchaseReturn,
                    line.item(),
                    lineNumber++,
                    line.quantity(),
                    line.freeQuantity(),
                    line.discount(),
                    line.tax(),
                    line.total(),
                    line.serials()));
        }
        purchaseReturn.totals(merchandise, grand);
        returnRepository.saveAndFlush(purchaseReturn);
        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            issue(purchaseReturn, item);
        }
        financeService.postReturn(purchaseReturn);
        auditRecorder.record(
                "PURCHASE_RETURNED",
                "return",
                AuditOutcome.SUCCESS,
                "purchase",
                String.valueOf(purchase.getId()),
                purchaseReturn.getReturnNumber());
        return detail(purchaseReturn);
    }

    private BigDecimal documentValue(
            boolean allReturned,
            BigDecimal goods,
            BigDecimal merchandise,
            BigDecimal grandTotal,
            BigDecimal remainingDocument) {
        if (allReturned) {
            return remainingDocument;
        }
        if (goods.signum() == 0) {
            return PurchaseMath.ZERO;
        }
        BigDecimal share = grandTotal.multiply(merchandise).divide(goods, 4, RoundingMode.HALF_UP);
        return share.compareTo(remainingDocument) > 0 ? remainingDocument : share;
    }

    private PricedReturn price(PurchaseItem item, ReturnLineRequest line, int index) {
        String field = "items[" + index + "]";
        BigDecimal quantity = MasterText.money(line.quantity(), field + ".quantity", "Quantity", true);
        BigDecimal free = line.freeQuantity() == null
                ? PurchaseMath.ZERO
                : MasterText.money(line.freeQuantity(), field + ".freeQuantity", "Free quantity", true);
        if (!item.getProduct().getUnit().isDecimalAllowed()) {
            whole(quantity, field + ".quantity", "Quantity");
            whole(free, field + ".freeQuantity", "Free quantity");
        }
        if (quantity.signum() < 0 || free.signum() < 0) {
            throw new BusinessRuleException("QUANTITY", field + ".quantity", "Return quantity cannot be negative");
        }
        if (quantity.signum() == 0 && free.signum() == 0) {
            throw new BusinessRuleException("QUANTITY", field + ".quantity", "Return quantity must be greater than zero");
        }
        if (quantity.compareTo(item.remainingQuantity()) > 0) {
            throw new BusinessRuleException(
                    "RETURN_QUANTITY",
                    field + ".quantity",
                    "Return quantity cannot be greater than the quantity still available on this purchase");
        }
        if (free.compareTo(item.remainingFreeQuantity()) > 0) {
            throw new BusinessRuleException(
                    "RETURN_QUANTITY",
                    field + ".freeQuantity",
                    "Free return quantity cannot be greater than the free quantity still available");
        }
        List<PurchaseReturnItem> previous = returnItemRepository.findByPurchaseItem_Id(item.getId());
        BigDecimal previousDiscount = sum(previous, PurchaseReturnItem::getDiscountAmount);
        BigDecimal previousTax = sum(previous, PurchaseReturnItem::getTaxAmount);
        BigDecimal previousTotal = sum(previous, PurchaseReturnItem::getLineTotal);
        boolean finishesPaid = quantity.signum() > 0
                && item.getReturnedQuantity().add(quantity).compareTo(item.getQuantity()) == 0;
        BigDecimal discount;
        BigDecimal tax;
        BigDecimal total;
        if (quantity.signum() == 0) {
            discount = PurchaseMath.ZERO;
            tax = PurchaseMath.ZERO;
            total = PurchaseMath.ZERO;
        } else if (finishesPaid) {
            discount = clamp(item.getDiscountAmount().subtract(previousDiscount));
            tax = clamp(item.getTaxAmount().subtract(previousTax));
            total = clamp(item.getLineTotal().subtract(previousTotal));
        } else {
            discount = PurchaseMath.ratio(item.getDiscountAmount(), quantity, item.getQuantity());
            tax = PurchaseMath.ratio(item.getTaxAmount(), quantity, item.getQuantity());
            BigDecimal gross = quantity.multiply(item.getPurchasePrice()).setScale(4, RoundingMode.HALF_UP);
            total = gross.subtract(discount).add(tax);
        }
        List<String> serials = serials(item, line.serials(), quantity.add(free), field);
        return new PricedReturn(item, quantity, free, discount, tax, total, serials);
    }

    private List<String> serials(PurchaseItem item, List<String> raw, BigDecimal units, String field) {
        List<String> serials = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                String serial = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
                if (!SERIAL.matcher(serial).matches()) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Enter a valid serial number");
                }
                serials.add(serial);
            }
        }
        if (!item.getProduct().isSerialTracking()) {
            if (!serials.isEmpty()) {
                throw new BusinessRuleException("SERIAL", field + ".serials", "This product does not use serial numbers");
            }
            return List.of();
        }
        int expected;
        try {
            expected = units.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("SERIAL", field + ".serials", "Serial products must use whole quantities");
        }
        if (serials.size() != expected) {
            throw new BusinessRuleException("SERIAL", field + ".serials", "Select each serial number being returned");
        }
        for (String serial : serials) {
            PurchaseItemSerial match = item.getSerials().stream()
                    .filter(row -> serial.equals(row.getSerialNumber()) && !row.isReturned())
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "SERIAL",
                            field + ".serials",
                            "Serial " + serial + " is not available to return on this line"));
            match.markReturned();
        }
        return List.copyOf(serials);
    }

    private void issue(PurchaseReturn purchaseReturn, PurchaseReturnItem item) {
        PurchaseItem source = item.getPurchaseItem();
        BigDecimal quantity = item.getQuantity().add(item.getFreeQuantity());
        BigDecimal stockValue = item.getLineTotal().subtract(item.getTaxAmount());
        if (stockValue.signum() < 0) {
            stockValue = PurchaseMath.ZERO;
        }
        inventoryPostingService.issue(new Movement(
                source.getProduct(),
                source.getBatchCode(),
                source.getExpiryDate(),
                quantity,
                source.getUnitCost(),
                stockValue,
                StockTxnType.PURCHASE_RETURN,
                PurchaseFinanceService.PURCHASE_RETURN,
                String.valueOf(purchaseReturn.getId()),
                purchaseReturn.getReturnDate(),
                "Purchase return " + purchaseReturn.getReturnNumber(),
                item.getSerials().stream().map(PurchaseReturnSerial::getSerialNumber).toList(),
                null,
                purchaseReturn.getReturnNumber(),
                true,
                null));
    }

    private ReturnDetail detail(PurchaseReturn purchaseReturn) {
        return PurchaseMapper.toReturnDetail(
                purchaseReturn,
                supplierBalance(purchaseReturn.getSupplier().getId()),
                stockLedgerRepository.findByReferenceTypeAndReferenceIdOrderByIdAsc(
                        PurchaseFinanceService.PURCHASE_RETURN, String.valueOf(purchaseReturn.getId())));
    }

    private PurchaseReturn load(Long id) {
        return returnRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return was not found"));
    }

    private BigDecimal supplierBalance(Long supplierId) {
        BigDecimal debit = ledgerRepository.sumDebit(supplierId);
        BigDecimal credit = ledgerRepository.sumCredit(supplierId);
        return (credit == null ? PurchaseMath.ZERO : credit).subtract(debit == null ? PurchaseMath.ZERO : debit);
    }

    private void whole(BigDecimal value, String field, String label) {
        if (value.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("WHOLE_QUANTITY", field, label + " must be a whole number for this unit");
        }
    }

    private BigDecimal clamp(BigDecimal value) {
        return value.signum() < 0 ? PurchaseMath.ZERO : value;
    }

    private BigDecimal sum(List<PurchaseReturnItem> items, java.util.function.Function<PurchaseReturnItem, BigDecimal> reader) {
        return items.stream().map(reader).reduce(PurchaseMath.ZERO, BigDecimal::add);
    }

    private record PricedReturn(
            PurchaseItem item,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            List<String> serials) {
    }
}
