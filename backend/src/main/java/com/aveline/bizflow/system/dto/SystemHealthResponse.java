package com.aveline.bizflow.system.dto;

public record SystemHealthResponse(
        String status,
        String database,
        int appliedMigrations
) {
}
