package lk.hms.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.hms.modules.users.entity.Permission;
import lk.hms.modules.users.entity.Role;
import lk.hms.modules.users.entity.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(Long id, String username, String fullName, String email, String status,
                               List<String> roles, boolean mustChangePassword, boolean locked,
                               Instant lastLoginAt, Instant createdAt, long version) {

        public static UserResponse from(User u, Instant now) {
            return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getStatus().name(),
                    u.getRoles().stream().map(Role::getCode).sorted().toList(), u.isMustChangePassword(),
                    u.isLockedAt(now), u.getLastLoginAt(), u.getCreatedAt(), u.getVersion());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use letters, numbers, dot, underscore or hyphen only.")
            String username,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @NotEmpty(message = "Assign at least one role.") Set<@NotBlank String> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @NotEmpty(message = "Assign at least one role.") Set<@NotBlank String> roles,
            @NotNull Long version) {
    }

    /** Returned once; the plain temporary password is never stored or retrievable again. */
    public record TemporaryPasswordResponse(UserResponse user, String temporaryPassword) {
    }

    public record RoleResponse(Long id, String code, String name, String description, List<String> permissions) {
        public static RoleResponse from(Role r) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                    r.getPermissions().stream().map(Permission::getCode).sorted().toList());
        }
    }
}
