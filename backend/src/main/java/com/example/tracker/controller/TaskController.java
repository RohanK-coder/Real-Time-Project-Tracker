package com.example.tracker.controller;

import com.example.tracker.dto.TaskMoveRequest;
import com.example.tracker.dto.TaskRequest;
import com.example.tracker.model.Task;
import com.example.tracker.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    public List<Task> getTasks() {
        return taskService.findAll();
    }

    @PostMapping
    public Task createTask(@Valid @RequestBody TaskRequest request, Authentication authentication) {
        return taskService.create(request, authentication);
    }

    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @PutMapping("/{id}/move")
    public Task moveTask(@PathVariable Long id, @Valid @RequestBody TaskMoveRequest request) {
        return taskService.move(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.delete(id);
    }
}
