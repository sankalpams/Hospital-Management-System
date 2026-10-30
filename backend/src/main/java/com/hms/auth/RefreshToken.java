package com.hms.auth;

import com.hms.common.BaseEntity;
import com.hms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "refresh_tokens") @Getter @Setter @NoArgsConstructor
public class RefreshToken extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "replaced_by_id") private UUID replacedById;
    public boolean isUsable(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
}
