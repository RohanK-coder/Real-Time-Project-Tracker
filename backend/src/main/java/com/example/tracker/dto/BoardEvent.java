package com.example.tracker.dto;

import java.time.Instant;

/**
 * Message pushed to {@code /topic/boards/{boardId}}. {@code seq} is contiguous per board, so a client
 * that sees seq N+2 after N knows it missed an event and must call the changes endpoint.
 */
public record BoardEvent(Long boardId, long seq, Type type, TaskDtos.TaskView task, Long taskId,
                         String actor, Instant at) {
    public enum Type { TASK_CREATED, TASK_UPDATED, TASK_MOVED, TASK_DELETED, MEMBERS_CHANGED }
}
