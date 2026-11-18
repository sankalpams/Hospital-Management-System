package lk.hms.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.hms.common.security.HmsUserPrincipal;

import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your username.") @Size(max = 50) String username,
            @NotBlank(message = "Enter your password.") @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password.") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Enter a new password.") @Size(max = 128) String newPassword) {
    }

    public record CurrentUserResponse(Long id, String username, String fullName, Set<String> roles,
                                      Set<String> permissions, boolean mustChangePassword, Long doctorId) {

        /** {@code doctorId}: the doctor profile linked to this login, or null (lets the UI offer "my patients" only when it exists). */
        public static CurrentUserResponse from(HmsUserPrincipal p, Long doctorId) {
            return new CurrentUserResponse(p.getId(), p.getUsername(), p.getFullName(), p.getRoles(),
                    p.getPermissions(), p.isMustChangePassword(), doctorId);
        }
    }
}
