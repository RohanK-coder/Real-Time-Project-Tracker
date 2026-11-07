package com.example.tracker.service;

import com.example.tracker.dto.ActivityView;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.model.Activity;
import com.example.tracker.model.ActivityType;
import com.example.tracker.model.BoardRole;
import com.example.tracker.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final BoardAccessService boardAccess;

    /** Written in the same transaction as the change it describes, so history can't drift from state. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long boardId, Long taskId, String actor, ActivityType type, String summary, long seq) {
        activityRepository.save(Activity.builder()
                .boardId(boardId).taskId(taskId).actor(actor).type(type)
                .summary(summary.length() > 1000 ? summary.substring(0, 997) + "..." : summary)
                .seq(seq).createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityView> list(Long boardId, Long taskId, String username, Integer page, Integer size) {
        boardAccess.require(boardId, username, BoardRole.VIEWER);
        var pageable = Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "id"));
        var result = taskId == null
                ? activityRepository.findByBoardId(boardId, pageable)
                : activityRepository.findByBoardIdAndTaskId(boardId, taskId, pageable);
        return PageResponse.of(result, ActivityView::of);
    }
}
