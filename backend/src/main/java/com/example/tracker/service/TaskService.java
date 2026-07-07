package com.example.tracker.service;

import com.example.tracker.dto.TaskMoveRequest;
import com.example.tracker.dto.TaskRequest;
import com.example.tracker.model.Task;
import com.example.tracker.model.TaskStatus;
import com.example.tracker.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskBroadcastService taskBroadcastService;

    public List<Task> findAll() {
        return taskRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Task create(TaskRequest request, Authentication authentication) {
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .assignee(request.getAssignee())
                .createdBy(authentication.getName())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Task saved = taskRepository.save(task);
        taskBroadcastService.publishTaskUpdate(saved);
        return saved;
    }

    public Task update(Long id, TaskRequest request) {
        Task task = getTask(id);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setAssignee(request.getAssignee());
        task.setUpdatedAt(Instant.now());
        Task saved = taskRepository.save(task);
        taskBroadcastService.publishTaskUpdate(saved);
        return saved;
    }

    public Task move(Long id, TaskMoveRequest request) {
        Task task = getTask(id);
        task.setStatus(request.getStatus());
        task.setUpdatedAt(Instant.now());
        Task saved = taskRepository.save(task);
        taskBroadcastService.publishTaskUpdate(saved);
        return saved;
    }

    public void delete(Long id) {
        taskRepository.deleteById(id);
        taskBroadcastService.publishTaskDeleted(id);
    }

    public Task createFromGithubIssue(String title, String body, String issueUrl, String openedBy) {
        Task task = Task.builder()
                .title(title)
                .description(body)
                .status(TaskStatus.TODO)
                .createdBy(openedBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .githubIssueUrl(issueUrl)
                .build();
        Task saved = taskRepository.save(task);
        taskBroadcastService.publishTaskUpdate(saved);
        return saved;
    }

    private Task getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Task not found"));
    }
}
