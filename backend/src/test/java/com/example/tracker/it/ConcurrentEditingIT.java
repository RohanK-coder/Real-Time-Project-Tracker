package com.example.tracker.it;

import com.example.tracker.support.Api;
import com.example.tracker.support.IntegrationTest;
import com.example.tracker.support.StompClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several users editing the same board at the same time. These run real parallel HTTP requests
 * against the running server (Postgres + Redis in containers), not mocks.
 */
class ConcurrentEditingIT extends IntegrationTest {

    @Test
    void twoUsersSavingTheSameVersion_oneWins_otherGets409WithCurrentState() throws Exception {
        User owner = newUser("owner");
        User alice = newUser("alice");
        User bob = newUser("bob");
        long board = newBoard(owner, Map.of(alice, "EDITOR", bob, "EDITOR"));
        JsonNode task = newTask(board, owner, "original");
        String path = "/api/boards/" + board + "/tasks/" + task.get("id").asLong();
        long version = task.get("version").asLong();

        // Both start from the same version and fire at the same moment.
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Api.Response> a = pool.submit(() -> { barrier.await(); return api().put(path, alice.token(),
                Map.of("title", "alice's title", "status", "TODO", "version", version)); });
        Future<Api.Response> b = pool.submit(() -> { barrier.await(); return api().put(path, bob.token(),
                Map.of("title", "bob's title", "status", "IN_PROGRESS", "version", version)); });
        List<Api.Response> results = List.of(a.get(), b.get());
        pool.shutdown();

        assertThat(results).extracting(Api.Response::status).containsExactlyInAnyOrder(200, 409);
        Api.Response winner = results.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        Api.Response loser = results.stream().filter(r -> r.status() == 409).findFirst().orElseThrow();

        // The loser is told exactly what it conflicted with, so the UI can merge instead of clobbering.
        assertThat(loser.body().get("current").get("title").asText()).isEqualTo(winner.body().get("title").asText());
        assertThat(loser.body().get("current").get("version").asLong()).isEqualTo(version + 1);

        JsonNode stored = api().get(path, owner.token()).expect(200);
        assertThat(stored.get("title").asText()).isEqualTo(winner.body().get("title").asText());
        assertThat(stored.get("version").asLong()).isEqualTo(version + 1);
    }

    @Test
    void manyUsersReadModifyWriteWithRetry_noUpdateIsLost() throws Exception {
        int users = 8;
        int editsPerUser = 5;
        User owner = newUser("owner");
        List<User> editors = IntStream.range(0, users).mapToObj(i -> newUser("ed" + i)).toList();
        Map<User, String> roles = new LinkedHashMap<>();
        editors.forEach(e -> roles.put(e, "EDITOR"));
        long board = newBoard(owner, roles);
        String path = "/api/boards/" + board + "/tasks/" + newTask(board, owner, "counter").get("id").asLong();

        // Each user appends its own tokens to the description, retrying on 409 like the real client does.
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> conflicts = new ArrayList<>();
        for (User e : editors) {
            conflicts.add(pool.submit(() -> {
                start.await();
                int seen = 0;
                for (int i = 0; i < editsPerUser; i++) {
                    while (true) {
                        JsonNode cur = api().get(path, e.token()).expect(200);
                        String desc = cur.get("description").isNull() ? "" : cur.get("description").asText();
                        var res = api().put(path, e.token(), Map.of("title", "counter", "status", "TODO",
                                "description", desc + "[" + e.name() + "#" + i + "]",
                                "version", cur.get("version").asLong()));
                        if (res.status() == 200) break;
                        assertThat(res.status()).isEqualTo(409);
                        seen++;
                    }
                }
                return seen;
            }));
        }
        start.countDown();
        int totalConflicts = 0;
        for (Future<Integer> f : conflicts) totalConflicts += f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        JsonNode finalTask = api().get(path, owner.token()).expect(200);
        String desc = finalTask.get("description").asText();
        for (User e : editors) {
            for (int i = 0; i < editsPerUser; i++) {
                assertThat(desc).as("edit %s#%d was lost", e.name(), i).contains("[" + e.name() + "#" + i + "]");
            }
        }
        assertThat(finalTask.get("version").asLong()).isEqualTo((long) users * editsPerUser);
        // Contention really happened: otherwise this test proves nothing.
        assertThat(totalConflicts).isPositive();

        // Every successful write left exactly one activity entry.
        long updates = api().get("/api/boards/" + board + "/activity?taskId=" + finalTask.get("id").asLong() + "&size=1",
                owner.token()).expect(200).get("totalItems").asLong();
        assertThat(updates).isEqualTo(1L + users * editsPerUser); // + TASK_CREATED
    }

