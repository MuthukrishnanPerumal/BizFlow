package com.aveline.bizflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long>, JpaSpecificationExecutor<AppNotification> {

    boolean existsByKindAndEntityTypeAndEntityIdAndReadAtIsNull(NotificationKind kind, String entityType, String entityId);

    long countByReadAtIsNull();

    @Modifying
    @Query("update AppNotification n set n.readAt = :when where n.readAt is null")
    int markAllRead(@Param("when") Instant when);
}
