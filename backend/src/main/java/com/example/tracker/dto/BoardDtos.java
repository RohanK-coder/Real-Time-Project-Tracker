package com.example.tracker.dto;

import com.example.tracker.model.BoardRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class BoardDtos {
    private BoardDtos() {}

    public record CreateBoardRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description) {}

    public record AddMemberRequest(@NotBlank String username, @NotNull BoardRole role) {}

    public record UpdateMemberRequest(@NotNull BoardRole role) {}

    public record BoardSummary(Long id, String name, String description, BoardRole myRole, Instant updatedAt) {}

    public record Member(String username, BoardRole role, Instant addedAt) {}

    public record BoardDetail(Long id, String name, String description, String owner, BoardRole myRole,
                              long eventSeq, List<Member> members, Instant createdAt) {}
}
