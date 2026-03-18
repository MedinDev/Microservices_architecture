package com.microservices.gateway.config;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
@Configuration
public class SecurityHeaderPropagationFilter {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    private final String internalApiKey;

    public SecurityHeaderPropagationFilter(
        @Value("${app.security.internal.api-key:internal-dev-key-change-me}") String internalApiKey
    ) {
        this.internalApiKey = internalApiKey;
    }

    @Bean
    public GlobalFilter securityHeaderFilter() {
        return (exchange, chain) -> exchange.getPrincipal()
            .flatMap(principal -> {
                ServerHttpRequest.Builder requestBuilder = exchange.getRequest()
                    .mutate()
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey);
                if (principal instanceof Authentication authentication) {
                    enrichUserHeaders(authentication, requestBuilder);
                }
                return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
            })
            .switchIfEmpty(Mono.defer(() -> {
                ServerHttpRequest request = exchange.getRequest()
                    .mutate()
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .build();
                return chain.filter(exchange.mutate().request(request).build());
            }));
    }

    private void enrichUserHeaders(Authentication authentication, ServerHttpRequest.Builder requestBuilder) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            Object userId = jwt.getClaims().get("userId");
            if (userId != null) {
                requestBuilder.header(USER_ID_HEADER, String.valueOf(userId));
            } else if (jwt.getSubject() != null) {
                requestBuilder.header(USER_ID_HEADER, jwt.getSubject());
            }
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String roles = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
        if (!roles.isBlank()) {
            requestBuilder.header(USER_ROLES_HEADER, roles);
        }
    }
}
