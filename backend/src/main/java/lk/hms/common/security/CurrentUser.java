package lk.hms.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the authenticated principal for services (audit, record-level checks). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<HmsUserPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HmsUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static HmsUserPrincipal require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }
}
