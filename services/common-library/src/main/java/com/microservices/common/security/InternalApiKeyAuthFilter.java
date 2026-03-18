package com.microservices.common.security;

import com.microservices.common.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiKeyAuthFilter extends OncePerRequestFilter {

    private final String internalApiKey;
    private final Set<String> trustedSources;

    public InternalApiKeyAuthFilter(
        @Value("${app.security.internal.api-key:internal-dev-key-change-me}") String internalApiKey,
        @Value("${app.security.internal.trusted-sources:api-gateway,order-service,payment-service,notification-service}") String trustedSources
    ) {
        this.internalApiKey = internalApiKey;
        this.trustedSources = Stream.of(trustedSources.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestSource = request.getHeader(SecurityHeaderConstants.REQUEST_SOURCE_HEADER);
        if (StringUtils.hasText(requestSource) && trustedSources.contains(requestSource)) {
            String apiKey = request.getHeader(SecurityHeaderConstants.INTERNAL_API_KEY_HEADER);
            if (!StringUtils.hasText(apiKey) || !internalApiKey.equals(apiKey)) {
                throw new UnauthorizedException("Invalid internal API key");
            }
        }
        filterChain.doFilter(request, response);
    }
}
