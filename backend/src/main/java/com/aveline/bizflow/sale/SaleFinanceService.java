package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.document.DocumentNumberService;
import com.aveline.bizflow.document.DocumentSeries;
import com.aveline.bizflow.finance.Payment;
import com.aveline.bizflow.finance.PaymentRepository;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.master.LedgerEntryType;
import com.aveline.bizflow.master.party.Customer;
import com.aveline.bizflow.master.party.CustomerLedgerEntry;
import com.aveline.bizflow.master.party.CustomerLedgerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SaleFinanceService {

    static final String SALE = "SALE";
    static final String SALES_RETURN = "SALES_RETURN";

    private final CustomerLedgerRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentNumberService documentNumberService;

    public SaleFinanceService(
            CustomerLedgerRepository ledgerRepository,
            PaymentRepository paymentRepository,
            DocumentNumberService documentNumberService) {
        this.ledgerRepository = ledgerRepository;
        this.paymentRepository = paymentRepository;
        this.documentNumberService = documentNumberService;
    }

    public void postSale(Sale sale) {
        Customer customer = sale.getCustomer();
        String referenceId = String.valueOf(sale.getId());
        if (sale.getGrandTotal().signum() > 0) {
            ledgerRepository.save(CustomerLedgerEntry.post(
                    customer,
                    LedgerEntryType.SALE,
                    SALE,
                    referenceId,
                    sale.getInvoiceDate(),
                    sale.getGrandTotal(),
                    SaleMath.ZERO,
                    "Sale " + sale.getSaleNumber()));
        }
        for (SaleTender tender : sale.getTenders()) {
            String paymentNumber = documentNumberService.next(DocumentSeries.PAYMENT, sale.getInvoiceDate().getYear());
            paymentRepository.save(Payment.customerReceipt(
                    paymentNumber,
                    customer,
                    tender.getPaymentMethod().toPaymentMethod(),
                    sale.getInvoiceDate(),
                    tender.getAmount(),
                    SALE,
                    referenceId,
                    tender.getReferenceNumber(),
                    "Receipt " + paymentNumber + " for " + sale.getSaleNumber()));
        }
        if (sale.getPaidAmount().signum() > 0) {
            ledgerRepository.save(CustomerLedgerEntry.post(
                    customer,
                    LedgerEntryType.PAYMENT,
                    SALE,
                    referenceId,
                    sale.getInvoiceDate(),
                    SaleMath.ZERO,
                    sale.getPaidAmount(),
                    "Receipt for " + sale.getSaleNumber()));
        }
        ledgerRepository.flush();
        enforceCreditLimit(customer);
    }

    public void reverseSale(Sale sale) {
        String referenceId = String.valueOf(sale.getId());
        for (CustomerLedgerEntry entry : ledgerRepository.findByReferenceTypeAndReferenceId(SALE, referenceId)) {
            entry.retire();
        }
        for (Payment payment : paymentRepository.findBySourceTypeAndSourceIdOrderByIdAsc(SALE, referenceId)) {
            if (payment.getStatus() == PaymentStatus.POSTED) {
                payment.cancel();
            }
        }
        ledgerRepository.flush();
    }

    public void postReturn(SaleReturn saleReturn, BigDecimal cashRefund) {
        if (saleReturn.getGrandTotal().signum() == 0 && cashRefund.signum() == 0) {
            return;
        }
        Customer customer = saleReturn.getCustomer();
        String referenceId = String.valueOf(saleReturn.getId());
        if (saleReturn.getGrandTotal().signum() > 0) {
            ledgerRepository.save(CustomerLedgerEntry.post(
                    customer,
                    LedgerEntryType.SALE_RETURN,
                    SALES_RETURN,
                    referenceId,
                    saleReturn.getReturnDate(),
                    SaleMath.ZERO,
                    saleReturn.getGrandTotal(),
                    "Sales return " + saleReturn.getReturnNumber() + " against " + saleReturn.getSale().getSaleNumber()));
        }
        if (cashRefund.signum() > 0) {
            String paymentNumber = documentNumberService.next(DocumentSeries.PAYMENT, saleReturn.getReturnDate().getYear());
            paymentRepository.save(Payment.customerRefund(
                    paymentNumber,
                    customer,
                    saleReturn.getRefundMethod().toPaymentMethod(),
                    saleReturn.getReturnDate(),
                    cashRefund,
                    SALES_RETURN,
                    referenceId,
                    null,
                    "Refund " + paymentNumber + " for " + saleReturn.getReturnNumber()));
            ledgerRepository.save(CustomerLedgerEntry.post(
                    customer,
                    LedgerEntryType.PAYMENT,
                    SALES_RETURN,
                    referenceId,
                    saleReturn.getReturnDate(),
                    cashRefund,
                    SaleMath.ZERO,
                    "Refund " + paymentNumber + " for " + saleReturn.getReturnNumber()));
        }
        ledgerRepository.flush();
    }

    private void enforceCreditLimit(Customer customer) {
        if (customer.getCreditLimit() == null || customer.getCreditLimit().signum() <= 0) {
            return;
        }
        BigDecimal debit = amount(ledgerRepository.sumDebit(customer.getId()));
        BigDecimal credit = amount(ledgerRepository.sumCredit(customer.getId()));
        BigDecimal receivable = debit.subtract(credit);
        if (receivable.compareTo(customer.getCreditLimit()) > 0) {
            throw new BusinessRuleException(
                    "CREDIT_LIMIT",
                    "customerId",
                    "This sale would take the customer balance above the credit limit");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? SaleMath.ZERO : value;
    }
}
