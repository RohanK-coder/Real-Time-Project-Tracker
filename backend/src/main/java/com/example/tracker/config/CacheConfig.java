package com.example.tracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    public static final String BOARD_ROLES = "board-roles";

    /**
     * Board membership is checked on every REST call and every WebSocket SUBSCRIBE, so it is cached in
     * Redis (shared by all instances) and explicitly evicted whenever membership changes.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(@Value("${app.cache.board-roles-ttl:10m}") Duration ttl) {
        return builder -> builder.withCacheConfiguration(BOARD_ROLES, RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("tracker:")
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string())));
    }

    /** If Redis is unavailable, log and fall through to the database instead of failing the request. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed for {}::{} - falling back to database: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}::{}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                // An eviction failure could leave a stale grant for up to the TTL; surface loudly.
                log.error("Cache evict failed for {}::{}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("Cache clear failed for {}: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
