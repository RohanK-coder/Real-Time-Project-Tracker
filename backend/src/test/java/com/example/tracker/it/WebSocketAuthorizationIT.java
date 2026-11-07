package com.example.tracker.it;

import com.example.tracker.support.IntegrationTest;
import com.example.tracker.support.StompClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The same board rules as REST, enforced on STOMP CONNECT / SUBSCRIBE / SEND. */
class WebSocketAuthorizationIT extends IntegrationTest {
    private static final Duration SHORT = Duration.ofMillis(700);
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void connectWithoutOrWithBadTokenIsRefused() {
        try (StompClient anonymous = stomp(null); StompClient forged = stomp("garbage.token.value")) {
            assertThat(anonymous.connectFails()).isTrue();
            assertThat(forged.connectFails()).isTrue();
        }
    }

    @Test
    void nonMemberCannotSubscribeToABoardTopic() {
        User owner = newUser("owner");
        User outsider = newUser("outsider");
        long board = newBoard(owner, Map.of());
        String topic = "/topic/boards/" + board;

        try (StompClient spy = stomp(outsider.token()).connected()) {
            spy.subscribe(topic);
            newTask(board, owner, "confidential");

            assertThat(spy.poll(topic, SHORT)).as("outsider must not receive board events").isNull();
            await(() -> !spy.errors.isEmpty(), WAIT, "ERROR frame");
            assertThat(spy.errors.get(0)).contains("Not a member");
        }
    }

    @Test
    void arbitraryDestinationsAndClientSendsAreDenied() {
        User u = newUser("u");
        User victim = newUser("victim");
        try (StompClient c1 = stomp(u.token()).connected();
             StompClient c2 = stomp(u.token()).connected();
             StompClient c3 = stomp(u.token()).connected()) {
            c1.subscribe("/topic/boards/1/../2");
            c2.subscribe("/user/" + victim.name() + "/queue/notifications"); // someone else's queue
            c3.send("/app/anything", Map.of("x", 1));
            await(() -> !c1.errors.isEmpty() && !c2.errors.isEmpty() && !c3.errors.isEmpty(), WAIT, "3 ERROR frames");
        }
    }

    @Test
    void membersOfAnyRoleReceiveLiveEvents() {
        User owner = newUser("owner");
        User viewer = newUser("viewer");
        long board = newBoard(owner, Map.of(viewer, "VIEWER"));
        String topic = "/topic/boards/" + board;

        try (StompClient client = stomp(viewer.token()).connected()) {
            client.subscribe(topic);
            awaitSubscribed(viewer.name(), topic);
            long id = newTask(board, owner, "hello").get("id").asLong();

            var event = client.next(topic, WAIT);
            assertThat(event.get("type").asText()).isEqualTo("TASK_CREATED");
            assertThat(event.get("task").get("id").asLong()).isEqualTo(id);
            assertThat(event.get("actor").asText()).isEqualTo(owner.name());
        }
    }

    @Test
    void removingAMemberClosesTheirLiveSocket() throws Exception {
        User owner = newUser("owner");
        User editor = newUser("editor");
        long board = newBoard(owner, Map.of(editor, "EDITOR"));
        String topic = "/topic/boards/" + board;

        try (StompClient client = stomp(editor.token()).connected()) {
            client.subscribe(topic);
            awaitSubscribed(editor.name(), topic);

            api().delete("/api/boards/" + board + "/members/" + editor.name(), owner.token()).expect(204);

            client.transportClosed.get(5, TimeUnit.SECONDS); // server closed the socket
            assertThat(client.isConnected()).isFalse();
        }
        // Reconnecting and re-subscribing is now refused.
        try (StompClient again = stomp(editor.token()).connected()) {
            again.subscribe(topic);
            await(() -> !again.errors.isEmpty(), WAIT, "ERROR frame on re-subscribe");
        }
    }

    @Test
    void notificationsArePushedOnlyToTheirRecipient() {
        User owner = newUser("owner");
        User alice = newUser("alice");
        User bob = newUser("bob");
        long board = newBoard(owner, Map.of(alice, "EDITOR", bob, "EDITOR"));
        String queue = "/user/queue/notifications";

        try (StompClient a = stomp(alice.token()).connected(); StompClient b = stomp(bob.token()).connected()) {
            a.subscribe(queue);
            b.subscribe(queue);
            awaitSubscribed(alice.name(), queue);
            awaitSubscribed(bob.name(), queue);

            api().post("/api/boards/" + board + "/tasks", owner.token(),
                    Map.of("title", "for alice", "status", "TODO", "assignee", alice.name())).expect(201);

            var n = a.next(queue, WAIT);
            assertThat(n.get("type").asText()).isEqualTo("TASK_ASSIGNED");
            assertThat(n.get("message").asText()).contains("for alice");
            assertThat(b.poll(queue, SHORT)).isNull();
        }
    }
}
