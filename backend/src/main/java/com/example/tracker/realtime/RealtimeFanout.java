package com.example.tracker.realtime;

import com.example.tracker.realtime.FanoutEnvelope.Kind;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

/**
 * Horizontal scaling: each instance only holds the WebSocket sessions connected to it. After commit,
 * an event is published once to a Redis channel; every instance (including this one) receives it and
 * delivers it to its own local STOMP subscribers. If Redis is down, we deliver locally so single-node
 * deployments keep working, and clients on other nodes recover via the seq-gap catch-up.
 */
@Component
@Slf4j
public class RealtimeFanout implements MessageListener {
    public static final String CHANNEL = "tracker:realtime";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;
    private final SessionRegistry sessions;
    private final MeterRegistry meters;
    private final boolean redisFanout;

    public RealtimeFanout(StringRedisTemplate redis, SimpMessagingTemplate messaging, ObjectMapper objectMapper,
                          SessionRegistry sessions, MeterRegistry meters,
                          @Value("${app.realtime.redis-fanout:true}") boolean redisFanout) {
        this.redis = redis;
        this.messaging = messaging;
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.meters = meters;
        this.redisFanout = redisFanout;
    }

    @TransactionalEventListener
    public void onBoardChanged(RealtimeEvents.BoardChanged e) {
        publish(new FanoutEnvelope(Kind.BOARD_EVENT, e.event().boardId(), null, objectMapper.valueToTree(e.event())));
    }

    @TransactionalEventListener
    public void onNotification(RealtimeEvents.NotificationCreated e) {
        publish(new FanoutEnvelope(Kind.USER_NOTIFICATION, null, e.recipient(), objectMapper.valueToTree(e.notification())));
    }

    @TransactionalEventListener
    public void onRevoked(RealtimeEvents.AccessRevoked e) {
        publish(new FanoutEnvelope(Kind.REVOKE, e.boardId(), e.username(), null));
    }

    private void publish(FanoutEnvelope envelope) {
        meters.counter("tracker.realtime.published", "kind", envelope.kind().name()).increment();
        if (redisFanout) {
            try {
                redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
                return;
            } catch (Exception ex) {
                meters.counter("tracker.realtime.fanout.fallback").increment();
                log.warn("Redis fan-out failed, delivering to local sessions only: {}", ex.getMessage());
            }
        }
        deliverLocally(envelope);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliverLocally(objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), FanoutEnvelope.class));
        } catch (Exception ex) {
            log.error("Could not process fan-out message", ex);
        }
    }

    void deliverLocally(FanoutEnvelope envelope) {
        switch (envelope.kind()) {
            case BOARD_EVENT -> messaging.convertAndSend("/topic/boards/" + envelope.boardId(), envelope.payload());
            case USER_NOTIFICATION ->
                    messaging.convertAndSendToUser(envelope.username(), "/queue/notifications", envelope.payload());
            case REVOKE -> sessions.closeSessionsOf(envelope.username());
        }
    }
}
