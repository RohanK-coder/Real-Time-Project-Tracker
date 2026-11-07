package com.example.tracker.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable pagination envelope (avoids serialising Spring's PageImpl, whose JSON shape is not a contract). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
