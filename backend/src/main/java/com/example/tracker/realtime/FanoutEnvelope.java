package com.example.tracker.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** Wire format on the Redis channel shared by all backend instances. */
public record FanoutEnvelope(Kind kind, Long boardId, String username, JsonNode payload) {
    public enum Kind { BOARD_EVENT, USER_NOTIFICATION, REVOKE }
}
