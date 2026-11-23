package com.creator.video.web;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.identity.infrastructure.security.SecurityResponseWriter;
import com.creator.video.application.UploadAdmission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.creator.common.web.RequestPaths;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

public class UploadAdmissionFilter extends OncePerRequestFilter {
    private static final String ATTEMPT_ID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern SOURCE_PATH = Pattern.compile("/api/creator/videos/[0-9]+/source");
    private static final Pattern SESSION_START = Pattern.compile("/api/creator/videos/[0-9]+/source/uploads");
    private static final Pattern SESSION_CHUNK = Pattern.compile("/api/creator/videos/[0-9]+/source/uploads/" + ATTEMPT_ID + "/chunks/[0-9]+");
    private static final Pattern SESSION_COMPLETE = Pattern.compile("/api/creator/videos/[0-9]+/source/uploads/" + ATTEMPT_ID + "/complete");
    private final UploadAdmission admission;
    private final SecurityResponseWriter writer;
    public UploadAdmissionFilter(UploadAdmission admission, SecurityResponseWriter writer) {
        this.admission = admission;
        this.writer = writer;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return access(RequestPaths.decoded(request)) == null;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Access access = access(RequestPaths.decoded(request));
        if (access == null) { chain.doFilter(request, response); return; }
        Actor actor = (Actor) request.getAttribute("currentActor");
        if (actor == null) { writer.error(response, ErrorCode.UNAUTHENTICATED); return; }
        if (!access.method.equals(request.getMethod())) { writer.error(response, ErrorCode.METHOD_NOT_ALLOWED); return; }
        if (access.contentType != null && !access.contentType.equalsIgnoreCase(mediaType(request.getContentType()))) {
            writer.error(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE); return;
        }
        if (request.getHeader("X-CSRF-TOKEN") == null || request.getHeader("X-CSRF-TOKEN").isBlank()) {
            writer.error(response, ErrorCode.CSRF_INVALID); return;
        }
        UploadAdmission.Lease lease;
        try {
            lease = admission.acquire(actor.id(), request.getContentLengthLong());
        } catch (BusinessException exception) {
            if (exception.getCode() == ErrorCode.UPLOAD_BUSY) response.setHeader("Retry-After", "60");
            writer.error(response, exception.getCode()); return;
        } catch (IOException exception) {
            writer.error(response, ErrorCode.SERVICE_UNAVAILABLE); return;
        }
        try (lease) { chain.doFilter(request, response); }
    }

    private Access access(String path) {
        if (SOURCE_PATH.matcher(path).matches()) return new Access("PUT", "video/mp4");
        if (SESSION_START.matcher(path).matches()) return new Access("POST", "application/json");
        if (SESSION_CHUNK.matcher(path).matches()) return new Access("PUT", "application/octet-stream");
        if (SESSION_COMPLETE.matcher(path).matches()) return new Access("POST", null);
        return null;
    }

    private String mediaType(String contentType) {
        if (contentType == null) return null;
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim();
    }

    private record Access(String method, String contentType) { }
}
