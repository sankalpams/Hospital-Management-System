package com.hms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp, int status, String code, String message,
                       List<FieldError> fieldErrors, String path) {
    public record FieldError(String field, String message) {}
}
