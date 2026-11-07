package com.example.tracker.realtime;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks this instance's raw WebSocket sessions so access can be revoked mid-connection: when a user
 * is removed from a board, their sockets are closed and the client's reconnect must pass the
 * SUBSCRIBE authorization check again.
 */
@Component
@Slf4j
public class SessionRegistry implements WebSocketHandlerDecoratorFactory {
    /** Application-defined close code (4000-4999 range). */
    public static final CloseStatus ACCESS_CHANGED = new CloseStatus(4403, "Board access changed");

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectProvider<SimpUserRegistry> userRegistry; // lazy: avoids a cycle with the broker config

    public SessionRegistry(ObjectProvider<SimpUserRegistry> userRegistry, MeterRegistry meters) {
        this.userRegistry = userRegistry;
        Gauge.builder("tracker.websocket.sessions", sessions, Map::size)
                .description("Open WebSocket sessions on this instance").register(meters);
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    public void closeSessionsOf(String username) {
        var user = userRegistry.getObject().getUser(username);
        if (user == null) return;
        user.getSessions().forEach(simpSession -> {
            WebSocketSession ws = sessions.get(simpSession.getId());
            if (ws != null && ws.isOpen()) {
                try {
                    ws.close(ACCESS_CHANGED);
                } catch (IOException ex) {
                    log.warn("Could not close session {} of {}", ws.getId(), username, ex);
                }
            }
        });
    }
}
