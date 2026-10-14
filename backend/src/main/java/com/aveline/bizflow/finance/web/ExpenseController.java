package com.aveline.bizflow.finance.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.finance.ExpenseCategory;
import com.aveline.bizflow.finance.ExpenseService;
import com.aveline.bizflow.finance.ExpenseStatus;
import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseDetail;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.ExpenseUpdateRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.VersionRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_VIEW')")
    public ApiResponse<ExpenseReport> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(expenseService.list(
                search, status, category, paymentMethod, dateFrom, dateTo, minAmount, maxAmount, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_VIEW')")
    public ApiResponse<ExpenseDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(expenseService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ResponseEntity<ApiResponse<ExpenseDetail>> create(@Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Expense recorded", expenseService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    public ApiResponse<ExpenseDetail> update(@PathVariable Long id, @Valid @RequestBody ExpenseUpdateRequest request) {
        return ApiResponse.ok("Expense updated", expenseService.update(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EXPENSE_CANCEL')")
    public ApiResponse<ExpenseDetail> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Expense cancelled", expenseService.cancel(id, request.version()));
    }
}
