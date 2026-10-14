package com.aveline.bizflow.reporting.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.export.ExportFormat;
import com.aveline.bizflow.reporting.DocumentScope;
import com.aveline.bizflow.reporting.ReportPreset;
import com.aveline.bizflow.reporting.ReportingQueryService;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportingQueryService reportingQueryService;

    public ReportController(ReportingQueryService reportingQueryService) {
        this.reportingQueryService = reportingQueryService;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<SalesReport> sales(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(reportingQueryService.sales(preset, from, to, search, scope, page, size, sort));
    }

    @GetMapping("/sales/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> salesExport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(required = false) String sort) {
        return file("sales-report", ExportFormat.parse(format), reportingQueryService.salesFile(ExportFormat.parse(format), preset, from, to, search, scope, sort));
    }

    @GetMapping(value = "/sales.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<String> salesCsv(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(required = false) String sort) {
        return csv("sales-report.csv", reportingQueryService.salesCsv(preset, from, to, search, scope, sort));
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<PurchaseReport> purchases(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(reportingQueryService.purchases(preset, from, to, search, scope, page, size, sort));
    }

    @GetMapping("/purchases/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> purchasesExport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(required = false) String sort) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        return file("purchase-report", exportFormat, reportingQueryService.purchasesFile(exportFormat, preset, from, to, search, scope, sort));
    }

    @GetMapping(value = "/purchases.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<String> purchasesCsv(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "POSTED") DocumentScope scope,
            @RequestParam(required = false) String sort) {
        return csv("purchase-report.csv", reportingQueryService.purchasesCsv(preset, from, to, search, scope, sort));
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<InventoryReport> inventory(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(reportingQueryService.inventory(preset, from, to, search, page, size, sort));
    }

    @GetMapping("/inventory/print")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<InventoryReport> inventoryPrint(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(reportingQueryService.inventoryPrint(preset, from, to, search, sort));
    }

    @GetMapping("/inventory/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> inventoryExport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        return file("stock-report", exportFormat, reportingQueryService.inventoryFile(exportFormat, preset, from, to, search, sort));
    }

    @GetMapping(value = "/inventory.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<String> inventoryCsv(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        return csv("inventory-report.csv", reportingQueryService.inventoryCsv(preset, from, to, search, sort));
    }

    @GetMapping("/profit")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<ProfitReport> profit(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(reportingQueryService.profit(preset, from, to, search, page, size, sort));
    }

    @GetMapping("/profit/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> profitExport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        return file("profit-report", exportFormat, reportingQueryService.profitFile(exportFormat, preset, from, to, search, sort));
    }

    @GetMapping(value = "/profit.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<String> profitCsv(
            @RequestParam(required = false) ReportPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        return csv("profit-report.csv", reportingQueryService.profitCsv(preset, from, to, search, sort));
    }

    private ResponseEntity<byte[]> file(String name, ExportFormat format, byte[] body) {
        return ResponseEntity.ok()
                .contentType(format.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name + "." + format.extension())
                        .build()
                        .toString())
                .body(body);
    }

    private ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