    @Test
    void parallelWritersProduceAGapFreeEventStream_everyEventExactlyOnce() throws Exception {
        int writers = 4;
        int tasksEach = 10;
        User owner = newUser("owner");
        User watcher = newUser("watcher");
        List<User> editors = IntStream.range(0, writers).mapToObj(i -> newUser("w" + i)).toList();
        Map<User, String> roles = new LinkedHashMap<>();
        roles.put(watcher, "VIEWER");
        editors.forEach(e -> roles.put(e, "EDITOR"));
        long board = newBoard(owner, roles);
        String topic = "/topic/boards/" + board;
        long startSeq = api().get("/api/boards/" + board, owner.token()).expect(200).get("eventSeq").asLong();

        try (StompClient client = stomp(watcher.token()).connected()) {
            client.subscribe(topic);
            awaitSubscribed(watcher.name(), topic);

            ExecutorService pool = Executors.newFixedThreadPool(writers);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            Set<Long> created = ConcurrentHashMap.newKeySet();
            for (User e : editors) {
                futures.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < tasksEach; i++) created.add(newTask(board, e, e.name() + "-" + i).get("id").asLong());
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
            pool.shutdown();

            int total = writers * tasksEach;
            List<Long> seqs = new ArrayList<>();
            Set<Long> received = new HashSet<>();
            for (int i = 0; i < total; i++) {
                JsonNode ev = client.next(topic, Duration.ofSeconds(5));
                seqs.add(ev.get("seq").asLong());
                received.add(ev.get("taskId").asLong());
            }

            // Every event delivered exactly once, with contiguous seqs: nothing missing, no duplicates.
            // Arrival order is NOT guaranteed (two requests that commit back-to-back publish
            // independently); clients restore order from seq, see tasksSlice.js.
            List<Long> expected = LongStreamRange(startSeq + 1, startSeq + total);
            assertThat(seqs).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
            assertThat(received).isEqualTo(created);
            assertThat(client.poll(topic, Duration.ofMillis(300))).as("no extra events").isNull();
        }
    }

    @Test
    void moveRacingADelete_neverResurrectsTheTask() throws Exception {
        User owner = newUser("owner");
        User alice = newUser("alice");
        long board = newBoard(owner, Map.of(alice, "EDITOR"));
        JsonNode task = newTask(board, owner, "contested");
        long id = task.get("id").asLong();
        long v = task.get("version").asLong();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Api.Response> del = pool.submit(() -> { barrier.await();
            return api().delete("/api/boards/" + board + "/tasks/" + id + "?version=" + v, owner.token()); });
        Future<Api.Response> move = pool.submit(() -> { barrier.await();
            return api().put("/api/boards/" + board + "/tasks/" + id + "/move", alice.token(), Map.of("status", "DONE", "version", v)); });
        int delStatus = del.get().status();
        int moveStatus = move.get().status();
        pool.shutdown();

        if (delStatus == 204) {
            assertThat(moveStatus).isIn(404, 409); // 404 if it ran after the delete committed
            api().get("/api/boards/" + board + "/tasks/" + id, owner.token()).expect(404);
        } else {
            assertThat(delStatus).isEqualTo(409);
            assertThat(moveStatus).isEqualTo(200);
            assertThat(api().get("/api/boards/" + board + "/tasks/" + id, owner.token()).expect(200).get("status").asText()).isEqualTo("DONE");
        }
    }

    private static List<Long> LongStreamRange(long fromInclusive, long toInclusive) {
        List<Long> out = new ArrayList<>();
        for (long i = fromInclusive; i <= toInclusive; i++) out.add(i);
        return out;
    }
}
