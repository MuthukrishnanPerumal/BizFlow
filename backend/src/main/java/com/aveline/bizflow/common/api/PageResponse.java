package com.aveline.bizflow.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be zero or greater");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be zero or greater");
        }
    }
}
