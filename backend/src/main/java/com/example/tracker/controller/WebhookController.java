package com.example.tracker.controller;

import com.example.tracker.dto.GithubIssueWebhookPayload;
import com.example.tracker.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final TaskService taskService;

    @PostMapping("/github")
    public ResponseEntity<String> handleGithubIssue(@RequestBody GithubIssueWebhookPayload payload) {
        if ("opened".equalsIgnoreCase(payload.getAction()) && payload.getIssue() != null) {
            taskService.createFromGithubIssue(
                    payload.getIssue().getTitle(),
                    payload.getIssue().getBody(),
                    payload.getIssue().getHtml_url(),
                    payload.getIssue().getUser() != null ? payload.getIssue().getUser().getLogin() : "github"
            );
        }
        return ResponseEntity.ok("Webhook processed");
    }
}
