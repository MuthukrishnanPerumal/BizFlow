package com.aveline.bizflow.notification.web;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.notification.AppNotificationService;
import com.aveline.bizflow.notification.dto.NotificationDtos.NotificationResponse;
import com.aveline.bizflow.notification.dto.NotificationDtos.NotificationSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final AppNotificationService notificationService;

    public NotificationController(AppNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<PageResponse<NotificationResponse>> search(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.search(kind, unreadOnly, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<NotificationSummary> summary() {
        return ApiResponse.ok(notificationService.summary());
    }

    @PostMapping("/{id:\\d+}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<NotificationSummary> markAllRead() {
        return ApiResponse.ok(notificationService.markAllRead());
    }
}
