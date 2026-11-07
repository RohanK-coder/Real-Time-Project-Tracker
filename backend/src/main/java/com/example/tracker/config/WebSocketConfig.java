package com.example.tracker.config;

import com.example.tracker.realtime.SessionRegistry;
import com.example.tracker.realtime.StompAuthChannelInterceptor;
import com.example.tracker.realtime.StompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor authInterceptor;
    private final SessionRegistry sessionRegistry;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                // 10s server/client heartbeats: half-open TCP connections are detected and closed,
                // which triggers the client's reconnect + catch-up path.
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket only (no SockJS): every target browser supports it, and it keeps each
        // client on a single TCP connection, so no sticky sessions are needed behind a load balancer.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        registry.setErrorHandler(new StompErrorHandler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessionRegistry)
                .setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(15_000);
    }
}
