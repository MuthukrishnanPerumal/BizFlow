package com.aveline.bizflow.common.exception;

import com.aveline.bizflow.common.api.ApiResponse;
import com.aveline.bizflow.common.api.FieldErrorDetail;
import com.aveline.bizflow.notification.AppNotificationService;
import com.aveline.bizflow.security.AuthMessages;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final AppNotificationService notifications;

    public GlobalExceptionHandler(AppNotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDetail(
                    fieldError.getField(),
                    fieldError.getCode(),
                    safeValidationMessage(fieldError.getDefaultMessage())));
        }
        for (ObjectError objectError : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new FieldErrorDetail(
                    null,
                    objectError.getCode(),
                    safeValidationMessage(objectError.getDefaultMessage())));
        }
        log.warn("Validation failed fieldCount={}", errors.size());
        return ResponseEntity.badRequest().body(ApiResponse.fail("Validation failed", errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("Malformed request body");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("The request body is malformed or unreadable", List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (body instanceof ApiResponse<?>) {
            return new ResponseEntity<>(body, headers, status);
        }
        if (status.is5xxServerError()) {
            log.error("Request failed with status {}", status.value(), ex);
        } else {
            log.warn("Request rejected with status {} ({})", status.value(), ex.getClass().getSimpleName());
        }
        String message = clientMessage(status, ex);
        return new ResponseEntity<>(ApiResponse.fail(message, List.of()), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        log.warn("Constraint violation count={}", errors.size());
        return ResponseEntity.badRequest().body(ApiResponse.fail("Validation failed", errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        failed("CONFLICT", ex.getMessage());
        log.warn("Conflict");
        List<FieldErrorDetail> errors = ex.getField() == null
                ? List.of()
                : List.of(new FieldErrorDetail(ex.getField(), "duplicate", ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), errors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        failed("DATA", "The request conflicts with existing data");
        log.warn("Data integrity violation ({})", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("The request conflicts with existing data", List.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        failed("LOCK", "The record was updated by someone else. Refresh and try again.");
        log.warn("Optimistic lock failure");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("The record was updated by someone else. Refresh and try again.", List.of()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        failed(ex.getCode(), ex.getMessage());
        log.warn("Business rule rejected code={}", ex.getCode());
        FieldErrorDetail detail = new FieldErrorDetail(ex.getField(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ex.getMessage(), List.of(detail)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(AuthMessages.publicMessage(ex.getMessage()), List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("You do not have permission to perform this action", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("An unexpected error occurred", List.of()));
    }

    private FieldErrorDetail toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
        String field = path;
        if (path != null) {
            int separator = path.lastIndexOf('.');
            if (separator >= 0 && separator < path.length() - 1) {
                field = path.substring(separator + 1);
            }
        }
        String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return new FieldErrorDetail(field, code, safeValidationMessage(violation.getMessage()));
    }

    private String clientMessage(HttpStatusCode status, Exception ex) {
        if (ex instanceof NoResourceFoundException || status.value() == HttpStatus.NOT_FOUND.value()) {
            return "The requested resource was not found";
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return "The requested method is not supported";
        }
        if (status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return "The request media type is not supported";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "The request body is malformed or unreadable";
        }
        return "Request could not be completed";
    }

    private void failed(String code, String message) {
        try {
            notifications.failedTransaction(code, message);
        } catch (RuntimeException ex) {
            log.warn("Failed-transaction notification was not recorded");
        }
    }

    private String safeValidationMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Invalid value";
        }
        return message;
    }
}
