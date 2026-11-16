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
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class UploadAdmissionFilter extends OncePerRequestFilter {
    private final UploadAdmission admission;
    private final SecurityResponseWriter writer;
    public UploadAdmissionFilter(UploadAdmission admission, SecurityResponseWriter writer) {
        this.admission = admission;
        this.writer = writer;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).matches("/api/creator/videos/[0-9]+/source");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Actor actor = (Actor) request.getAttribute("currentActor");
        if (actor == null) { writer.error(response, ErrorCode.UNAUTHENTICATED); return; }
        if (!request.getMethod().equals("PUT")) { writer.error(response, ErrorCode.METHOD_NOT_ALLOWED); return; }
        if (!"video/mp4".equalsIgnoreCase(request.getContentType())) { writer.error(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE); return; }
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
}
