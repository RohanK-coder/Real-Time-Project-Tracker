package com.example.tracker.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisClientConfig {
    /**
     * By default Lettuce queues commands while disconnected and only fails them at the command timeout,
     * so every request would stall for seconds during a Redis outage. Rejecting immediately lets the
     * fallbacks (DB for membership, local delivery for events, fail-open rate limiting) kick in at once,
     * while auto-reconnect restores Redis use when it comes back.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastWhenRedisIsDown() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                .build());
    }
}
