package com.hms.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiError> app(AppException ex, HttpServletRequest request) {
        return response(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of(), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please correct the highlighted fields.", fields, request);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> mismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "One or more request parameters are invalid.", List.of(), request);
    }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> credentials(BadCredentialsException ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.", List.of(), request);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", List.of(), request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "This change conflicts with existing data.", List.of(), request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", List.of(), request);
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                               List<ApiError.FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, fields, request.getRequestURI()));
    }
}
