package com.hms.user;

import com.hms.common.BaseEntity;
import com.hms.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users") @Getter @Setter @NoArgsConstructor
public class User extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    private String phone;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "role_id", nullable = false) private Role role;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "force_password_change", nullable = false) private boolean forcePasswordChange = true;
    @Column(name = "failed_login_attempts", nullable = false) private short failedLoginAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    public String displayName() { return firstName + " " + lastName; }
    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }
}
