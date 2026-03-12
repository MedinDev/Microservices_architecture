package com.microservices.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            String clientIp = exchange.getRequest().getRemoteAddress() == null
                ? "unknown-client"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just(clientIp);
        };
    }
}
