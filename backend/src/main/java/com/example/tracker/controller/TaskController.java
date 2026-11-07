package com.example.tracker.controller;

import com.example.tracker.dto.PageResponse;
import com.example.tracker.dto.TaskDtos.*;
import com.example.tracker.model.TaskStatus;
import com.example.tracker.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boards/{boardId}")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    /** Search + filter + paginate. {@code q} matches title or description (case-insensitive substring). */
    @GetMapping("/tasks")
    public PageResponse<TaskView> search(@PathVariable Long boardId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) TaskStatus status,
                                         @RequestParam(required = false) String assignee,
                                         @RequestParam(required = false, defaultValue = "updated") String sort,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         Authentication auth) {
        return taskService.search(boardId, auth.getName(), q, status, assignee, sort, page, size);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView create(@PathVariable Long boardId, @Valid @RequestBody CreateTaskRequest request, Authentication auth) {
        return taskService.create(boardId, request, auth.getName());
    }

    @GetMapping("/tasks/{taskId}")
    public TaskView get(@PathVariable Long boardId, @PathVariable Long taskId, Authentication auth) {
        return taskService.get(boardId, taskId, auth.getName());
    }

    @PutMapping("/tasks/{taskId}")
    public TaskView update(@PathVariable Long boardId, @PathVariable Long taskId,
                           @Valid @RequestBody UpdateTaskRequest request, Authentication auth) {
        return taskService.update(boardId, taskId, request, auth.getName());
    }

    @PutMapping("/tasks/{taskId}/move")
    public TaskView move(@PathVariable Long boardId, @PathVariable Long taskId,
                         @Valid @RequestBody MoveTaskRequest request, Authentication auth) {
        return taskService.move(boardId, taskId, request, auth.getName());
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long boardId, @PathVariable Long taskId,
                       @RequestParam(required = false) Long version, Authentication auth) {
        taskService.delete(boardId, taskId, version, auth.getName());
    }

    /** Everything that changed after {@code sinceSeq} (including deletion tombstones). Used after reconnects. */
    @GetMapping("/changes")
    public ChangesResponse changes(@PathVariable Long boardId, @RequestParam long sinceSeq, Authentication auth) {
        return taskService.changesSince(boardId, sinceSeq, auth.getName());
    }
}
