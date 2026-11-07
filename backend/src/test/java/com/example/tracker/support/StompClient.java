package com.example.tracker.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Test STOMP client that records everything: received messages per destination, ERROR frames, disconnects. */
public class StompClient implements AutoCloseable {
    private static final ThreadPoolTaskScheduler SCHEDULER = new ThreadPoolTaskScheduler();

    static {
        SCHEDULER.setPoolSize(2);
        SCHEDULER.initialize();
    }

    private final WebSocketStompClient client;
    private final CompletableFuture<StompSession> session;
    public final List<String> errors = new CopyOnWriteArrayList<>();
    public final CompletableFuture<Throwable> transportClosed = new CompletableFuture<>();
    private final ConcurrentHashMap<String, BlockingQueue<JsonNode>> inbox = new ConcurrentHashMap<>();

    private StompClient(String url, String token, ObjectMapper mapper) {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);
        client.setTaskScheduler(SCHEDULER);
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) connectHeaders.add("Authorization", "Bearer " + token);
        session = client.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message"))); // ERROR frames land here
            }

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                        byte[] payload, Throwable exception) {
                // ERROR frames whose text/plain body can't be converted are routed here.
                errors.add(String.valueOf(headers.getFirst("message")));
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                transportClosed.complete(ex);
            }
        });
    }

    public static StompClient connect(String url, String token, ObjectMapper mapper) {
        return new StompClient(url, token, mapper);
    }

    /** Waits for CONNECTED; throws if the server refused the connection. */
    public StompClient connected() {
        try {
            session.get(5, TimeUnit.SECONDS);
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("STOMP connect failed: " + errors, e);
        }
    }

    public boolean connectFails() {
        try {
            StompSession s = session.get(5, TimeUnit.SECONDS);
            // Some servers send CONNECTED then close; treat a prompt close as failure too.
            transportClosed.get(2, TimeUnit.SECONDS);
            return !s.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    public void subscribe(String destination) {
        BlockingQueue<JsonNode> queue = inbox.computeIfAbsent(destination, d -> new LinkedBlockingQueue<>());
        connected().session.join().subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
    }

    public void send(String destination, Object payload) {
        connected().session.join().send(destination, payload);
    }

    public JsonNode next(String destination, Duration timeout) {
        try {
            JsonNode msg = inbox.computeIfAbsent(destination, d -> new LinkedBlockingQueue<>())
                    .poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (msg == null) throw new AssertionError("No message on " + destination + " within " + timeout);
            return msg;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonNode poll(String destination, Duration timeout) {
        try {
            return inbox.computeIfAbsent(destination, d -> new LinkedBlockingQueue<>())
                    .poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isConnected() {
        return session.isDone() && !session.isCompletedExceptionally() && session.join().isConnected();
    }

    @Override
    public void close() {
        if (isConnected()) {
            try {
                session.join().disconnect();
            } catch (Exception ignored) {
            }
        }
        client.stop();
    }
}
