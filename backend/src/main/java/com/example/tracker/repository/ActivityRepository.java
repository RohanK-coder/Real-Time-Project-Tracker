package com.example.tracker.repository;

import com.example.tracker.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Page<Activity> findByBoardId(Long boardId, Pageable pageable);
    Page<Activity> findByBoardIdAndTaskId(Long boardId, Long taskId, Pageable pageable);
}
