package com.example.tracker.dto;

import com.example.tracker.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskRequest {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private TaskStatus status;
    private String assignee;
}
