package lk.hms.modules.notifications;

import lk.hms.common.api.PageResponse;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;

/** In-app notifications (docs/01 G10). Created inside the business transaction that causes them. */
@Service
public class NotificationService {

    public record NotificationResponse(Long id, String category, String severity, String title, String body,
                                       String linkPath, Instant readAt, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getCategory(), n.getSeverity(), n.getTitle(), n.getBody(),
                    n.getLinkPath(), n.getReadAt(), n.getCreatedAt());
        }
    }

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public void notifyUsers(Collection<Long> userIds, String category, String severity, String title, String body, String link) {
        userIds.stream().distinct().forEach(id -> {
            Notification n = new Notification();
            n.setUserId(id);
            n.setCategory(category);
            n.setSeverity(severity);
            n.setTitle(title.length() > 150 ? title.substring(0, 150) : title);
            n.setBody(body == null || body.length() <= 500 ? body : body.substring(0, 500));
            n.setLinkPath(link);
            repo.save(n);
        });
    }

    /** Everyone currently able to act on it, e.g. all users with pharmacy:dispense. */
    @Transactional
    public void notifyPermission(String permission, String category, String severity, String title, String body, String link) {
        notifyUsers(repo.activeUserIdsWithPermission(permission), category, severity, title, body, link);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(boolean unreadOnly, int page, int size) {
        return PageResponse.of(repo.forUser(me(), unreadOnly, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repo.countByUserIdAndReadAtIsNull(me());
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = repo.findById(id).filter(x -> x.getUserId().equals(me()))
                .orElseThrow(() -> BusinessException.notFound("Notification", id));   // others' notifications look absent
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now(clock));
        }
    }

    @Transactional
    public int markAllRead() {
        return repo.markAllRead(me(), Instant.now(clock));
    }

    private static Long me() {
        return CurrentUser.require().getId();
    }
}
