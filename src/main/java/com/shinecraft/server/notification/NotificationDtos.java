package com.shinecraft.server.notification;

import java.time.LocalDateTime;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(
            Long id,
            String type,
            String title,
            String message,
            String targetType,
            Long targetId,
            boolean read,
            LocalDateTime createdAt) {
        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getTargetType(),
                    notification.getTargetId(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }

    public record UnreadCountResponse(long count) {}
}
