package lk.hms.common.api;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint. Never contains stack traces or SQL.
 *
 * @param code machine-readable code the client can branch on (e.g. APPOINTMENT_SLOT_TAKEN)
 */
public record ApiError(int status, String code, String message, List<FieldError> fieldErrors,
                       String requestId, Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String requestId) {
        return new ApiError(status, code, message, null, requestId, Instant.now());
    }
}
