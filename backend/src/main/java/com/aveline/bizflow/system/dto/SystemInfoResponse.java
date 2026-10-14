package com.aveline.bizflow.system.dto;

import java.time.Instant;

public record SystemInfoResponse(
        String name,
        String version,
        String environment,
        String apiVersion,
        Instant serverTime
) {
}
