package com.example.tracker.service;

import com.example.tracker.exception.NotFoundException;
import com.example.tracker.model.Board;
import com.example.tracker.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Issues per-board event sequence numbers. Must run inside the caller's write transaction. */
@Component
@RequiredArgsConstructor
public class BoardSequencer {
    private final BoardRepository boardRepository;

    /** Row-locks the board until the surrounding transaction commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Board lock(Long boardId) {
        return boardRepository.lockById(boardId).orElseThrow(() -> new NotFoundException("Board not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long currentSeq(Long boardId) {
        return boardRepository.findById(boardId).map(Board::getEventSeq)
                .orElseThrow(() -> new NotFoundException("Board not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(Board lockedBoard) {
        long seq = lockedBoard.getEventSeq() + 1;
        lockedBoard.setEventSeq(seq);
        lockedBoard.setUpdatedAt(Instant.now());
        return seq;
    }
}
