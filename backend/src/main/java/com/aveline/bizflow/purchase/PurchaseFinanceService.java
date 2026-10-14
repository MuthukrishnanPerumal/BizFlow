package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.finance.Payment;
import com.aveline.bizflow.finance.PaymentRepository;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.master.LedgerEntryType;
import com.aveline.bizflow.master.party.Supplier;
import com.aveline.bizflow.master.party.SupplierLedgerEntry;
import com.aveline.bizflow.master.party.SupplierLedgerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PurchaseFinanceService {

    static final String PURCHASE = "PURCHASE";
    static final String PURCHASE_RETURN = "PURCHASE_RETURN";

    private final SupplierLedgerRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;

    public PurchaseFinanceService(
            SupplierLedgerRepository ledgerRepository,
            PaymentRepository paymentRepository,
            DocumentNumberService documentNumberService) {
        this.ledgerRepository = ledgerRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
    }

    public void postPurchase(Purchase purchase) {
        Supplier supplier = purchase.getSupplier();
        String referenceId = String.valueOf(purchase.getId());
        if (purchase.getGrandTotal().signum() > 0) {
            ledgerRepository.save(SupplierLedgerEntry.post(
                    supplier,
                    LedgerEntryType.PURCHASE,
                    PURCHASE,
                    referenceId,
                    purchase.getPurchaseDate(),
                    PurchaseMath.ZERO,
                    purchase.getGrandTotal(),
                    "Purchase " + purchase.getPurchaseNumber() + " invoice " + purchase.getInvoiceNumber()));
        }
        if (purchase.getPaidAmount().signum() > 0) {
            String paymentNumber = documentNumberService.next(DocumentSeries.PAYMENT, purchase.getPurchaseDate().getYear());
            paymentRepository.save(Payment.supplierPayment(
                    paymentNumber,
                    supplier,
                    purchase.getPaymentType(),
                    purchase.getPurchaseDate(),
                    purchase.getPaidAmount(),
                    PURCHASE,
                    referenceId,
                    purchase.getReferenceNumber(),
                    "Payment for " + purchase.getPurchaseNumber()));
            ledgerRepository.save(SupplierLedgerEntry.post(
                    supplier,
                    LedgerEntryType.PAYMENT,
                    PURCHASE,
                    referenceId,
                    purchase.getPurchaseDate(),
                    purchase.getPaidAmount(),
                    PurchaseMath.ZERO,
                    "Payment " + paymentNumber + " for " + purchase.getPurchaseNumber()));
        }
        ledgerRepository.flush();
        enforceCreditLimit(supplier);
    }

    public void reversePurchase(Purchase purchase) {
        String referenceId = String.valueOf(purchase.getId());
        for (SupplierLedgerEntry entry : ledgerRepository.findByReferenceTypeAndReferenceId(PURCHASE, referenceId)) {
            entry.retire();
        }
        for (Payment payment : paymentRepository.findBySourceTypeAndSourceIdOrderByIdAsc(PURCHASE, referenceId)) {
            if (payment.getStatus() == PaymentStatus.POSTED) {
                payment.cancel();
            }
        }
        ledgerRepository.flush();
    }

    public void postReturn(PurchaseReturn purchaseReturn) {
        if (purchaseReturn.getGrandTotal().signum() == 0) {
            return;
        }
        ledgerRepository.saveAndFlush(SupplierLedgerEntry.post(
                purchaseReturn.getSupplier(),
                LedgerEntryType.PURCHASE_RETURN,
                PURCHASE_RETURN,
                String.valueOf(purchaseReturn.getId()),
                purchaseReturn.getReturnDate(),
                purchaseReturn.getGrandTotal(),
                PurchaseMath.ZERO,
                "Purchase return " + purchaseReturn.getReturnNumber()
                        + " against " + purchaseReturn.getPurchase().getPurchaseNumber()));
    }

    private void enforceCreditLimit(Supplier supplier) {
        if (supplier.getCreditLimit() == null || supplier.getCreditLimit().signum() <= 0) {
            return;
        }
        BigDecimal debit = amount(ledgerRepository.sumDebit(supplier.getId()));
        BigDecimal credit = amount(ledgerRepository.sumCredit(supplier.getId()));
        BigDecimal payable = credit.subtract(debit);
        if (payable.compareTo(supplier.getCreditLimit()) > 0) {
            throw new BusinessRuleException(
                    "CREDIT_LIMIT",
                    "supplierId",
                    "This purchase would take the supplier balance above the credit limit");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? PurchaseMath.ZERO : value;
    }
}
