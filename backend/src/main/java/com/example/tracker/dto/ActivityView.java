package com.example.tracker.dto;

import com.example.tracker.model.Activity;
import com.example.tracker.model.ActivityType;

import java.time.Instant;

public record ActivityView(Long id, Long boardId, Long taskId, String actor, ActivityType type,
                           String summary, long seq, Instant createdAt) {
    public static ActivityView of(Activity a) {
        return new ActivityView(a.getId(), a.getBoardId(), a.getTaskId(), a.getActor(), a.getType(),
                a.getSummary(), a.getSeq(), a.getCreatedAt());
    }
}
