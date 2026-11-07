package com.example.tracker.it;

import com.example.tracker.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryNotificationsSearchIT extends IntegrationTest {

    @Test
    void activityHistoryIsPagedNewestFirstAndFilterableByTask() {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        JsonNode t = newTask(board, owner, "tracked");
        long id = t.get("id").asLong();
        api().put("/api/boards/" + board + "/tasks/" + id + "/move", owner.token(), Map.of("status", "IN_PROGRESS", "version", 0)).expect(200);
        api().put("/api/boards/" + board + "/tasks/" + id + "/move", owner.token(), Map.of("status", "DONE", "version", 1)).expect(200);
        newTask(board, owner, "other");

        JsonNode page0 = api().get("/api/boards/" + board + "/activity?size=2", owner.token()).expect(200);
        assertThat(page0.get("totalItems").asLong()).isEqualTo(5); // board created + 2 creates + 2 moves
        assertThat(page0.get("hasNext").asBoolean()).isTrue();
        assertThat(page0.get("items").get(0).get("summary").asText()).contains("created \"other\"");
        assertThat(page0.get("items").get(1).get("summary").asText()).contains("IN_PROGRESS → DONE");

        JsonNode forTask = api().get("/api/boards/" + board + "/activity?taskId=" + id, owner.token()).expect(200);
        assertThat(forTask.get("items")).extracting(n -> n.get("type").asText())
                .containsExactly("TASK_MOVED", "TASK_MOVED", "TASK_CREATED");
    }

    @Test
    void notificationsForAssignmentAndChangesButNotForOwnActions() {
        User owner = newUser("owner");
        User alice = newUser("alice");
        long board = newBoard(owner, Map.of(alice, "EDITOR"));
        api().post("/api/notifications/read-all", alice.token(), null).expect(200); // clear "added to board"

        JsonNode t = api().post("/api/boards/" + board + "/tasks", owner.token(),
                Map.of("title", "review PR", "status", "TODO", "assignee", alice.name())).expect(201);
        api().put("/api/boards/" + board + "/tasks/" + t.get("id").asLong() + "/move", owner.token(),
                Map.of("status", "IN_PROGRESS", "version", 0)).expect(200);
        // Alice moving her own task must not notify herself.
        api().put("/api/boards/" + board + "/tasks/" + t.get("id").asLong() + "/move", alice.token(),
                Map.of("status", "DONE", "version", 1)).expect(200);

        assertThat(api().get("/api/notifications/unread-count", alice.token()).expect(200).get("count").asLong()).isEqualTo(2);
        JsonNode list = api().get("/api/notifications?unreadOnly=true", alice.token()).expect(200);
        assertThat(list.get("items")).extracting(n -> n.get("type").asText())
                .containsExactly("ASSIGNED_TASK_CHANGED", "TASK_ASSIGNED");

        long first = list.get("items").get(0).get("id").asLong();
        api().post("/api/notifications/" + first + "/read", alice.token(), null).expect(200);
        assertThat(api().get("/api/notifications/unread-count", alice.token()).expect(200).get("count").asLong()).isEqualTo(1);
        api().post("/api/notifications/read-all", alice.token(), null).expect(200);
        assertThat(api().get("/api/notifications/unread-count", alice.token()).expect(200).get("count").asLong()).isZero();
        assertThat(api().get("/api/notifications/unread-count", owner.token()).expect(200).get("count").asLong()).isZero();
    }

    @Test
    void searchFiltersAndPaginates() {
        User owner = newUser("owner");
        User bob = newUser("bob");
        long board = newBoard(owner, Map.of(bob, "EDITOR"));
        for (int i = 0; i < 25; i++) newTask(board, owner, "Bug " + i);
        api().post("/api/boards/" + board + "/tasks", owner.token(),
                Map.of("title", "Payment flow", "description", "Fix the CHECKOUT redirect", "status", "IN_PROGRESS", "assignee", bob.name())).expect(201);
        api().post("/api/boards/" + board + "/tasks", owner.token(), Map.of("title", "100% coverage", "status", "TODO")).expect(201);

        String base = "/api/boards/" + board + "/tasks";
        // Case-insensitive match on description
        JsonNode r = api().get(base + "?q=checkout", owner.token()).expect(200);
        assertThat(r.get("items")).extracting(n -> n.get("title").asText()).containsExactly("Payment flow");
        // Filters combine
        assertThat(api().get(base + "?status=IN_PROGRESS&assignee=" + bob.name(), owner.token()).expect(200).get("totalItems").asLong()).isEqualTo(1);
        assertThat(api().get(base + "?q=bug&status=DONE", owner.token()).expect(200).get("totalItems").asLong()).isZero();
        // LIKE wildcards in user input are literals, not patterns
        assertThat(api().get(base + "?q=%25", owner.token()).expect(200).get("items"))
                .extracting(n -> n.get("title").asText()).containsExactly("100% coverage");

        // Pagination: 25 "Bug" tasks, 10 per page, stable title ordering, no overlap
        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode p = api().get(base + "?q=bug&sort=title&size=10&page=" + page, owner.token()).expect(200);
            assertThat(p.get("totalItems").asLong()).isEqualTo(25);
            assertThat(p.get("totalPages").asInt()).isEqualTo(3);
            assertThat(p.get("hasNext").asBoolean()).isEqualTo(page < 2);
            p.get("items").forEach(n -> seen.add(n.get("title").asText()));
        }
        assertThat(seen).hasSize(25).doesNotHaveDuplicates();

        // Page size is capped server-side
        assertThat(api().get(base + "?size=100000", owner.token()).expect(200).get("size").asInt()).isEqualTo(200);
    }
}
