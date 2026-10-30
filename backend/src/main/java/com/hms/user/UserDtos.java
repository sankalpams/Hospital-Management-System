package com.hms.user;

import com.hms.role.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}
    public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,80}$", message = "Username must be 3–80 letters, digits, dots, underscores, or hyphens") String username,
        @NotBlank @Email String email, @NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use a Sri Lankan phone number in +94XXXXXXXXX format") String phone,
        @NotNull RoleCode role, String temporaryPassword) {}
    public record UpdateUserRequest(@NotBlank @Email String email, @NotBlank @Size(max = 100) String firstName,
                                    @NotBlank @Size(max = 100) String lastName,
                                    @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Use a Sri Lankan phone number in +94XXXXXXXXX format") String phone,
                                    RoleCode role, Boolean active) {}
    public record UserResponse(UUID id, String username, String email, String firstName, String lastName,
                               String phone, RoleCode role, boolean active, boolean forcePasswordChange,
                               Instant lastLoginAt, Instant createdAt, long version) {}
}
