package com.ansj.shopuser.user.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class BlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    public void save(String accessToken) {
        long remainingMillis = jwtProvider.getRemainingMillis(accessToken);
        if (remainingMillis <= 0) return;
        redisTemplate.opsForValue().set(
            BLACKLIST_PREFIX + accessToken, "1", Duration.ofMillis(remainingMillis));
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}
