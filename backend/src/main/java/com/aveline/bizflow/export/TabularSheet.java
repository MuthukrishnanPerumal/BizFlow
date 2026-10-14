package com.aveline.bizflow.export;

import java.util.List;

public record TabularSheet(String title, List<String> headers, List<List<String>> rows) {

    public TabularSheet {
        title = title == null || title.isBlank() ? "Export" : title.trim();
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("headers are required");
        }
    }
}
