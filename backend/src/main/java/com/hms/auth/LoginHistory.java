package com.hms.auth;

import com.hms.common.BaseEntity;
import com.hms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "login_history") @Getter @Setter @NoArgsConstructor
public class LoginHistory extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @Column(name = "username_attempted", nullable = false) private String usernameAttempted;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LoginOutcome outcome;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
}
