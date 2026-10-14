package com.aveline.bizflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "audit_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "module_code", length = 64)
    private String moduleCode;

    @Column(name = "resource_type", length = 128)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditOutcome outcome;

    @Column(length = 1000)
    private String detail;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    public static AuditEvent record(
            String eventType,
            String actor,
            String action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String detail,
            String oldValue,
            String newValue,
            String ipAddress,
            Instant createdAt) {
        AuditEvent event = new AuditEvent();
        event.eventType = eventType;
        event.actor = actor;
        event.action = action;
        event.moduleCode = AuditCatalog.module(eventType, resourceType);
        event.outcome = outcome;
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.detail = detail;
        event.oldValue = oldValue;
        event.newValue = newValue;
        event.ipAddress = ipAddress;
        event.createdAt = createdAt;
        return event;
    }
}
