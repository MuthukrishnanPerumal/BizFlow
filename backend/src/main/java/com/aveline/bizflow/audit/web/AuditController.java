package com.aveline.bizflow.audit.web;

import com.aveline.bizflow.audit.AuditQueryService;
import com.aveline.bizflow.audit.dto.AuditDtos.AuditDetail;
import com.aveline.bizflow.audit.dto.AuditDtos.AuditSummary;
import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.export.ExportFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public ApiResponse<PageResponse<AuditSummary>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(auditQueryService.search(from, to, user, module, action, entity, search, page, size, sort));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('AUDIT_EXPORT')")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String search) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        byte[] body = auditQueryService.export(exportFormat, from, to, user, module, action, entity, search);
        return ResponseEntity.ok()
                .contentType(exportFormat.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("audit-log." + exportFormat.extension())
                        .build()
                        .toString())
                .body(body);
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public ApiResponse<AuditDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(auditQueryService.get(id));
    }
}
