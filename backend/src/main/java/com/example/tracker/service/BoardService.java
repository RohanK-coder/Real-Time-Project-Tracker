package com.example.tracker.service;

import com.example.tracker.dto.BoardDtos.*;
import com.example.tracker.dto.BoardEvent;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.exception.NotFoundException;
import com.example.tracker.model.*;
import com.example.tracker.realtime.RealtimeEvents;
import com.example.tracker.repository.BoardMemberRepository;
import com.example.tracker.repository.BoardRepository;
import com.example.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final BoardMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final BoardAccessService boardAccess;
    private final BoardSequencer sequencer;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;

    @Transactional
    public BoardDetail create(CreateBoardRequest request, String username) {
        AppUser owner = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        Instant now = Instant.now();
        Board board = boardRepository.save(Board.builder()
                .name(request.name().trim()).description(request.description())
                .owner(owner).eventSeq(0).createdAt(now).updatedAt(now).build());
        memberRepository.save(BoardMember.builder().board(board).user(owner).role(BoardRole.OWNER).addedAt(now).build());
        boardAccess.evict(board.getId(), username); // drop any cached "NONE" from earlier probing
        activityService.record(board.getId(), null, username, ActivityType.BOARD_CREATED,
                "created board \"" + board.getName() + "\"", 0);
        return detail(board, BoardRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardSummary> listFor(String username, Integer page, Integer size) {
        var pageable = Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "board.updatedAt"));
        return PageResponse.of(memberRepository.findBoardsFor(username, pageable), m -> new BoardSummary(
                m.getBoard().getId(), m.getBoard().getName(), m.getBoard().getDescription(), m.getRole(),
                m.getBoard().getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public BoardDetail get(Long boardId, String username) {
        BoardRole role = boardAccess.require(boardId, username, BoardRole.VIEWER);
        Board board = boardRepository.findById(boardId).orElseThrow(() -> new NotFoundException("Board not found"));
        return detail(board, role);
    }

    @Transactional
    public BoardDetail addMember(Long boardId, AddMemberRequest request, String actor) {
        boardAccess.require(boardId, actor, BoardRole.OWNER);
        Board board = sequencer.lock(boardId);
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("User '" + request.username() + "' not found"));
        if (memberRepository.findMember(boardId, user.getUsername()).isPresent()) {
            throw new IllegalArgumentException(user.getUsername() + " is already a member of this board");
        }
        memberRepository.save(BoardMember.builder().board(board).user(user).role(request.role()).addedAt(Instant.now()).build());
        boardAccess.evict(boardId, user.getUsername());

        long seq = sequencer.next(board);
        activityService.record(boardId, null, actor, ActivityType.MEMBER_ADDED,
                "added " + user.getUsername() + " as " + request.role(), seq);
        notificationService.notify(user.getUsername(), actor, NotificationType.ADDED_TO_BOARD,
                actor + " added you to \"" + board.getName() + "\" as " + request.role(), boardId, null);
        publishMembersChanged(boardId, seq, actor);
        return detail(board, BoardRole.OWNER);
    }

    @Transactional
    public BoardDetail updateMember(Long boardId, String username, UpdateMemberRequest request, String actor) {
        boardAccess.require(boardId, actor, BoardRole.OWNER);
        Board board = sequencer.lock(boardId);
        BoardMember member = memberRepository.findMember(boardId, username)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        guardLastOwner(boardId, member, request.role());
        BoardRole previous = member.getRole();
        member.setRole(request.role());
        boardAccess.evict(boardId, username);

        long seq = sequencer.next(board);
        activityService.record(boardId, null, actor, ActivityType.MEMBER_ROLE_CHANGED,
                "changed " + username + " from " + previous + " to " + request.role(), seq);
        publishMembersChanged(boardId, seq, actor);
        return detail(board, boardAccess.require(boardId, actor, BoardRole.VIEWER));
    }

    /** Owners can remove anyone; any member can remove themselves (leave the board). */
    @Transactional
    public void removeMember(Long boardId, String username, String actor) {
        boardAccess.require(boardId, actor, actor.equals(username) ? BoardRole.VIEWER : BoardRole.OWNER);
        Board board = sequencer.lock(boardId);
        BoardMember member = memberRepository.findMember(boardId, username)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        guardLastOwner(boardId, member, null);
        memberRepository.delete(member);
        boardAccess.evict(boardId, username);

        long seq = sequencer.next(board);
        activityService.record(boardId, null, actor, ActivityType.MEMBER_REMOVED,
                actor.equals(username) ? username + " left the board" : "removed " + username, seq);
        publishMembersChanged(boardId, seq, actor);
        events.publishEvent(new RealtimeEvents.AccessRevoked(username, boardId));
    }

    @Transactional
    public void delete(Long boardId, String actor) {
        boardAccess.require(boardId, actor, BoardRole.OWNER);
        sequencer.lock(boardId);
        var members = memberRepository.findAllByBoard(boardId);
        boardRepository.deleteById(boardId); // cascades to members, tasks, activity, notifications
        members.forEach(m -> {
            boardAccess.evict(boardId, m.getUser().getUsername());
            events.publishEvent(new RealtimeEvents.AccessRevoked(m.getUser().getUsername(), boardId));
        });
    }

    private void guardLastOwner(Long boardId, BoardMember member, BoardRole newRole) {
        if (member.getRole() == BoardRole.OWNER && newRole != BoardRole.OWNER
                && memberRepository.countByBoardIdAndRole(boardId, BoardRole.OWNER) <= 1) {
            throw new IllegalArgumentException("A board must keep at least one owner");
        }
    }

    private void publishMembersChanged(Long boardId, long seq, String actor) {
        events.publishEvent(new RealtimeEvents.BoardChanged(
                new BoardEvent(boardId, seq, BoardEvent.Type.MEMBERS_CHANGED, null, null, actor, Instant.now())));
    }

    private BoardDetail detail(Board board, BoardRole myRole) {
        var members = memberRepository.findAllByBoard(board.getId()).stream()
                .map(m -> new Member(m.getUser().getUsername(), m.getRole(), m.getAddedAt())).toList();
        return new BoardDetail(board.getId(), board.getName(), board.getDescription(), board.getOwner().getUsername(),
                myRole, board.getEventSeq(), members, board.getCreatedAt());
    }
}
