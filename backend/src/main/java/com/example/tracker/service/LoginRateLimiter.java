package com.example.tracker.service;

import com.example.tracker.exception.RateLimitedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Fixed-window limiter on login attempts per (username, client IP), stored in Redis so the limit
 * holds across instances. Fails open if Redis is unavailable: availability over strictness here.
 */
@Component
@Slf4j
public class LoginRateLimiter {
    private final StringRedisTemplate redis;
    private final int maxPerMinute;

    public LoginRateLimiter(StringRedisTemplate redis, @Value("${app.rate-limit.login-per-minute:10}") int maxPerMinute) {
        this.redis = redis;
        this.maxPerMinute = maxPerMinute;
    }

    public void check(String username, String clientIp) {
        if (maxPerMinute <= 0) return;
        String key = "tracker:rl:login:" + username.toLowerCase(Locale.ROOT) + ":" + clientIp;
        try {
            Long attempts = redis.opsForValue().increment(key);
            if (attempts != null && attempts == 1) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (attempts != null && attempts > maxPerMinute) {
                throw new RateLimitedException("Too many login attempts. Try again in a minute.");
            }
        } catch (RateLimitedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Login rate limiter unavailable, allowing request: {}", ex.getMessage());
        }
    }
}
