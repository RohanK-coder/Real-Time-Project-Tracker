package com.example.tracker.controller;

import com.example.tracker.dto.GithubIssueWebhookPayload;
import com.example.tracker.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * GitHub "issues" webhook. Point the repository webhook at /api/webhooks/github/{boardId} with
 * content type application/json and the shared secret from GITHUB_WEBHOOK_SECRET. Requests are
 * accepted only with a valid X-Hub-Signature-256 HMAC over the raw body.
 */
@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class WebhookController {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public WebhookController(TaskService taskService, ObjectMapper objectMapper,
                             @Value("${app.github.webhook-secret:}") String secret) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/github/{boardId}")
    public ResponseEntity<Map<String, Object>> handleGithubIssue(
            @PathVariable Long boardId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestBody byte[] body) throws Exception {
        if (secret.length == 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Webhook secret not configured"));
        }
        if (!validSignature(body, signature)) {
            log.warn("Rejected GitHub webhook for board {}: bad signature", boardId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }
        if (event != null && !"issues".equals(event)) {
            return ResponseEntity.ok(Map.of("ignored", true, "reason", "event " + event));
        }
        var payload = objectMapper.readValue(body, GithubIssueWebhookPayload.class);
        if (!"opened".equalsIgnoreCase(payload.getAction()) || payload.getIssue() == null) {
            return ResponseEntity.ok(Map.of("ignored", true));
        }
        var issue = payload.getIssue();
        var task = taskService.createFromGithubIssue(boardId, issue.getTitle(), issue.getBody(), issue.getHtml_url(),
                issue.getUser() != null ? issue.getUser().getLogin() : "unknown");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("taskId", task.id()));
    }

    private boolean validSignature(byte[] body, String header) throws Exception {
        if (header == null || !header.startsWith("sha256=")) return false;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] expected = mac.doFinal(body);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(header.substring(7));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided); // constant-time compare
    }
}
