package lk.hms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A rule violation the user can understand and act on. The message is shown to the user,
 * so it must not leak internals. Use the static factories for the common HTTP mappings.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException notFound(String entity, Object id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " " + id + " was not found.");
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
