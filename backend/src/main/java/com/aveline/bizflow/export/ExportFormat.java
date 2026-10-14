package com.aveline.bizflow.export;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public enum ExportFormat {
    CSV,
    XLSX,
    PDF;

    public static ExportFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CSV;
        }
        try {
            return ExportFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("EXPORT_FORMAT", "format", "Choose csv, xlsx, or pdf");
        }
    }

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MediaType mediaType() {
        return switch (this) {
            case CSV -> new MediaType("text", "csv", StandardCharsets.UTF_8);
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> MediaType.APPLICATION_PDF;
        };
    }
}
