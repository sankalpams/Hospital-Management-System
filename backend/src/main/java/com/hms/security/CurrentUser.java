package com.hms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}
    public static Optional<HmsPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof HmsPrincipal p ? Optional.of(p) : Optional.empty();
    }
    public static Optional<UUID> id() { return principal().map(HmsPrincipal::id); }
    public static Optional<String> role() { return principal().map(HmsPrincipal::role); }
}
