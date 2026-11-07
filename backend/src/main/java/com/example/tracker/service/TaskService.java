package com.example.tracker.service;

import com.example.tracker.dto.BoardEvent;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.dto.TaskDtos.*;
import com.example.tracker.exception.NotFoundException;
import com.example.tracker.exception.TaskConflictException;
import com.example.tracker.model.*;
import com.example.tracker.realtime.RealtimeEvents;
import com.example.tracker.repository.TaskRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Concurrency model for every write:
 * <ol>
 *   <li>Lock the board row (SELECT ... FOR UPDATE). Writers to one board are serialised; writers
 *       to different boards run in parallel.</li>
 *   <li>Read the task and compare the client's {@code version} with the stored one. A stale
 *       version gets 409 Conflict with the current task in the body. Nothing is silently overwritten.</li>
 *   <li>Apply the change, take the next board sequence number, write the activity entry and any
 *       notifications in the same transaction.</li>
 *   <li>After commit, broadcast a {@link BoardEvent} with that sequence number.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final Map<String, Sort> SORTS = Map.of(
            "updated", Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")),
            "created", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            "title", Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));

    private final TaskRepository taskRepository;
    private final BoardAccessService boardAccess;
    private final BoardSequencer sequencer;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<TaskView> search(Long boardId, String username, String q, TaskStatus status,
                                         String assignee, String sort, Integer page, Integer size) {
        boardAccess.require(boardId, username, BoardRole.VIEWER);
        Sort order = SORTS.getOrDefault(sort == null ? "updated" : sort, SORTS.get("updated"));
        var pageable = Paging.of(page, size, MAX_PAGE_SIZE, order);
        return PageResponse.of(taskRepository.findAll(searchSpec(boardId, q, status, assignee), pageable), TaskView::of);
    }

    @Transactional(readOnly = true)
    public TaskView get(Long boardId, Long taskId, String username) {
        boardAccess.require(boardId, username, BoardRole.VIEWER);
        return TaskView.of(find(boardId, taskId));
    }

    /**
     * Catch-up for reconnecting clients. REPEATABLE READ gives one snapshot for both queries, so
     * {@code currentSeq} and the task list are consistent with each other.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChangesResponse changesSince(Long boardId, long sinceSeq, String username) {
        boardAccess.require(boardId, username, BoardRole.VIEWER);
        long current = sequencer.currentSeq(boardId);
        var tasks = taskRepository.findByBoardIdAndLastEventSeqGreaterThanOrderByLastEventSeqAsc(boardId, sinceSeq)
                .stream().map(TaskView::of).toList();
        return new ChangesResponse(sinceSeq, current, tasks);
    }

    @Transactional
    public TaskView create(Long boardId, CreateTaskRequest request, String actor) {
        boardAccess.require(boardId, actor, BoardRole.EDITOR);
        Board board = sequencer.lock(boardId);
        String assignee = normalizeAssignee(boardId, request.assignee());
        long seq = sequencer.next(board);
        Instant now = Instant.now();
        Task task = taskRepository.saveAndFlush(Task.builder()
                .boardId(boardId).title(request.title().trim()).description(request.description())
                .status(request.status()).assignee(assignee)
                .createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now)
                .lastEventSeq(seq).build());
        afterCreate(board, task, actor, seq);
        return TaskView.of(task);
    }

    /** Used by the GitHub webhook (already authenticated by HMAC, so no member check). */
    @Transactional
    public TaskView createFromGithubIssue(Long boardId, String title, String body, String issueUrl, String openedBy) {
        Board board = sequencer.lock(boardId);
        long seq = sequencer.next(board);
        Instant now = Instant.now();
        String actor = "github:" + openedBy;
        Task task = taskRepository.saveAndFlush(Task.builder()
                .boardId(boardId).title(truncate(title, 200)).description(truncate(body, 4000))
                .status(TaskStatus.TODO).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now)
                .githubIssueUrl(issueUrl).lastEventSeq(seq).build());
        afterCreate(board, task, actor, seq);
        return TaskView.of(task);
    }

    @Transactional
    public TaskView update(Long boardId, Long taskId, UpdateTaskRequest request, String actor) {
        boardAccess.require(boardId, actor, BoardRole.EDITOR);
        Board board = sequencer.lock(boardId);
        Task task = find(boardId, taskId);
        checkVersion(task, request.version());

        String assignee = normalizeAssignee(boardId, request.assignee());
        List<String> changes = new ArrayList<>();
        String title = request.title().trim();
        if (!title.equals(task.getTitle())) changes.add("renamed to \"" + title + "\"");
        if (!Objects.equals(emptyToNull(request.description()), emptyToNull(task.getDescription()))) changes.add("edited the description");
        if (request.status() != task.getStatus()) changes.add("moved " + task.getStatus() + " → " + request.status());
        if (!Objects.equals(assignee, task.getAssignee())) changes.add(assignee == null ? "unassigned it" : "assigned it to " + assignee);
        if (changes.isEmpty()) {
            return TaskView.of(task); // no-op: no new version, no event
        }

        String previousAssignee = task.getAssignee();
        task.setTitle(title);
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setAssignee(assignee);
        long seq = touch(board, task, actor);

        activityService.record(boardId, taskId, actor, ActivityType.TASK_UPDATED,
                "updated \"" + task.getTitle() + "\": " + String.join(", ", changes), seq);
        if (assignee != null && !assignee.equals(previousAssignee)) {
            notificationService.notify(assignee, actor, NotificationType.TASK_ASSIGNED,
                    actor + " assigned you \"" + task.getTitle() + "\"", boardId, taskId);
        } else if (assignee != null) {
            notificationService.notify(assignee, actor, NotificationType.ASSIGNED_TASK_CHANGED,
                    actor + " " + String.join(", ", changes) + " on \"" + task.getTitle() + "\"", boardId, taskId);
        }
        publish(board.getId(), seq, BoardEvent.Type.TASK_UPDATED, task, actor);
        return TaskView.of(task);
    }

    @Transactional
    public TaskView move(Long boardId, Long taskId, MoveTaskRequest request, String actor) {
        boardAccess.require(boardId, actor, BoardRole.EDITOR);
        Board board = sequencer.lock(boardId);
        Task task = find(boardId, taskId);
        checkVersion(task, request.version());
        if (task.getStatus() == request.status()) {
            return TaskView.of(task);
        }
        TaskStatus from = task.getStatus();
        task.setStatus(request.status());
        long seq = touch(board, task, actor);

        activityService.record(boardId, taskId, actor, ActivityType.TASK_MOVED,
                "moved \"" + task.getTitle() + "\" " + from + " → " + request.status(), seq);
        notificationService.notify(task.getAssignee(), actor, NotificationType.ASSIGNED_TASK_CHANGED,
                actor + " moved \"" + task.getTitle() + "\" to " + request.status(), boardId, taskId);
        publish(board.getId(), seq, BoardEvent.Type.TASK_MOVED, task, actor);
        return TaskView.of(task);
    }

    /** Soft delete: the tombstone row lets reconnecting clients learn the task is gone. */
    @Transactional
    public void delete(Long boardId, Long taskId, Long expectedVersion, String actor) {
        boardAccess.require(boardId, actor, BoardRole.EDITOR);
        Board board = sequencer.lock(boardId);
        Task task = find(boardId, taskId);
        if (expectedVersion != null) {
            checkVersion(task, expectedVersion);
        }
        task.setDeleted(true);
        long seq = touch(board, task, actor);

        activityService.record(boardId, taskId, actor, ActivityType.TASK_DELETED, "deleted \"" + task.getTitle() + "\"", seq);
        notificationService.notify(task.getAssignee(), actor, NotificationType.ASSIGNED_TASK_DELETED,
                actor + " deleted \"" + task.getTitle() + "\"", boardId, taskId);
        publish(board.getId(), seq, BoardEvent.Type.TASK_DELETED, task, actor);
    }

    private void afterCreate(Board board, Task task, String actor, long seq) {
        activityService.record(board.getId(), task.getId(), actor, ActivityType.TASK_CREATED,
                "created \"" + task.getTitle() + "\" in " + task.getStatus(), seq);
        notificationService.notify(task.getAssignee(), actor, NotificationType.TASK_ASSIGNED,
                actor + " assigned you \"" + task.getTitle() + "\"", board.getId(), task.getId());
        publish(board.getId(), seq, BoardEvent.Type.TASK_CREATED, task, actor);
    }

    private long touch(Board board, Task task, String actor) {
        long seq = sequencer.next(board);
        task.setUpdatedBy(actor);
        task.setUpdatedAt(Instant.now());
        task.setLastEventSeq(seq);
        taskRepository.saveAndFlush(task); // flush so the returned/broadcast version is the new one
        return seq;
    }

    private void publish(Long boardId, long seq, BoardEvent.Type type, Task task, String actor) {
        events.publishEvent(new RealtimeEvents.BoardChanged(
                new BoardEvent(boardId, seq, type, TaskView.of(task), task.getId(), actor, Instant.now())));
    }

    private void checkVersion(Task task, Long expected) {
        if (!Objects.equals(task.getVersion(), expected)) {
            throw new TaskConflictException(TaskView.of(task));
        }
    }

    private Task find(Long boardId, Long taskId) {
        return taskRepository.findByIdAndBoardIdAndDeletedFalse(taskId, boardId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private String normalizeAssignee(Long boardId, String assignee) {
        String a = emptyToNull(assignee);
        if (a != null && boardAccess.roleOf(boardId, a).isEmpty()) {
            throw new IllegalArgumentException("Assignee '" + a + "' is not a member of this board");
        }
        return a;
    }

    private static Specification<Task> searchSpec(Long boardId, String q, TaskStatus status, String assignee) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("boardId"), boardId));
            p.add(cb.isFalse(root.get("deleted")));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (assignee != null && !assignee.isBlank()) p.add(cb.equal(root.get("assignee"), assignee.trim()));
            if (q != null && !q.isBlank()) {
                // lower(col) LIKE '%term%' is served by the pg_trgm GIN indexes from V1__init.sql.
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
