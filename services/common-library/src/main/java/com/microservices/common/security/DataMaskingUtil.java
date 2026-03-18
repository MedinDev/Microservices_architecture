package com.microservices.common.security;

public final class DataMaskingUtil {

    private DataMaskingUtil() {
    }

    public static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        int visible = Math.min(4, value.length() / 3);
        String head = value.substring(0, visible);
        String tail = value.substring(value.length() - visible);
        return head + "*".repeat(value.length() - (visible * 2)) + tail;
    }
}
