package com.ansj.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
        "/user/api/users/login",
        "/user/api/users/signup",
        "/user/api/users/validate-username",
        "/user/api/users/reissue",
        "/user/api/users/logout"
    );

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final SecretKey secretKey = Keys.hmacShaKeyFor(
        "my-secret-key-my-secret-key-my-secret-key".getBytes(StandardCharsets.UTF_8));

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (Exception e) {
            return unauthorized(exchange);
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        return redisTemplate.hasKey(BLACKLIST_PREFIX + token)
            .doOnError(e -> log.warn("[JwtAuthFilter] Redis blacklist 조회 실패: {}", e.getMessage()))
            .onErrorReturn(false)
            .flatMap(isBlacklisted -> {
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    log.debug("[JwtAuthFilter] 블랙리스트 토큰 차단 - userId={}", userId);
                    return unauthorized(exchange);
                }
                ServerWebExchange mutated = exchange.mutate()
                    .request(builder -> builder
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build())
                    .build();
                return chain.filter(mutated);
            });
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
