package com.aveline.bizflow.sale;

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
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.party.CustomerLedgerRepository;
import com.aveline.bizflow.master.party.CustomerRepository;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnLineRequest;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@Transactional
public class SaleReturnService {

    private static final Set<String> SORTS = Set.of("returnDate", "returnNumber", "grandTotal", "createdAt");
    private static final Pattern SERIAL = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final SaleReturnItemRepository returnItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLedgerRepository ledgerRepository;
    private final DocumentNumberService documentNumberService;
    private final InventoryPostingService inventoryPostingService;
    private final SaleFinanceService financeService;
    private final StockLedgerRepository stockLedgerRepository;
    private final AuditRecorder auditRecorder;

    public SaleReturnService(
            SaleRepository saleRepository,
            SaleReturnRepository returnRepository,
            SaleReturnItemRepository returnItemRepository,
            CustomerRepository customerRepository,
            CustomerLedgerRepository ledgerRepository,
            DocumentNumberService documentNumberService,
            InventoryPostingService inventoryPostingService,
            SaleFinanceService financeService,
            StockLedgerRepository stockLedgerRepository,
            AuditRecorder auditRecorder) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.customerRepository = customerRepository;
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
            Long customerId,
            Long saleId,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            int page,
            int size,
            String sort) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "returnDate");
        Page<SaleReturn> result = returnRepository.search(
                LikePatterns.contain(search), customerId, saleId, fromDate, toDate, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(SaleMapper::toReturnSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ReturnDetail get(Long id) {
        return detail(load(id));
    }

    public ReturnDetail create(SaleReturnRequest request) {
        Sale sale = saleRepository.lockById(request.saleId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale was not found"));
        MasterVersions.check(sale.getVersion(), request.saleVersion());
        SaleWorkflow.assertReturnable(sale.getStatus());
        customerRepository.lockById(sale.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer was not found"));
        if (request.returnDate().isBefore(sale.getInvoiceDate())) {
            throw new BusinessRuleException("DATE", "returnDate", "Return date cannot be before the invoice date");
        }
        String reason = MasterText.required(request.reason(), 500, "reason", "Reason");
        String notes = MasterText.optional(request.notes(), 1000, "notes", "Notes");
        Set<Long> seen = new HashSet<>();
        List<PricedReturn> priced = new ArrayList<>();
        BigDecimal merchandise = SaleMath.ZERO;
        BigDecimal itemDiscount = SaleMath.ZERO;
        BigDecimal tax = SaleMath.ZERO;
        for (int index = 0; index < request.items().size(); index++) {
            ReturnLineRequest line = request.items().get(index);
            if (!seen.add(line.saleItemId())) {
                throw new BusinessRuleException("DUPLICATE_LINE", "items", "Each sale line can be returned only once per document");
            }
            SaleItem item = sale.getItems().stream()
                    .filter(row -> row.getId().equals(line.saleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("ITEM", "items", "The sale line was not found on this invoice"));
            PricedReturn pricedLine = price(item, line, index);
            item.addReturned(pricedLine.quantity());
            priced.add(pricedLine);
            merchandise = merchandise.add(pricedLine.total());
            itemDiscount = itemDiscount.add(pricedLine.discount());
            tax = tax.add(pricedLine.tax());
        }
        boolean allReturned = sale.getItems().stream().allMatch(item -> item.remainingQuantity().signum() == 0);
        BigDecimal goods = sale.getItems().stream().map(SaleItem::getLineTotal).reduce(SaleMath.ZERO, BigDecimal::add);
        BigDecimal remainingDocument = sale.getGrandTotal().subtract(sale.getReturnedValue());
        if (remainingDocument.signum() < 0) {
            remainingDocument = SaleMath.ZERO;
        }
        BigDecimal grand = documentValue(allReturned, goods, merchandise, sale.getGrandTotal(), remainingDocument);
        BigDecimal previousOrder = returnRepository.findBySale_IdOrderByIdAsc(sale.getId()).stream()
                .map(SaleReturn::getOrderDiscountReversal)
                .reduce(SaleMath.ZERO, BigDecimal::add);
        BigDecimal remainingOrder = SaleMath.clamp(sale.getOrderDiscount().subtract(previousOrder));
        BigDecimal orderShare = allReturned
                ? remainingOrder
                : SaleMath.ratio(sale.getOrderDiscount(), merchandise, goods);
        if (orderShare.compareTo(remainingOrder) > 0) {
            orderShare = remainingOrder;
        }
        BigDecimal cashRefund = request.refundMethod().settlesInCash() ? grand : SaleMath.ZERO;
        BigDecimal refundable = sale.getPaidAmount().subtract(sale.getRefundedAmount());
        if (cashRefund.compareTo(refundable) > 0) {
            throw new BusinessRuleException(
                    "REFUND",
                    "refundMethod",
                    "A cash refund cannot exceed the amount still collected on this invoice. Use a credit adjustment for the rest.");
        }
        BigDecimal returnedValue = sale.getReturnedValue().add(grand);
        BigDecimal refundedAmount = sale.getRefundedAmount().add(cashRefund);
        BigDecimal balance = sale.getGrandTotal()
                .subtract(sale.getPaidAmount())
                .subtract(returnedValue)
                .add(refundedAmount);
        if (balance.signum() < 0) {
            balance = SaleMath.ZERO;
        }
        sale.applyReturn(returnedValue, refundedAmount, balance, SaleWorkflow.afterReturn(true, allReturned));
        SaleReturn saleReturn = SaleReturn.open(
                documentNumberService.next(DocumentSeries.SALES_RETURN, request.returnDate().getYear()),
                sale,
                request.returnDate(),
                reason,
                request.refundMethod(),
                notes);
        int lineNumber = 1;
        for (PricedReturn line : priced) {
            saleReturn.addItem(SaleReturnItem.create(
                    saleReturn,
                    line.item(),
                    lineNumber++,
                    line.quantity(),
                    line.discount(),
                    line.tax(),
                    line.total(),
                    line.serials()));
        }
        saleReturn.totals(merchandise, itemDiscount, orderShare, tax, grand);
        returnRepository.saveAndFlush(saleReturn);
        for (SaleReturnItem item : saleReturn.getItems()) {
            receive(saleReturn, item);
        }
        financeService.postReturn(saleReturn, cashRefund);
        auditRecorder.record(
                "SALE_RETURNED",
                "return",
                AuditOutcome.SUCCESS,
                "sale",
                String.valueOf(sale.getId()),
                saleReturn.getReturnNumber());
        return detail(saleReturn);
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
            return SaleMath.ZERO;
        }
        BigDecimal share = grandTotal.multiply(merchandise).divide(goods, 4, RoundingMode.HALF_UP);
        return share.compareTo(remainingDocument) > 0 ? remainingDocument : share;
    }

    private PricedReturn price(SaleItem item, ReturnLineRequest line, int index) {
        String field = "items[" + index + "]";
        BigDecimal quantity = MasterText.money(line.quantity(), field + ".quantity", "Quantity", true);
        if (!item.getProduct().getUnit().isDecimalAllowed()) {
            whole(quantity, field + ".quantity", "Quantity");
        }
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("QUANTITY", field + ".quantity", "Return quantity must be greater than zero");
        }
        if (quantity.compareTo(item.remainingQuantity()) > 0) {
            throw new BusinessRuleException(
                    "RETURN_QUANTITY",
                    field + ".quantity",
                    "Return quantity cannot be greater than the quantity still available on this invoice");
        }
        List<SaleReturnItem> previous = returnItemRepository.findBySaleItem_Id(item.getId());
        BigDecimal previousDiscount = sum(previous, SaleReturnItem::getDiscountAmount);
        BigDecimal previousTax = sum(previous, SaleReturnItem::getTaxAmount);
        BigDecimal previousTotal = sum(previous, SaleReturnItem::getLineTotal);
        boolean finishes = item.getReturnedQuantity().add(quantity).compareTo(item.getQuantity()) == 0;
        BigDecimal discount = finishes
                ? SaleMath.clamp(item.getDiscountAmount().subtract(previousDiscount))
                : SaleMath.ratio(item.getDiscountAmount(), quantity, item.getQuantity());
        BigDecimal tax = finishes
                ? SaleMath.clamp(item.getTaxAmount().subtract(previousTax))
                : SaleMath.ratio(item.getTaxAmount(), quantity, item.getQuantity());
        BigDecimal total = finishes
                ? SaleMath.clamp(item.getLineTotal().subtract(previousTotal))
                : SaleMath.ratio(item.getLineTotal(), quantity, item.getQuantity());
        List<String> serials = serials(item, line.serials(), quantity, field);
        for (String serial : serials) {
            item.getSerials().stream()
                    .filter(row -> serial.equals(row.getSerialNumber()))
                    .findFirst()
                    .ifPresent(SaleItemSerial::markReturned);
        }
        return new PricedReturn(item, quantity, discount, tax, total, serials);
    }

    private List<String> serials(SaleItem item, List<String> raw, BigDecimal quantity, String field) {
        List<String> serials = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                String serial = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
                if (!SERIAL.matcher(serial).matches()) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Enter a valid serial number");
                }
                if (serials.contains(serial)) {
                    throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " is repeated");
                }
                serials.add(serial);
            }
        }
        if (!item.getProduct().isSerialTracking()) {
            if (!serials.isEmpty()) {
                throw new BusinessRuleException("SERIAL", field + ".serials", item.getProduct().getName() + " does not use serial numbers");
            }
            return List.of();
        }
        int expected;
        try {
            expected = quantity.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("SERIAL", field + ".serials", "Serial products must use whole quantities");
        }
        if (serials.size() != expected) {
            throw new BusinessRuleException("SERIAL", field + ".serials", "Enter one serial number for each returned quantity");
        }
        for (String serial : serials) {
            SaleItemSerial row = item.getSerials().stream()
                    .filter(candidate -> serial.equals(candidate.getSerialNumber()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "SERIAL",
                            field + ".serials",
                            "Serial " + serial + " was not sold on this line"));
            if (row.isReturned()) {
                throw new BusinessRuleException("SERIAL", field + ".serials", "Serial " + serial + " has already been returned");
            }
        }
        return List.copyOf(serials);
    }

    private void receive(SaleReturn saleReturn, SaleReturnItem item) {
        SaleItem source = item.getSaleItem();
        if (source.getProduct().getStockType() == StockType.NONE) {
            return;
        }
        BigDecimal stockValue = item.getQuantity().multiply(source.getUnitCost()).setScale(4, RoundingMode.HALF_UP);
        inventoryPostingService.receive(new Movement(
                source.getProduct(),
                source.getBatchCode(),
                source.getExpiryDate(),
                item.getQuantity(),
                source.getUnitCost(),
                stockValue,
                StockTxnType.SALES_RETURN,
                SaleFinanceService.SALES_RETURN,
                String.valueOf(saleReturn.getId()),
                saleReturn.getReturnDate(),
                "Sales return " + saleReturn.getReturnNumber(),
                item.getSerials().stream().map(SaleReturnSerial::getSerialNumber).toList(),
                null,
                saleReturn.getReturnNumber(),
                true,
                null));
    }

    private void whole(BigDecimal value, String field, String label) {
        if (value.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("WHOLE_QUANTITY", field, label + " must be a whole number for this unit");
        }
    }

    private BigDecimal sum(List<SaleReturnItem> rows, Function<SaleReturnItem, BigDecimal> reader) {
        return rows.stream().map(reader).reduce(SaleMath.ZERO, BigDecimal::add);
    }

    private SaleReturn load(Long id) {
        return returnRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sales return was not found"));
    }

    private ReturnDetail detail(SaleReturn saleReturn) {
        var stock = stockLedgerRepository.findByReferenceTypeAndReferenceIdOrderByIdAsc(
                SaleFinanceService.SALES_RETURN, String.valueOf(saleReturn.getId()));
        return SaleMapper.toReturnDetail(saleReturn, customerBalance(saleReturn.getCustomer().getId()), stock);
    }

    private BigDecimal customerBalance(Long customerId) {
        BigDecimal debit = ledgerRepository.sumDebit(customerId);
        BigDecimal credit = ledgerRepository.sumCredit(customerId);
        BigDecimal safeDebit = debit == null ? SaleMath.ZERO : debit;
        BigDecimal safeCredit = credit == null ? SaleMath.ZERO : credit;
        return safeDebit.subtract(safeCredit);
    }

    private record PricedReturn(
            SaleItem item,
            BigDecimal quantity,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            List<String> serials) {
    }
}
