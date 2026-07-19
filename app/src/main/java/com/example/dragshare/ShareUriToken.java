package com.example.dragshare;

import java.util.UUID;

final class ShareUriToken {
    private static final String JPEG_SUFFIX = ".jpg";

    private ShareUriToken() {}

    static String fileName(String token) {
        if (parse(token) == null) {
            throw new IllegalArgumentException("Invalid share token");
        }
        return token + JPEG_SUFFIX;
    }

    static String parse(String pathSegment) {
        if (pathSegment == null) {
            return null;
        }
        String token = pathSegment.endsWith(JPEG_SUFFIX)
                ? pathSegment.substring(0, pathSegment.length() - JPEG_SUFFIX.length())
                : pathSegment;
        try {
            UUID.fromString(token);
            return token;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
