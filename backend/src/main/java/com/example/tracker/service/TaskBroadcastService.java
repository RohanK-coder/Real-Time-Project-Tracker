package com.example.tracker.service;

import com.example.tracker.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskBroadcastService {
    private static final String CHANNEL = "task-updates";
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void publishTaskUpdate(Task task) {
        try {
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.convertAndSend(CHANNEL, json);
            messagingTemplate.convertAndSend("/topic/tasks", task);
        } catch (Exception ex) {
            log.warn("Could not publish to redis, sending websocket only", ex);
            messagingTemplate.convertAndSend("/topic/tasks", task);
        }
    }

    public void publishTaskDeleted(Long id) {
        messagingTemplate.convertAndSend("/topic/tasks/deleted", id);
    }
}
