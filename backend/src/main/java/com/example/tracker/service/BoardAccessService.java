package com.example.tracker.service;

import com.example.tracker.exception.ForbiddenException;
import com.example.tracker.exception.NotFoundException;
import com.example.tracker.model.BoardRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Single authorization point for board-scoped operations, used by REST services and the STOMP
 * SUBSCRIBE interceptor alike. Non-members get 404 (the board's existence is not leaked);
 * members without enough privilege get 403.
 */
@Service
@RequiredArgsConstructor
public class BoardAccessService {
    private final MembershipCache membershipCache;

    public Optional<BoardRole> roleOf(Long boardId, String username) {
        String role = membershipCache.roleOf(boardId, username);
        return MembershipCache.NONE.equals(role) ? Optional.empty() : Optional.of(BoardRole.valueOf(role));
    }

    public BoardRole require(Long boardId, String username, BoardRole minimum) {
        BoardRole role = roleOf(boardId, username).orElseThrow(() -> new NotFoundException("Board not found"));
        if (!role.atLeast(minimum)) {
            throw new ForbiddenException("This action requires the " + minimum + " role on this board");
        }
        return role;
    }

    /**
     * Evicts now and again after commit: a concurrent request could otherwise re-populate the cache
     * with the pre-change role between the first eviction and the commit.
     */
    public void evict(Long boardId, String username) {
        membershipCache.evict(boardId, username);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    membershipCache.evict(boardId, username);
                }
            });
        }
    }
}
