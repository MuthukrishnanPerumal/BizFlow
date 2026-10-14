package com.aveline.bizflow.notification;

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
@Table(name = "app_notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "module_code", length = 64)
    private String moduleCode;

    @Column(name = "entity_type", length = 128)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "read_at", columnDefinition = "datetime(6)")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    public static AppNotification open(
            NotificationKind kind,
            NotificationSeverity severity,
            String title,
            String message,
            String moduleCode,
            String entityType,
            String entityId,
            Instant createdAt) {
        AppNotification notification = new AppNotification();
        notification.kind = kind;
        notification.severity = severity;
        notification.title = title;
        notification.message = message;
        notification.moduleCode = moduleCode;
        notification.entityType = entityType;
        notification.entityId = entityId;
        notification.createdAt = createdAt;
        return notification;
    }

    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }
}
