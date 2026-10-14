package com.aveline.bizflow.system.service;

import com.aveline.bizflow.audit.AuditOutcome;
import com.aveline.bizflow.audit.AuditRecorder;
import com.aveline.bizflow.notification.AppNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlatformStartupListener implements ApplicationRunner {

    private final AuditRecorder auditRecorder;
    private final AppNotificationService notifications;

    public PlatformStartupListener(AuditRecorder auditRecorder, AppNotificationService notifications) {
        this.auditRecorder = auditRecorder;
        this.notifications = notifications;
    }

    @Override
    public void run(ApplicationArguments args) {
        auditRecorder.record("APPLICATION_STARTED", "Application started", AuditOutcome.SUCCESS, null);
        notifications.system("Application started", "Aveline BizFlow is ready.", "startup");
        log.info("Application startup recorded");
    }
}
