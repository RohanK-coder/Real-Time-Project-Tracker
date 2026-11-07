package com.example.tracker.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class Paging {
    public static final int DEFAULT_SIZE = 20;

    private Paging() {}

    /** Clamps client-supplied paging so a single request can't ask for an unbounded page. */
    public static PageRequest of(Integer page, Integer size, int maxSize, Sort sort) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? DEFAULT_SIZE : Math.min(Math.max(1, size), maxSize);
        return PageRequest.of(p, s, sort);
    }
}
