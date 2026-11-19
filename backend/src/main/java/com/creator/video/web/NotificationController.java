package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.NotificationService;
import com.creator.video.domain.Notification;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public PageResult<NotificationView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        var result = notifications.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(NotificationView::from).toList(), result.total(), result.page(), result.size());
    }
    @GetMapping("/unread")
    public UnreadView unread(@RequestAttribute("currentActor") Actor actor) { return new UnreadView(notifications.unread(actor)); }
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) { notifications.markRead(actor, id); }
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@RequestAttribute("currentActor") Actor actor) { notifications.markAll(actor); }
    public record UnreadView(long unread) { }
    public record NotificationView(String id, String type, String title, String body, String link,
            LocalDateTime readAt, LocalDateTime createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(Long.toString(n.id()), n.type(), n.title(), n.body(), n.link(), n.readAt(), n.createdAt());
        }
    }
}
