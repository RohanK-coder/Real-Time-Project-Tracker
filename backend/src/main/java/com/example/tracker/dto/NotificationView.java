package com.example.tracker.dto;

import com.example.tracker.model.Notification;
import com.example.tracker.model.NotificationType;

import java.time.Instant;

public record NotificationView(Long id, Long boardId, Long taskId, NotificationType type, String message,
                               String actor, boolean read, Instant createdAt) {
    public static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getBoardId(), n.getTaskId(), n.getType(), n.getMessage(),
                n.getActor(), n.isRead(), n.getCreatedAt());
    }
}
