package lk.hms.modules.notifications;

import lk.hms.common.api.PageResponse;
import lk.hms.modules.notifications.NotificationService.NotificationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Every signed-in user sees only their own notifications; no extra permission needed. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unread,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.mine(unread, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", service.markAllRead());
    }
}
