package com.aveline.bizflow.audit.dto;

import java.time.Instant;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditSummary(
            long id,
            String user,
            String action,
            String module,
            String entity,
            String entityId,
            String outcome,
            String detail,
            String ipAddress,
            Instant timestamp) {
    }

    public record AuditDetail(
            long id,
            String user,
            String action,
            String module,
            String entity,
            String entityId,
            String eventType,
            String outcome,
            String detail,
            String oldValue,
            String newValue,
            String ipAddress,
            Instant timestamp) {
    }
}
