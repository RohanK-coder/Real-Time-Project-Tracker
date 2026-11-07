package com.example.tracker.service;

import com.example.tracker.dto.NotificationView;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.exception.NotFoundException;
import com.example.tracker.model.Notification;
import com.example.tracker.model.NotificationType;
import com.example.tracker.realtime.RealtimeEvents;
import com.example.tracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher events;

    /** Persists the notification and queues a push to the recipient's sockets after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(String recipient, String actor, NotificationType type, String message, Long boardId, Long taskId) {
        if (recipient == null || recipient.equals(actor)) {
            return; // never notify people about their own actions
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .recipient(recipient).actor(actor).type(type)
                .message(message.length() > 500 ? message.substring(0, 497) + "..." : message)
                .boardId(boardId).taskId(taskId).read(false).createdAt(Instant.now())
                .build());
        events.publishEvent(new RealtimeEvents.NotificationCreated(recipient, NotificationView.of(saved)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(String username, boolean unreadOnly, Integer page, Integer size) {
        var pageable = Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "id"));
        var result = unreadOnly
                ? notificationRepository.findByRecipientAndReadFalse(username, pageable)
                : notificationRepository.findByRecipient(username, pageable);
        return PageResponse.of(result, NotificationView::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return notificationRepository.countByRecipientAndReadFalse(username);
    }

    @Transactional
    public NotificationView markRead(Long id, String username) {
        // Scoped by recipient: another user's notification id is indistinguishable from a missing one.
        Notification n = notificationRepository.findByIdAndRecipient(id, username)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.setRead(true);
        return NotificationView.of(n);
    }

    @Transactional
    public int markAllRead(String username) {
        return notificationRepository.markAllRead(username);
    }
}
