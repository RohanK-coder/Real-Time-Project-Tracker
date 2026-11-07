package com.example.tracker.it;

import com.example.tracker.support.IntegrationTest;
import com.example.tracker.support.StompClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client that drops off (network blip, laptop sleep) must converge to the same state as one that
 * stayed connected: it remembers the last seq it applied, calls /changes?sinceSeq=N on reconnect,
 * then resumes the live stream with no gap.
 */
class ReconnectCatchUpIT extends IntegrationTest {

    @Test
    void clientCatchesUpOnEverythingMissedWhileDisconnected() {
        User owner = newUser("owner");
        User bob = newUser("bob");
        long board = newBoard(owner, Map.of(bob, "EDITOR"));
        String topic = "/topic/boards/" + board;
        long keep = newTask(board, owner, "will be edited").get("id").asLong();
        long doomed = newTask(board, owner, "will be deleted").get("id").asLong();

        long lastSeq;
        try (StompClient c = stomp(bob.token()).connected()) {
            c.subscribe(topic);
            awaitSubscribed(bob.name(), topic);
            newTask(board, owner, "seen live");
            lastSeq = c.next(topic, Duration.ofSeconds(5)).get("seq").asLong();
        } // bob disconnects

        // While bob is offline: create, update, move, delete.
        long created = newTask(board, owner, "created while offline").get("id").asLong();
        JsonNode k = api().get("/api/boards/" + board + "/tasks/" + keep, owner.token()).expect(200);
        k = api().put("/api/boards/" + board + "/tasks/" + keep, owner.token(), Map.of("title", "edited while offline",
                "status", "TODO", "version", k.get("version").asLong())).expect(200);
        api().put("/api/boards/" + board + "/tasks/" + keep + "/move", owner.token(),
                Map.of("status", "DONE", "version", k.get("version").asLong())).expect(200);
        api().delete("/api/boards/" + board + "/tasks/" + doomed, owner.token()).expect(204);

        // Reconnect: fetch the delta first...
        JsonNode changes = api().get("/api/boards/" + board + "/changes?sinceSeq=" + lastSeq, bob.token()).expect(200);
        assertThat(changes.get("currentSeq").asLong()).isEqualTo(lastSeq + 4);
        Map<Long, JsonNode> byId = new HashMap<>();
        changes.get("tasks").forEach(t -> byId.put(t.get("id").asLong(), t));

        assertThat(byId).containsOnlyKeys(created, keep, doomed);
        assertThat(byId.get(keep).get("title").asText()).isEqualTo("edited while offline");
        assertThat(byId.get(keep).get("status").asText()).isEqualTo("DONE"); // latest state, not intermediate
        assertThat(byId.get(doomed).get("deleted").asBoolean()).as("deletion tombstone").isTrue();

        // ...then resume live: the next event continues exactly at currentSeq + 1.
        try (StompClient c = stomp(bob.token()).connected()) {
            c.subscribe(topic);
            awaitSubscribed(bob.name(), topic);
            newTask(board, owner, "after reconnect");
            assertThat(c.next(topic, Duration.ofSeconds(5)).get("seq").asLong()).isEqualTo(changes.get("currentSeq").asLong() + 1);
        }
    }

    @Test
    void emptyDeltaWhenNothingChanged() {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        newTask(board, owner, "x");
        long seq = api().get("/api/boards/" + board, owner.token()).expect(200).get("eventSeq").asLong();
        JsonNode changes = api().get("/api/boards/" + board + "/changes?sinceSeq=" + seq, owner.token()).expect(200);
        assertThat(changes.get("tasks")).isEmpty();
        assertThat(changes.get("currentSeq").asLong()).isEqualTo(seq);
    }
}
