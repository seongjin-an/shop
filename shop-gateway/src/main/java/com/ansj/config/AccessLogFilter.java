package com.ansj.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        String userId  = request.getHeaders().getFirst("X-User-Id");
        String method  = request.getMethod().name();
        String path    = request.getURI().getPath();

        log.info("[GATEWAY] → {} {} traceId={} userId={}", method, path, traceId, userId);

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - start;
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = (status != null) ? status.value() : 0;
                    log.info("[GATEWAY] ← {} {} status={} {}ms traceId={}", method, path, statusCode, duration, traceId);
                });
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
