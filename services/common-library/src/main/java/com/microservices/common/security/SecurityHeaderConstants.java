package com.microservices.common.security;

public final class SecurityHeaderConstants {

    public static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private SecurityHeaderConstants() {
    }
}
