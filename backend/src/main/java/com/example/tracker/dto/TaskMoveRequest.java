package com.example.tracker.dto;

import com.example.tracker.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskMoveRequest {
    @NotNull
    private TaskStatus status;
}
