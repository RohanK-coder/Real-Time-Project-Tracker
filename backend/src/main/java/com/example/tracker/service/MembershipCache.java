package com.example.tracker.service;

import com.example.tracker.config.CacheConfig;
import com.example.tracker.repository.BoardMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cached "what role does user X have on board Y". Separate bean so the cache proxy applies
 * (Spring caching does not intercept self-invocation). "NONE" is cached too, so probing
 * boards you are not a member of does not hammer the database.
 */
@Component
@RequiredArgsConstructor
public class MembershipCache {
    static final String NONE = "NONE";
    private final BoardMemberRepository memberRepository;

    @Cacheable(cacheNames = CacheConfig.BOARD_ROLES, key = "#boardId + ':' + #username")
    public String roleOf(Long boardId, String username) {
        return memberRepository.findRole(boardId, username).map(Enum::name).orElse(NONE);
    }

    @CacheEvict(cacheNames = CacheConfig.BOARD_ROLES, key = "#boardId + ':' + #username")
    public void evict(Long boardId, String username) {
    }
}
