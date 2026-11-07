package com.example.tracker.exception;

import com.example.tracker.dto.TaskDtos.TaskView;

/** Client edited a stale version. Carries the current server state so the client can merge or retry. */
public class TaskConflictException extends RuntimeException {
    private final TaskView current;

    public TaskConflictException(TaskView current) {
        super("Task was modified by " + (current == null ? "another user" : current.updatedBy())
                + " (current version " + (current == null ? "?" : current.version()) + ")");
        this.current = current;
    }

    public TaskView getCurrent() { return current; }
}
