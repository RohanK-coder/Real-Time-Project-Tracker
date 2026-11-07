package com.example.tracker.dto;

import com.example.tracker.model.Task;
import com.example.tracker.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TaskDtos {
    private TaskDtos() {}

    public record CreateTaskRequest(@NotBlank @Size(max = 200) String title,
                                    @Size(max = 4000) String description,
                                    @NotNull TaskStatus status,
                                    String assignee) {}

    /** Full replacement of the editable fields. {@code version} is the version the client edited. */
    public record UpdateTaskRequest(@NotBlank @Size(max = 200) String title,
                                    @Size(max = 4000) String description,
                                    @NotNull TaskStatus status,
                                    String assignee,
                                    @NotNull Long version) {}

    public record MoveTaskRequest(@NotNull TaskStatus status, @NotNull Long version) {}

    public record TaskView(Long id, Long boardId, String title, String description, TaskStatus status,
                           String assignee, String createdBy, String updatedBy, Instant createdAt,
                           Instant updatedAt, Long version, long lastEventSeq, boolean deleted,
                           String githubIssueUrl) {
        public static TaskView of(Task t) {
            return new TaskView(t.getId(), t.getBoardId(), t.getTitle(), t.getDescription(), t.getStatus(),
                    t.getAssignee(), t.getCreatedBy(), t.getUpdatedBy(), t.getCreatedAt(), t.getUpdatedAt(),
                    t.getVersion(), t.getLastEventSeq(), t.isDeleted(), t.getGithubIssueUrl());
        }
    }

    /**
     * Catch-up payload for a reconnecting client: every task (including deletion tombstones) changed
     * after {@code sinceSeq}, plus the board's current sequence so the client can resume from it.
     */
    public record ChangesResponse(long sinceSeq, long currentSeq, List<TaskView> tasks) {}
}
