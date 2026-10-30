package com.hms.common;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public AppException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public static AppException notFound(String message) { return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    public static AppException conflict(String message) { return new AppException(HttpStatus.CONFLICT, "CONFLICT", message); }
    public static AppException business(String message) { return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", message); }
}
