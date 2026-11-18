package lk.hms.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Login activity log: every success and failure, kept for security review. */
@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 40)
    private String failureReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
}
