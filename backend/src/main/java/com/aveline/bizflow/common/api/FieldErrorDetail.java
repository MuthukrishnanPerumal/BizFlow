package com.aveline.bizflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorDetail(String field, String code, String message) {
}
