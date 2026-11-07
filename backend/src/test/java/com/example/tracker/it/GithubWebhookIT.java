package com.example.tracker.it;

import com.example.tracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.github.webhook-secret=test-webhook-secret")
class GithubWebhookIT extends IntegrationTest {
    private static final String BODY = """
            {"action":"opened","issue":{"title":"Crash on save","body":"stack trace","html_url":"https://github.com/o/r/issues/1","user":{"login":"octocat"}}}""";

    @Test
    void rejectsUnsignedOrBadlySignedPayloads() throws Exception {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        assertThat(post(board, BODY, null)).isEqualTo(401);
        assertThat(post(board, BODY, "sha256=" + "00".repeat(32))).isEqualTo(401);
        assertThat(post(board, BODY, sign(BODY.replace("Crash", "Hijack")))).isEqualTo(401);
        assertThat(api().get("/api/boards/" + board + "/tasks", owner.token()).expect(200).get("totalItems").asLong()).isZero();
    }

    @Test
    void signedIssueOpenedCreatesATask() throws Exception {
        User owner = newUser("owner");
        long board = newBoard(owner, Map.of());
        assertThat(post(board, BODY, sign(BODY))).isEqualTo(201);
        var task = api().get("/api/boards/" + board + "/tasks", owner.token()).expect(200).get("items").get(0);
        assertThat(task.get("title").asText()).isEqualTo("Crash on save");
        assertThat(task.get("createdBy").asText()).isEqualTo("github:octocat");
        assertThat(task.get("githubIssueUrl").asText()).isEqualTo("https://github.com/o/r/issues/1");
    }

    private int post(long board, String body, String signature) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/webhooks/github/" + board))
                .header("Content-Type", "application/json").header("X-GitHub-Event", "issues");
        if (signature != null) req.header("X-Hub-Signature-256", signature);
        return HttpClient.newHttpClient().send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
