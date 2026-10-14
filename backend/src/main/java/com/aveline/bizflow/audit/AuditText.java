package com.aveline.bizflow.audit;

import java.util.regex.Pattern;

public final class AuditText {

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|secret|token|authorization|api[-_]?key|jwt|credential|hash)\\s*[\"']?\\s*[:=]\\s*[\"']?[^\\s,;\"'}]+");
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(?i)\"(password|passwordHash|currentPassword|newPassword|secret|token|accessToken|refreshToken|authorization|apiKey)\"\\s*:\\s*\"(?:\\\\.|[^\"])*\"");

    private AuditText() {
    }

    public static String clean(String value, int maxLength) {
        if (value == null || value.isBlank() || maxLength < 1) {
            return null;
        }
        String cleaned = JSON_FIELD.matcher(value).replaceAll("\"$1\":\"[redacted]\"");
        cleaned = ASSIGNMENT.matcher(cleaned).replaceAll("$1=[redacted]");
        String trimmed = cleaned.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
