package com.example.tracker.controller;

import com.example.tracker.dto.NotificationView;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size, Authentication auth) {
        return notificationService.list(auth.getName(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notificationService.unreadCount(auth.getName()));
    }

    @PostMapping("/{id}/read")
    public NotificationView markRead(@PathVariable Long id, Authentication auth) {
        return notificationService.markRead(id, auth.getName());
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication auth) {
        return Map.of("updated", notificationService.markAllRead(auth.getName()));
    }
}
