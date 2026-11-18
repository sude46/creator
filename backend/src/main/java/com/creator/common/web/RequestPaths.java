package com.creator.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

/** Resolves a request path from the container's decoded servlet mapping. */
public final class RequestPaths {
    private RequestPaths() {
    }

    public static String decoded(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = request.getPathInfo();
        if (path != null && !path.isEmpty()) return path;
        String raw = request.getRequestURI().substring(request.getContextPath().length());
        try {
            return UriUtils.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return raw;
        }
    }
}
