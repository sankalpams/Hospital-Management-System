package lk.hms.modules.auth.repository;

import lk.hms.modules.auth.entity.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("""
            select count(a) from LoginAttempt a
            where a.userId = :userId and a.success = false
              and a.failureReason = 'BAD_CREDENTIALS' and a.attemptedAt > :since
            """)
    long countRecentFailures(Long userId, Instant since);

    /** Every failed sign-in from one address (any username, including unknown ones) since a moment. */
    @Query("select count(a) from LoginAttempt a where a.ipAddress = :ip and a.success = false and a.attemptedAt > :since")
    long countRecentFailuresFromIp(String ip, Instant since);

    @Query("""
            select a from LoginAttempt a
            where (:q is null or a.username like :q or a.ipAddress like :q)
              and (:success is null or a.success = :success)
              and a.attemptedAt >= :from and a.attemptedAt < :to
            """)
    Page<LoginAttempt> search(String q, Boolean success, Instant from, Instant to, Pageable pageable);
}
