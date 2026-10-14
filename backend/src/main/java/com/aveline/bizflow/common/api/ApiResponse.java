package com.aveline.bizflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<FieldErrorDetail> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return ok("Operation completed successfully", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static ApiResponse<Void> fail(String message, List<FieldErrorDetail> errors) {
        List<FieldErrorDetail> safeErrors = errors == null ? List.of() : List.copyOf(errors);
        return new ApiResponse<>(false, message, null, safeErrors, Instant.now());
    }
}
