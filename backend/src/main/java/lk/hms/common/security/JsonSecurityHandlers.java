package lk.hms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lk.hms.common.api.ApiError;
import lk.hms.common.web.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Security failures answered in the standard {@link ApiError} JSON shape instead of HTML/redirects. */
@Component
public class JsonSecurityHandlers {

    private final ObjectMapper objectMapper;

    public JsonSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: distinguishes an expired session (client shows "session expired") from no session at all. */
    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
            write(response, HttpStatus.UNAUTHORIZED,
                    expired ? "SESSION_EXPIRED" : "UNAUTHENTICATED",
                    expired ? "Your session has expired. Please sign in again." : "Please sign in.");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                        "Your security token is missing or out of date. Reload the page and try again.");
            } else {
                write(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to perform this action.");
            }
        };
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), code, message, RequestIdFilter.current()));
    }
}
