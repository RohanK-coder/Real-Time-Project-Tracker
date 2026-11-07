package com.example.tracker.controller;

import com.example.tracker.dto.ActivityView;
import com.example.tracker.dto.BoardDtos.*;
import com.example.tracker.dto.PageResponse;
import com.example.tracker.service.ActivityService;
import com.example.tracker.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;
    private final ActivityService activityService;

    @GetMapping
    public PageResponse<BoardSummary> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size, Authentication auth) {
        return boardService.listFor(auth.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardDetail create(@Valid @RequestBody CreateBoardRequest request, Authentication auth) {
        return boardService.create(request, auth.getName());
    }

    @GetMapping("/{boardId}")
    public BoardDetail get(@PathVariable Long boardId, Authentication auth) {
        return boardService.get(boardId, auth.getName());
    }

    @DeleteMapping("/{boardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long boardId, Authentication auth) {
        boardService.delete(boardId, auth.getName());
    }

    @PostMapping("/{boardId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardDetail addMember(@PathVariable Long boardId, @Valid @RequestBody AddMemberRequest request, Authentication auth) {
        return boardService.addMember(boardId, request, auth.getName());
    }

    @PutMapping("/{boardId}/members/{username}")
    public BoardDetail updateMember(@PathVariable Long boardId, @PathVariable String username,
                                    @Valid @RequestBody UpdateMemberRequest request, Authentication auth) {
        return boardService.updateMember(boardId, username, request, auth.getName());
    }

    @DeleteMapping("/{boardId}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long boardId, @PathVariable String username, Authentication auth) {
        boardService.removeMember(boardId, username, auth.getName());
    }

    @GetMapping("/{boardId}/activity")
    public PageResponse<ActivityView> activity(@PathVariable Long boardId,
                                               @RequestParam(required = false) Long taskId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size, Authentication auth) {
        return activityService.list(boardId, taskId, auth.getName(), page, size);
    }
}
