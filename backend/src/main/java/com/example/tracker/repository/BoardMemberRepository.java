package com.example.tracker.repository;

import com.example.tracker.model.BoardMember;
import com.example.tracker.model.BoardRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {

    @Query("select m.role from BoardMember m where m.board.id = :boardId and m.user.username = :username")
    Optional<BoardRole> findRole(@Param("boardId") Long boardId, @Param("username") String username);

    @Query("select m from BoardMember m join fetch m.user where m.board.id = :boardId and m.user.username = :username")
    Optional<BoardMember> findMember(@Param("boardId") Long boardId, @Param("username") String username);

    @Query("select m from BoardMember m join fetch m.user where m.board.id = :boardId order by m.addedAt")
    List<BoardMember> findAllByBoard(@Param("boardId") Long boardId);

    long countByBoardIdAndRole(Long boardId, BoardRole role);

    @Query(value = "select m from BoardMember m join fetch m.board where m.user.username = :username",
           countQuery = "select count(m) from BoardMember m where m.user.username = :username")
    Page<BoardMember> findBoardsFor(@Param("username") String username, Pageable pageable);
}
