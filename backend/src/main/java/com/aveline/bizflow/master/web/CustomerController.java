package com.aveline.bizflow.master.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.master.RecordStatus;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerRequest;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerResponse;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerUpdateRequest;
import com.aveline.bizflow.master.dto.PartyDtos.LedgerEntryResponse;
import com.aveline.bizflow.master.party.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(customerService.list(search, status, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(customerService.get(id));
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<PageResponse<LedgerEntryResponse>> ledger(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(customerService.ledger(id, page, size, sort));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Customer created", customerService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return ApiResponse.ok("Customer updated", customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.ok("Customer deleted", null);
    }
}
