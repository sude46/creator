package com.creator.video.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UploadAdmissionFilterTest {
    private final UploadAdmissionFilter filter = new UploadAdmissionFilter(null, null);

    @Test
    void encodedServletPathCannotBypassAdmissionMatching() {
        var request = new MockHttpServletRequest("PUT", "/api/creator/videos/%31/source");
        request.setServletPath("/api/creator/videos/1/source");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void pathInfoIsUsedWhenServletPathIsEmpty() {
        var request = new MockHttpServletRequest("PUT", "/api/creator/videos/%31/source");
        request.setServletPath("");
        request.setPathInfo("/api/creator/videos/1/source");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void rawUriFallbackIsDecodedBeforeMatching() {
        var request = new MockHttpServletRequest("PUT", "/app/api/creator/videos/%31/source");
        request.setContextPath("/app");
        request.setServletPath("");
        request.setPathInfo(null);

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void onlyTheSourceUploadPathIsGuarded() {
        var source = new MockHttpServletRequest("PUT", "/api/creator/videos/42/source");
        source.setServletPath("/api/creator/videos/42/source");
        assertFalse(filter.shouldNotFilter(source));

        var status = new MockHttpServletRequest("GET", "/api/creator/videos/42/upload");
        status.setServletPath("/api/creator/videos/42/upload");
        assertTrue(filter.shouldNotFilter(status));

        var nested = new MockHttpServletRequest("PUT", "/api/creator/videos/42/source/extra");
        nested.setServletPath("/api/creator/videos/42/source/extra");
        assertTrue(filter.shouldNotFilter(nested));
    }
}
