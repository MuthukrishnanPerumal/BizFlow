package com.aveline.bizflow.notification.dto;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            long id,
            String kind,
            String severity,
            String title,
            String message,
            String module,
            String entity,
            String entityId,
            Instant readAt,
            Instant createdAt) {
    }

    public record NotificationSummary(long unread) {
    }
}
