package com.aveline.bizflow.finance;

import com.aveline.bizflow.finance.JournalService.DraftLine;
import com.aveline.bizflow.master.LedgerEntryType;
import com.aveline.bizflow.master.party.CustomerLedgerEntry;
import com.aveline.bizflow.master.party.CustomerLedgerRepository;
import com.aveline.bizflow.master.party.SupplierLedgerEntry;
import com.aveline.bizflow.master.party.SupplierLedgerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerPostingService {

    private final CustomerLedgerRepository customerLedgerRepository;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final JournalService journalService;

    public LedgerPostingService(
            CustomerLedgerRepository customerLedgerRepository,
            SupplierLedgerRepository supplierLedgerRepository,
            JournalService journalService) {
        this.customerLedgerRepository = customerLedgerRepository;
        this.supplierLedgerRepository = supplierLedgerRepository;
        this.journalService = journalService;
    }

    public void postCustomerReceipt(Payment payment) {
        customerLedgerRepository.save(CustomerLedgerEntry.post(
                payment.getCustomer(),
                LedgerEntryType.PAYMENT,
                FinancePolicy.CUSTOMER_PAYMENT,
                String.valueOf(payment.getId()),
                payment.getPaymentDate(),
                FinanceMath.ZERO,
                payment.getAmount(),
                "Receipt " + payment.getPaymentNumber()));
        journalService.post(
                payment.getPaymentDate(),
                FinancePolicy.CUSTOMER_PAYMENT,
                String.valueOf(payment.getId()),
                "Receipt " + payment.getPaymentNumber(),
                customerLines(payment));
        customerLedgerRepository.flush();
    }

    public void reverseCustomerReceipt(Payment payment) {
        retireCustomer(payment.getId());
        journalService.reverse(FinancePolicy.CUSTOMER_PAYMENT, String.valueOf(payment.getId()));
    }

    public void postSupplierPayment(Payment payment) {
        supplierLedgerRepository.save(SupplierLedgerEntry.post(
                payment.getSupplier(),
                LedgerEntryType.PAYMENT,
                FinancePolicy.SUPPLIER_PAYMENT,
                String.valueOf(payment.getId()),
                payment.getPaymentDate(),
                payment.getAmount(),
                FinanceMath.ZERO,
                "Payment " + payment.getPaymentNumber()));
        journalService.post(
                payment.getPaymentDate(),
                FinancePolicy.SUPPLIER_PAYMENT,
                String.valueOf(payment.getId()),
                "Payment " + payment.getPaymentNumber(),
                supplierLines(payment));
        supplierLedgerRepository.flush();
    }

    public void reverseSupplierPayment(Payment payment) {
        for (SupplierLedgerEntry entry : supplierLedgerRepository.findByReferenceTypeAndReferenceId(
                FinancePolicy.SUPPLIER_PAYMENT, String.valueOf(payment.getId()))) {
            entry.retire();
        }
        supplierLedgerRepository.flush();
        journalService.reverse(FinancePolicy.SUPPLIER_PAYMENT, String.valueOf(payment.getId()));
    }

    public void postExpense(Expense expense) {
        journalService.post(
                expense.getExpenseDate(),
                FinancePolicy.EXPENSE,
                String.valueOf(expense.getId()),
                expense.getExpenseNumber() + " " + expense.getDescription(),
                List.of(
                        DraftLine.debit(expense.getCategory().account(), expense.getAmount(), null, null),
                        DraftLine.credit(GlAccount.funds(expense.getPaymentMethod()), expense.getAmount(), null, null)));
    }

    public void reverseExpense(Expense expense) {
        journalService.reverse(FinancePolicy.EXPENSE, String.valueOf(expense.getId()));
    }

    public BigDecimal customerOutstanding(Long customerId) {
        return FinanceMath.zeroIfNull(customerLedgerRepository.sumDebit(customerId))
                .subtract(FinanceMath.zeroIfNull(customerLedgerRepository.sumCredit(customerId)));
    }

    public BigDecimal supplierOutstanding(Long supplierId) {
        return FinanceMath.zeroIfNull(supplierLedgerRepository.sumCredit(supplierId))
                .subtract(FinanceMath.zeroIfNull(supplierLedgerRepository.sumDebit(supplierId)));
    }

    private void retireCustomer(Long paymentId) {
        for (CustomerLedgerEntry entry : customerLedgerRepository.findByReferenceTypeAndReferenceId(
                FinancePolicy.CUSTOMER_PAYMENT, String.valueOf(paymentId))) {
            entry.retire();
        }
        customerLedgerRepository.flush();
    }

    private List<DraftLine> customerLines(Payment payment) {
        List<DraftLine> lines = new ArrayList<>();
        lines.add(DraftLine.debit(
                GlAccount.funds(payment.getPaymentMethod()),
                payment.getAmount(),
                null,
                null));
        addCredit(lines, GlAccount.ACCOUNTS_RECEIVABLE, payment.getAllocatedAmount(), "CUSTOMER", payment.getCustomer().getId());
        addCredit(lines, GlAccount.CUSTOMER_ADVANCES, payment.getUnallocatedAmount(), "CUSTOMER", payment.getCustomer().getId());
        return lines;
    }

    private List<DraftLine> supplierLines(Payment payment) {
        List<DraftLine> lines = new ArrayList<>();
        addDebit(lines, GlAccount.ACCOUNTS_PAYABLE, payment.getAllocatedAmount(), "SUPPLIER", payment.getSupplier().getId());
        addDebit(lines, GlAccount.SUPPLIER_ADVANCES, payment.getUnallocatedAmount(), "SUPPLIER", payment.getSupplier().getId());
        lines.add(DraftLine.credit(
                GlAccount.funds(payment.getPaymentMethod()),
                payment.getAmount(),
                null,
                null));
        return lines;
    }

    private void addDebit(List<DraftLine> lines, GlAccount account, BigDecimal amount, String partyType, Long partyId) {
        if (amount != null && amount.signum() > 0) {
            lines.add(DraftLine.debit(account, amount, partyType, partyId));
        }
    }

    private void addCredit(List<DraftLine> lines, GlAccount account, BigDecimal amount, String partyType, Long partyId) {
        if (amount != null && amount.signum() > 0) {
            lines.add(DraftLine.credit(account, amount, partyType, partyId));
        }
    }
}
