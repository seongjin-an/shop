package com.ansj.shopuser.user.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_PREFIX = "refresh:";

    public void save(Long userId, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + userId, refreshToken, Duration.ofMillis(ttlMillis));
    }

    public String get(Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
    }

    public void delete(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }
}
