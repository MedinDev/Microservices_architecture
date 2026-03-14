package com.microservices.common.security;

import com.microservices.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtValidationUtil {

    private final String jwtSecret;

    public JwtValidationUtil(@Value("${security.jwt.secret:change-me-for-dev-only-change-me-for-dev-only}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Claims validateAndExtract(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        String token = bearerToken.substring(7);
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid JWT token");
        }
    }

    public boolean hasRole(Claims claims, String role) {
        Object rolesClaim = claims.get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return false;
        }
        return roles.stream().map(String::valueOf).anyMatch(r -> r.equalsIgnoreCase(role));
    }

    public Long getUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(userId));
    }

    public List<String> getRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
