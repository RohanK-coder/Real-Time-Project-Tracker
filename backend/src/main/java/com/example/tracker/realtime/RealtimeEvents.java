package com.example.tracker.realtime;

import com.example.tracker.dto.BoardEvent;
import com.example.tracker.dto.NotificationView;

/**
 * In-process application events raised inside write transactions. They are only forwarded to
 * WebSocket clients after the transaction commits, so clients never see a change that rolled back.
 */
public final class RealtimeEvents {
    private RealtimeEvents() {}

    public record BoardChanged(BoardEvent event) {}

    public record NotificationCreated(String recipient, NotificationView notification) {}

    /** User lost access to a board: their live sockets are closed and must re-authorize on reconnect. */
    public record AccessRevoked(String username, Long boardId) {}
}
