package com.example.tracker.config;

import com.example.tracker.realtime.RealtimeFanout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RealtimeConfig {
    @Bean
    @ConditionalOnProperty(name = "app.realtime.redis-fanout", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer realtimeListenerContainer(RedisConnectionFactory factory, RealtimeFanout fanout) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(fanout, new ChannelTopic(RealtimeFanout.CHANNEL));
        return container;
    }
}
