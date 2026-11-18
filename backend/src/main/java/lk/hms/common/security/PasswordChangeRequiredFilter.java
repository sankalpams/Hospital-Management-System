package lk.hms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Users holding a temporary password may only identify themselves, change the password or
 * sign out. Everything else is rejected with 403 PASSWORD_CHANGE_REQUIRED (server-enforced,
 * not just a client redirect).
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout", "/api/v1/auth/csrf");

    private final JsonSecurityHandlers handlers;

    public PasswordChangeRequiredFilter(JsonSecurityHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean blocked = CurrentUser.get().map(HmsUserPrincipal::isMustChangePassword).orElse(false)
                && !ALLOWED.contains(request.getRequestURI());
        if (blocked) {
            handlers.write(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your temporary password before continuing.");
            return;
        }
        chain.doFilter(request, response);
    }
}
