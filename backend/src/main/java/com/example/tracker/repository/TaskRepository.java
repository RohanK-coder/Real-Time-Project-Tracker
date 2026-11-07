package com.example.tracker.repository;

import com.example.tracker.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    Optional<Task> findByIdAndBoardIdAndDeletedFalse(Long id, Long boardId);

    /** Includes tombstones so reconnecting clients learn about deletions. */
    List<Task> findByBoardIdAndLastEventSeqGreaterThanOrderByLastEventSeqAsc(Long boardId, long seq);
}
