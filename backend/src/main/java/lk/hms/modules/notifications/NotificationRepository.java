package lk.hms.modules.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.userId = :userId and (:unreadOnly = false or n.readAt is null) order by n.id desc")
    Page<Notification> forUser(Long userId, boolean unreadOnly, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);

    /** Active users holding a permission (recipients of role-wide alerts such as low stock). */
    @Query("""
            select distinct u.id from User u join u.roles r join r.permissions p
            where p.code = :permission and u.status = lk.hms.modules.users.entity.UserStatus.ACTIVE
            """)
    List<Long> activeUserIdsWithPermission(String permission);
}
