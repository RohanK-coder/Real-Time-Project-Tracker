package com.example.tracker.model;

/** Per-board role, ordered from least to most privileged. */
public enum BoardRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean atLeast(BoardRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
