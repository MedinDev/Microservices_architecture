package com.microservices.gateway.config;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

@Configuration
public class CorrelationIdGatewayFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Bean
    public GlobalFilter correlationIdFilter() {
        return (exchange, chain) -> {
            List<String> correlationIdHeaders = exchange.getRequest().getHeaders().get(CORRELATION_ID_HEADER);
            String correlationId = correlationIdHeaders == null || correlationIdHeaders.isEmpty()
                ? null
                : correlationIdHeaders.getFirst();
            if (!StringUtils.hasText(correlationId)) {
                correlationId = UUID.randomUUID().toString();
            }
            String finalCorrelationId = correlationId;
            ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, finalCorrelationId))
                .build();
            exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}
