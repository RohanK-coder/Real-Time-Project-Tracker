package com.example.tracker.repository;

import com.example.tracker.model.Board;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    /**
     * SELECT ... FOR UPDATE on the board row. Every board mutation takes this lock first, which
     * (a) serialises writers per board so event sequence numbers are gap-free and commit-ordered, and
     * (b) makes the optimistic version check deterministic, because the second writer reads the
     * task only after the first has committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Board b where b.id = :id")
    Optional<Board> lockById(@Param("id") Long id);
}
