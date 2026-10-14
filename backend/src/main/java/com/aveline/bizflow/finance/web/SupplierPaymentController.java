package com.aveline.bizflow.finance.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.finance.PartyPaymentService;
import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.finance.dto.FinanceDtos.OpenInvoice;
import com.aveline.bizflow.finance.dto.FinanceDtos.PaymentDetail;
import com.aveline.bizflow.finance.dto.FinanceDtos.PaymentReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.SupplierPaymentRequest;
import com.aveline.bizflow.finance.dto.FinanceDtos.SupplierPaymentUpdateRequest;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/supplier-payments")
public class SupplierPaymentController {

    private final PartyPaymentService partyPaymentService;

    public SupplierPaymentController(PartyPaymentService partyPaymentService) {
        this.partyPaymentService = partyPaymentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<PaymentReport> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "STANDALONE") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(partyPaymentService.supplierPayments(
                search, status, paymentMethod, supplierId, dateFrom, dateTo, minAmount, maxAmount, scope, page, size, sort));
    }

    @GetMapping("/open-invoices")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<List<OpenInvoice>> openInvoices(@RequestParam Long supplierId) {
        return ApiResponse.ok(partyPaymentService.openPurchases(supplierId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<PaymentDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(partyPaymentService.supplierPayment(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<PaymentDetail>> create(@Valid @RequestBody SupplierPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment recorded", partyPaymentService.createSupplierPayment(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_UPDATE')")
    public ApiResponse<PaymentDetail> update(@PathVariable Long id, @Valid @RequestBody SupplierPaymentUpdateRequest request) {
        return ApiResponse.ok("Payment updated", partyPaymentService.updateSupplierPayment(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYMENT_CANCEL')")
    public ApiResponse<PaymentDetail> cancel(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok("Payment cancelled", partyPaymentService.cancelSupplierPayment(id, request.version()));
    }
}
