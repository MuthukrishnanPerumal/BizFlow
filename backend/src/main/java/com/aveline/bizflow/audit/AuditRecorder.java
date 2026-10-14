package com.aveline.bizflow.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;

@Service
public class AuditRecorder {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditRecorder(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(String eventType, String action, AuditOutcome outcome, String detail) {
        record(eventType, action, outcome, null, null, detail);
    }

    @Transactional
    public void recordAs(
            String actor,
            String eventType,
            String action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String detail) {
        save(actor, eventType, action, outcome, resourceType, resourceId, detail);
    }

    @Transactional
    public void record(
            String eventType,
            String action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String detail) {
        if (eventType == null || eventType.isBlank() || eventType.length() > 64) {
            throw new IllegalArgumentException("eventType must be 1 to 64 characters");
        }
        if (action == null || action.isBlank() || action.length() > 128) {
            throw new IllegalArgumentException("action must be 1 to 128 characters");
        }
        save(currentActor(), eventType, action, outcome, resourceType, resourceId, detail, null, null);
    }

    @Transactional
    public void recordChange(
            String eventType,
            String action,
            String resourceType,
            String resourceId,
            String detail,
            String oldValue,
            String newValue) {
        save(currentActor(), eventType, action, AuditOutcome.SUCCESS, resourceType, resourceId, detail, oldValue, newValue);
    }

    private void save(
            String actor,
            String eventType,
            String action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String detail) {
        save(actor, eventType, action, outcome, resourceType, resourceId, detail, null, null);
    }

    private void save(
            String actor,
            String eventType,
            String action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String detail,
            String oldValue,
            String newValue) {
        if (eventType == null || eventType.isBlank() || eventType.length() > 64) {
            throw new IllegalArgumentException("eventType must be 1 to 64 characters");
        }
        if (action == null || action.isBlank() || action.length() > 128) {
            throw new IllegalArgumentException("action must be 1 to 128 characters");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        String safeActor = limit(actor, 128);
        AuditEvent event = AuditEvent.record(
                eventType,
                safeActor == null ? "unknown" : safeActor,
                action,
                outcome,
                limit(resourceType, 128),
                limit(resourceId, 64),
                AuditText.clean(detail, 1000),
                AuditText.clean(oldValue, 4000),
                AuditText.clean(newValue, 4000),
                clientIp(),
                clock.instant());
        auditEventRepository.save(event);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return "system";
        }
        return limit(name, 128);
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return limit(request.getRemoteAddr(), 64);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
