package com.creator.identity.web;

import com.creator.identity.application.DeviceSessionService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.DeviceSession;
import com.creator.identity.infrastructure.security.HttpDeviceSessions;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/sessions")
public class DeviceSessionController {
    private final DeviceSessionService sessions;
    private final HttpDeviceSessions devices;

    public DeviceSessionController(DeviceSessionService sessions, HttpDeviceSessions devices) {
        this.sessions = sessions;
        this.devices = devices;
    }

    @GetMapping
    public SessionListView list(@RequestAttribute("currentActor") Actor actor, HttpServletRequest request) {
        String current = devices.currentId(request);
        return new SessionListView(sessions.list(actor.id()).stream()
                .map(session -> SessionView.from(session, session.id().equals(current))).toList());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestAttribute("currentActor") Actor actor, @PathVariable String id) {
        sessions.revoke(actor.id(), id);
    }

    @DeleteMapping("/others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@RequestAttribute("currentActor") Actor actor, HttpServletRequest request) {
        sessions.revokeOthers(actor.id(), devices.currentId(request));
    }

    @DeleteMapping("/all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAll(@RequestAttribute("currentActor") Actor actor) {
        sessions.revokeAll(actor.id());
    }

    public record SessionListView(List<SessionView> items) { }
    public record SessionView(String id, boolean current, String ip, String userAgent, LocalDateTime createdAt,
            LocalDateTime lastSeenAt, LocalDateTime expiresAt) {
        static SessionView from(DeviceSession session, boolean current) {
            return new SessionView(session.id(), current, session.ip(), session.userAgent(),
                    session.createdAt(), session.lastSeenAt(), session.expiresAt());
        }
    }
}
