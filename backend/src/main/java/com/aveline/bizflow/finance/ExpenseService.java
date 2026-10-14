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
import com.aveline.bizflow.finance.dto.FinanceDtos.AuditView;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseDetail;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseSummary;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseUpdateRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.JournalLineView;
import com.aveline.bizflow.master.MasterText;
import com.aveline.bizflow.master.MasterVersions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Service
public class ExpenseService {

    private static final Set<String> SORTS = Set.of("expenseDate", "expenseNumber", "amount", "status", "category");

    private final ExpenseRepository expenseRepository;
    private final DocumentNumberService documentNumberService;
    private final LedgerPostingService ledgerPostingService;
    private final JournalService journalService;
    private final AuditRecorder auditRecorder;
    private final AuditEventRepository auditEventRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            DocumentNumberService documentNumberService,
            LedgerPostingService ledgerPostingService,
            JournalService journalService,
            AuditRecorder auditRecorder,
            AuditEventRepository auditEventRepository) {
        this.expenseRepository = expenseRepository;
        this.documentNumberService = documentNumberService;
        this.ledgerPostingService = ledgerPostingService;
        this.journalService = journalService;
        this.auditRecorder = auditRecorder;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public ExpenseReport list(
            String search,
            ExpenseStatus status,
            ExpenseCategory category,
            PaymentMethod method,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        BigDecimal minimum = optionalMoney(minAmount, "minAmount", "Minimum amount");
        BigDecimal maximum = optionalMoney(maxAmount, "maxAmount", "Maximum amount");
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new BusinessRuleException("AMOUNT_RANGE", "minAmount", "Minimum amount cannot exceed the maximum");
        }
        String term = LikePatterns.contain(search);
        Pageable pageable = PageRequests.of(page, size, sort, SORTS, "expenseDate");
        Page<Expense> result = expenseRepository.search(term, status, category, method, fromDate, toDate, minimum, maximum, pageable);
        BigDecimal posted = expenseRepository.postedTotal(term, category, method, fromDate, toDate, minimum, maximum);
        return new ExpenseReport(
                result.getContent().stream().map(this::summary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                posted == null ? FinanceMath.ZERO : posted);
    }

    @Transactional(readOnly = true)
    public ExpenseDetail get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public ExpenseDetail create(ExpenseRequest request) {
        BigDecimal amount = FinanceMath.positive(request.amount(), "amount", "Amount");
        FinancePolicy.requireSettlementMethod(request.paymentMethod());
        Expense expense = expenseRepository.save(Expense.post(
                documentNumberService.next(DocumentSeries.EXPENSE, request.expenseDate().getYear()),
                request.expenseDate(),
                request.category(),
                description(request.description()),
                amount,
                request.paymentMethod(),
                reference(request.referenceNumber()),
                notes(request.notes())));
        ledgerPostingService.postExpense(expense);
        auditRecorder.record(
                "EXPENSE_CREATED",
                "create",
                AuditOutcome.SUCCESS,
                "expense",
                String.valueOf(expense.getId()),
                "Recorded expense " + expense.getExpenseNumber());
        return detail(expense);
    }

    @Transactional
    public ExpenseDetail update(Long id, ExpenseUpdateRequest request) {
        Expense expense = expenseRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense was not found"));
        MasterVersions.check(expense.getVersion(), request.version());
        if (expense.getStatus() != ExpenseStatus.POSTED) {
            throw new BusinessRuleException("STATUS", "status", "Only a posted expense can be changed");
        }
        BigDecimal amount = FinanceMath.positive(request.amount(), "amount", "Amount");
        FinancePolicy.requireSettlementMethod(request.paymentMethod());
        ledgerPostingService.reverseExpense(expense);
        expense.revise(
                request.expenseDate(),
                request.category(),
                description(request.description()),
                amount,
                request.paymentMethod(),
                reference(request.referenceNumber()),
                notes(request.notes()));
        ledgerPostingService.postExpense(expense);
        auditRecorder.record(
                "EXPENSE_UPDATED",
                "update",
                AuditOutcome.SUCCESS,
                "expense",
                String.valueOf(expense.getId()),
                "Updated expense " + expense.getExpenseNumber());
        return detail(expense);
    }

    @Transactional
    public ExpenseDetail cancel(Long id, Long version) {
        Expense expense = expenseRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense was not found"));
        MasterVersions.check(expense.getVersion(), version);
        if (expense.getStatus() != ExpenseStatus.POSTED) {
            throw new BusinessRuleException("STATUS", "status", "Only a posted expense can be cancelled");
        }
        ledgerPostingService.reverseExpense(expense);
        expense.cancel();
        auditRecorder.record(
                "EXPENSE_CANCELLED",
                "cancel",
                AuditOutcome.SUCCESS,
                "expense",
                String.valueOf(expense.getId()),
                "Cancelled expense " + expense.getExpenseNumber());
        return detail(expense);
    }

    private Expense load(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense was not found"));
    }

    private ExpenseDetail detail(Expense expense) {
        JournalEntry journal = journalService.latest(FinancePolicy.EXPENSE, String.valueOf(expense.getId()));
        return new ExpenseDetail(
                expense.getId(),
                expense.getExpenseNumber(),
                expense.getExpenseDate(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getPaymentMethod(),
                expense.getReferenceNumber(),
                expense.getNotes(),
                expense.getStatus(),
                journal == null ? null : journal.getStatus(),
                journal == null ? java.util.List.of() : journal.getLines().stream()
                        .map(line -> new JournalLineView(
                                line.getLineNumber(),
                                line.getAccountCode(),
                                GlAccount.fromCode(line.getAccountCode()).label(),
                                line.getDebitAmount(),
                                line.getCreditAmount()))
                        .toList(),
                auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("expense", String.valueOf(expense.getId()))
                        .stream()
                        .map(this::auditView)
                        .toList(),
                expense.getVersion());
    }

    private ExpenseSummary summary(Expense expense) {
        return new ExpenseSummary(
                expense.getId(),
                expense.getExpenseNumber(),
                expense.getExpenseDate(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getPaymentMethod(),
                expense.getStatus(),
                expense.getReferenceNumber());
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

    private static String description(String value) {
        return MasterText.required(value, 500, "description", "Description");
    }

    private static String reference(String value) {
        return MasterText.optional(value, 64, "referenceNumber", "Reference");
    }

    private static String notes(String value) {
        return MasterText.optional(value, 1000, "notes", "Notes");
    }

    private static BigDecimal optionalMoney(BigDecimal value, String field, String label) {
        return value == null ? null : MasterText.money(value, field, label, true);
    }
}
