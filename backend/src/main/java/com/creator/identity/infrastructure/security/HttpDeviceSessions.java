package com.creator.identity.infrastructure.security;

import com.creator.identity.application.DeviceSessionService;
import com.creator.identity.domain.DeviceSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class HttpDeviceSessions implements DeviceSessionRegistrar, DeviceSessionGuard {
    private static final String SESSION_ATTRIBUTE = "creator.deviceSession.id";
    private static final String REQUEST_ATTRIBUTE = "creator.deviceSession.id";
    private final DeviceSessionService sessions;

    public HttpDeviceSessions(DeviceSessionService sessions) { this.sessions = sessions; }

    @Override
    public void register(long userId, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String previous = attribute(session);
        DeviceSession created = sessions.register(userId, session.getId(),
                request.getHeader("User-Agent"), request.getRemoteAddr());
        if (previous != null && !previous.equals(created.id())) sessions.logout(previous);
        session.setAttribute(SESSION_ATTRIBUTE, created.id());
        request.setAttribute(REQUEST_ATTRIBUTE, created.id());
    }

    @Override
    public boolean validate(long userId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String id = session == null ? null : attribute(session);
        if (id == null || !sessions.validate(userId, id, session.getId())) return false;
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        return true;
    }

    public String currentId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof String id) return id;
        HttpSession session = request.getSession(false);
        return session == null ? null : attribute(session);
    }

    public void logout(HttpServletRequest request) {
        String id = currentId(request);
        sessions.logout(id);
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(SESSION_ATTRIBUTE);
        request.removeAttribute(REQUEST_ATTRIBUTE);
    }

    private String attribute(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof String id ? id : null;
    }
}
