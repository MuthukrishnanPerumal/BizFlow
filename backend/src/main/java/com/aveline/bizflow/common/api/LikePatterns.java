package com.aveline.bizflow.common.api;

public final class LikePatterns {

    private LikePatterns() {
    }

    public static String contain(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
