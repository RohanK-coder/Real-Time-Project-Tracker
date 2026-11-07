package com.example.tracker.it;

import com.example.tracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Board-level authorization on the REST API. */
class RestAuthorizationIT extends IntegrationTest {

    @Test
    void unauthenticatedAndForgedTokensAreRejectedWith401() {
        api().get("/api/boards", null).expect(401);
        api().get("/api/boards", "not-a-jwt").expect(401);
        // Signed with a different key
        api().get("/api/boards", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.c2lnbmF0dXJl").expect(401);
    }

    @Test
    void selfRegistrationCannotGrantAdmin() {
        String name = uniq("mallory");
        var res = api().post("/api/auth/register", null, Map.of("username", name, "email", name + "@x.dev",
                "password", "password123", "role", "ROLE_ADMIN")).expect(201);
        assertThat(res.get("role").asText()).isEqualTo("ROLE_MEMBER");
    }

    @Test
    void nonMembersCannotSeeOrTouchABoard() {
        User owner = newUser("owner");
        User outsider = newUser("outsider");
        long board = newBoard(owner, Map.of());
        long task = newTask(board, owner, "secret").get("id").asLong();
        String t = outsider.token();

        // 404 rather than 403: outsiders can't even confirm the board exists.
        api().get("/api/boards/" + board, t).expect(404);
        api().get("/api/boards/" + board + "/tasks", t).expect(404);
        api().get("/api/boards/" + board + "/tasks/" + task, t).expect(404);
        api().get("/api/boards/" + board + "/activity", t).expect(404);
        api().get("/api/boards/" + board + "/changes?sinceSeq=0", t).expect(404);
        api().post("/api/boards/" + board + "/tasks", t, Map.of("title", "x", "status", "TODO")).expect(404);
        api().put("/api/boards/" + board + "/tasks/" + task + "/move", t, Map.of("status", "DONE", "version", 0)).expect(404);
        api().delete("/api/boards/" + board + "/tasks/" + task, t).expect(404);
        api().post("/api/boards/" + board + "/members", t, Map.of("username", outsider.name(), "role", "OWNER")).expect(404);

        assertThat(api().get("/api/boards", t).expect(200).get("totalItems").asLong()).isZero();
    }

    @Test
    void viewersReadButCannotWrite() {
        User owner = newUser("owner");
        User viewer = newUser("viewer");
        long board = newBoard(owner, Map.of(viewer, "VIEWER"));
        long task = newTask(board, owner, "t").get("id").asLong();
        String t = viewer.token();

        api().get("/api/boards/" + board + "/tasks", t).expect(200);
        api().get("/api/boards/" + board + "/activity", t).expect(200);
        api().post("/api/boards/" + board + "/tasks", t, Map.of("title", "x", "status", "TODO")).expect(403);
        api().put("/api/boards/" + board + "/tasks/" + task, t, Map.of("title", "x", "status", "TODO", "version", 0)).expect(403);
        api().put("/api/boards/" + board + "/tasks/" + task + "/move", t, Map.of("status", "DONE", "version", 0)).expect(403);
        api().delete("/api/boards/" + board + "/tasks/" + task, t).expect(403);
    }

    @Test
    void editorsWriteTasksButOnlyOwnersManageMembers() {
        User owner = newUser("owner");
        User editor = newUser("editor");
        User other = newUser("other");
        long board = newBoard(owner, Map.of(editor, "EDITOR"));

        newTask(board, editor, "by editor");
        api().post("/api/boards/" + board + "/members", editor.token(), Map.of("username", other.name(), "role", "EDITOR")).expect(403);
        api().put("/api/boards/" + board + "/members/" + editor.name(), editor.token(), Map.of("role", "OWNER")).expect(403);
        api().delete("/api/boards/" + board, editor.token()).expect(403);
    }

    @Test
    void removedMemberLosesAccessImmediately() {
        User owner = newUser("owner");
        User editor = newUser("editor");
        long board = newBoard(owner, Map.of(editor, "EDITOR"));
        api().get("/api/boards/" + board, editor.token()).expect(200); // warms the membership cache

        api().delete("/api/boards/" + board + "/members/" + editor.name(), owner.token()).expect(204);

        // The cached role must have been evicted, not served stale for the TTL.
        api().get("/api/boards/" + board, editor.token()).expect(404);
        api().post("/api/boards/" + board + "/tasks", editor.token(), Map.of("title", "x", "status", "TODO")).expect(404);
    }

    @Test
    void demotionTakesEffectImmediately() {
        User owner = newUser("owner");
        User editor = newUser("editor");
        long board = newBoard(owner, Map.of(editor, "EDITOR"));
        newTask(board, editor, "allowed");

        api().put("/api/boards/" + board + "/members/" + editor.name(), owner.token(), Map.of("role", "VIEWER")).expect(200);

        api().post("/api/boards/" + board + "/tasks", editor.token(), Map.of("title", "x", "status", "TODO")).expect(403);
    }

    @Test
    void lastOwnerCannotBeRemovedOrDemoted() {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        api().delete("/api/boards/" + board + "/members/" + owner.name(), owner.token()).expect(400);
        api().put("/api/boards/" + board + "/members/" + owner.name(), owner.token(), Map.of("role", "VIEWER")).expect(400);
    }

    @Test
    void assigneeMustBeABoardMember() {
        User owner = newUser("owner");
        User outsider = newUser("outsider");
        long board = newBoard(owner, Map.of());
        api().post("/api/boards/" + board + "/tasks", owner.token(),
                Map.of("title", "x", "status", "TODO", "assignee", outsider.name())).expect(400);
    }

    @Test
    void usersCannotReadOrAckOthersNotifications() {
        User owner = newUser("owner");
        User alice = newUser("alice");
        User bob = newUser("bob");
        newBoard(owner, Map.of(alice, "EDITOR")); // alice gets ADDED_TO_BOARD
        long id = api().get("/api/notifications", alice.token()).expect(200).get("items").get(0).get("id").asLong();

        api().post("/api/notifications/" + id + "/read", bob.token(), null).expect(404);
        assertThat(api().get("/api/notifications", bob.token()).expect(200).get("totalItems").asLong()).isZero();
    }

    @Test
    void validationErrorsAreProblemDetails() {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        var body = api().post("/api/boards/" + board + "/tasks", owner.token(), Map.of("title", "", "status", "TODO")).expect(400);
        assertThat(body.get("errors").has("title")).isTrue();
        api().post("/api/boards/" + board + "/tasks", owner.token(), Map.of("title", "x", "status", "NOPE")).expect(400);
    }
}
