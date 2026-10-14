package com.aveline.bizflow.notification;

import com.aveline.bizflow.audit.AuditText;
import com.aveline.bizflow.common.api.PageRequests;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.notification.dto.NotificationDtos.NotificationResponse;
import com.aveline.bizflow.notification.dto.NotificationDtos.NotificationSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AppNotificationService {

    private static final Set<String> FAILURES = Set.of("INSUFFICIENT_STOCK", "CONFLICT", "LOCK", "DATA");
    private static final Set<String> SORTS = Set.of("createdAt", "kind", "severity");

    private final AppNotificationRepository notificationRepository;
    private final Clock clock;

    public AppNotificationService(AppNotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public void raise(
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String message,
            String module,
            String entityType,
            String entityId) {
        String safeTitle = limit(AuditText.clean(title, 200), 200);
        String safeMessage = limit(AuditText.clean(message, 1000), 1000);
        if (safeTitle == null || safeMessage == null) {
            return;
        }
        String type = limit(entityType, 128);
        String id = limit(entityId, 64);
        if (type != null && id != null && notificationRepository.existsByKindAndEntityTypeAndEntityIdAndReadAtIsNull(kind, type, id)) {
            return;
        }
        notificationRepository.save(AppNotification.open(
                kind,
                severity,
                safeTitle,
                safeMessage,
                limit(module, 64),
                type,
                id,
                clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failedTransaction(String code, String message) {
        if (code == null || !FAILURES.contains(code) || skipCurrentRequest()) {
            return;
        }
        String safe = AuditText.clean(message, 1000);
        if (safe == null) {
            safe = "A transaction could not be completed";
        }
        raise(
                NotificationKind.FAILED_TRANSACTION,
                NotificationSeverity.CRITICAL,
                "Transaction failed",
                safe,
                "TRANSACTION",
                "failure",
                Integer.toHexString(safe.hashCode()));
    }

    @Transactional
    public void system(String title, String message, String entityId) {
        raise(NotificationKind.SYSTEM, NotificationSeverity.INFO, title, message, "SYSTEM", "system", entityId);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(String kind, boolean unreadOnly, int page, int size) {
        NotificationKind parsed = parseKind(kind);
        Specification<AppNotification> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parsed != null) {
                predicates.add(builder.equal(root.get("kind"), parsed));
            }
            if (unreadOnly) {
                predicates.add(builder.isNull(root.get("readAt")));
            }
            if (predicates.isEmpty()) {
                return builder.conjunction();
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<AppNotification> result = notificationRepository.findAll(
                specification,
                PageRequests.of(page, size, "createdAt,desc", SORTS, "createdAt"));
        return new PageResponse<>(
                result.getContent().stream().map(this::view).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificationSummary summary() {
        return new NotificationSummary(notificationRepository.countByReadAtIsNull());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        AppNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification was not found"));
        notification.markRead(clock.instant());
        return view(notification);
    }

    @Transactional
    public NotificationSummary markAllRead() {
        notificationRepository.markAllRead(clock.instant());
        return new NotificationSummary(0);
    }

    private NotificationResponse view(AppNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getKind().name(),
                notification.getSeverity().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getModuleCode(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private NotificationKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        try {
            return NotificationKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean skipCurrentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/api/v1/notifications");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
