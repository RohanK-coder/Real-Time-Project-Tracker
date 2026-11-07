package com.example.tracker.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {
    @LocalServerPort
    protected int port;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected SimpUserRegistry simpUserRegistry;

    protected Api api() {
        return new Api("http://localhost:" + port, objectMapper);
    }

    protected StompClient stomp(String token) {
        return StompClient.connect("ws://localhost:" + port + "/ws", token, objectMapper);
    }

    /** Unique per test run so test classes never collide on usernames. */
    protected static String uniq(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected User newUser(String prefix) {
        String name = uniq(prefix);
        JsonNode res = api().post("/api/auth/register", null,
                Map.of("username", name, "email", name + "@test.dev", "password", "password123")).expect(201);
        return new User(name, res.get("token").asText());
    }

    /** Creates a board owned by {@code owner} and adds the given members as username->role. */
    protected long newBoard(User owner, Map<User, String> members) {
        long boardId = api().post("/api/boards", owner.token(), Map.of("name", uniq("board"))).expect(201).get("id").asLong();
        members.forEach((u, role) -> api().post("/api/boards/" + boardId + "/members", owner.token(),
                Map.of("username", u.name(), "role", role)).expect(201));
        return boardId;
    }

    protected JsonNode newTask(long boardId, User user, String title) {
        return api().post("/api/boards/" + boardId + "/tasks", user.token(),
                Map.of("title", title, "status", "TODO")).expect(201);
    }

    /** Waits until the server has registered a SUBSCRIBE (removes sleep-based flakiness). */
    protected void awaitSubscribed(String username, String destination) {
        await(() -> simpUserRegistry.findSubscriptions(s ->
                s.getSession().getUser().getName().equals(username) && s.getDestination().equals(destination)).size() > 0,
                Duration.ofSeconds(5), "subscription of " + username + " to " + destination);
    }

    protected static void await(BooleanSupplier condition, Duration timeout, String what) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(condition.getAsBoolean()).as("timed out waiting for " + what).isTrue();
    }

    public record User(String name, String token) {}
}
