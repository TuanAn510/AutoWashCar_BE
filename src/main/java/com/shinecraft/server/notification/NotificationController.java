package com.shinecraft.server.notification;

import com.shinecraft.server.common.ApiListResponse;
import com.shinecraft.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    ApiListResponse<NotificationDtos.NotificationResponse> mine() {
        return ApiListResponse.ok("Notifications retrieved successfully", notificationService.listMine());
    }

    @GetMapping("/unread-count")
    ApiResponse<NotificationDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok("Unread notification count retrieved successfully", notificationService.unreadCount());
    }

    @PatchMapping("/{id}/read")
    ApiResponse<NotificationDtos.NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok("Notification marked as read", notificationService.markRead(id));
    }

    @PatchMapping("/read-all")
    ApiResponse<NotificationDtos.UnreadCountResponse> markAllRead() {
        return ApiResponse.ok("All notifications marked as read", notificationService.markAllRead());
    }
}
