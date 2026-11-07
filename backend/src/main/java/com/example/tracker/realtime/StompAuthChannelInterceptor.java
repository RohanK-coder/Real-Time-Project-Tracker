package com.example.tracker.realtime;

import com.example.tracker.model.BoardRole;
import com.example.tracker.security.JwtService;
import com.example.tracker.service.BoardAccessService;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorization for STOMP frames, mirroring the REST rules:
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <jwt>}; otherwise the client gets an ERROR frame.</li>
 *   <li>SUBSCRIBE to {@code /topic/boards/{id}} requires at least VIEWER on that board (same check as REST).</li>
 *   <li>SUBSCRIBE to {@code /user/queue/notifications} is allowed (Spring routes it to the caller only).</li>
 *   <li>Every other destination and every client SEND is denied: all writes go through the REST API,
 *       where validation, versioning and auditing live.</li>
 *   <li>Frames after the JWT expires are rejected, so a long-lived socket can't outlive its token.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/boards/(\\d+)$");
    private static final String EXPIRES_AT = "tokenExpiresAt";

    private final JwtService jwtService;
    private final BoardAccessService boardAccess;
    private final MeterRegistry meters;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats
        }
        try {
            switch (accessor.getCommand()) {
                case CONNECT -> authenticate(accessor);
                case SUBSCRIBE -> authorizeSubscribe(accessor);
                case SEND -> throw new AccessDeniedException("Clients may not SEND; use the REST API");
                default -> { }
            }
        } catch (AccessDeniedException ex) {
            meters.counter("tracker.websocket.denied", "command", accessor.getCommand().name()).increment();
            throw ex;
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token");
        }
        try {
            var token = jwtService.verify(header.substring(7));
            accessor.setUser(token.toAuthentication());
            sessionAttributes(accessor).put(EXPIRES_AT, token.expiresAt());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid or expired token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal user = requireLiveUser(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("Missing destination");
        }
        if (destination.equals("/user/queue/notifications")) {
            return;
        }
        Matcher m = BOARD_TOPIC.matcher(destination);
        if (!m.matches()) {
            throw new AccessDeniedException("Subscription to " + destination + " is not allowed");
        }
        long boardId = Long.parseLong(m.group(1));
        if (boardAccess.roleOf(boardId, user.getName()).filter(r -> r.atLeast(BoardRole.VIEWER)).isEmpty()) {
            throw new AccessDeniedException("Not a member of board " + boardId);
        }
    }

    private Principal requireLiveUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        Object expiresAt = sessionAttributes(accessor).get(EXPIRES_AT);
        if (expiresAt instanceof Instant exp && exp.isBefore(Instant.now())) {
            throw new AccessDeniedException("Token expired; reconnect with a fresh token");
        }
        return user;
    }

    private static Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) {
            throw new AccessDeniedException("No session");
        }
        return attrs;
    }
}
