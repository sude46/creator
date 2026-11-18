package com.creator.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JsonRequestSizeFilterTest {
    private final JsonRequestSizeFilter filter = new JsonRequestSizeFilter(new HttpLimitProperties(64), new ObjectMapper());

    @Test
    void acceptsJsonWithinLimitAndExposesCachedBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/creator/drafts");
        request.setContentType("application/json; charset=UTF-8");
        request.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        var forwarded = (HttpServletRequest) chain.getRequest();
        assertEquals("{\"a\":1}", forwarded.getReader().readLine());
    }

    @Test
    void rejectsDeclaredAndChunkedJsonOverLimit() throws Exception {
        var declared = new MockHttpServletRequest("POST", "/api/creator/drafts");
        declared.setContentType("application/json");
        declared.setContent("x".repeat(65).getBytes(StandardCharsets.UTF_8));
        var declaredResponse = new MockHttpServletResponse();
        filter.doFilter(declared, declaredResponse, new MockFilterChain());
        assertEquals(413, declaredResponse.getStatus());
        assertTrue(declaredResponse.getContentAsString().contains("REQUEST_TOO_LARGE"));

        var chunked = new MockHttpServletRequest("POST", "/api/creator/drafts") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        chunked.setContentType("application/json");
        chunked.setContent("x".repeat(65).getBytes(StandardCharsets.UTF_8));
        var chunkedResponse = new MockHttpServletResponse();
        filter.doFilter(chunked, chunkedResponse, new MockFilterChain());
        assertEquals(413, chunkedResponse.getStatus());
        assertTrue(chunkedResponse.getContentAsString().contains("REQUEST_TOO_LARGE"));
    }

    @Test
    void leavesVideoUploadsAndNonApiRequestsAlone() throws Exception {
        var upload = new MockHttpServletRequest("PUT", "/api/creator/videos/1/source");
        upload.setContentType("video/mp4");
        upload.setContent(new byte[1024]);
        var uploadResponse = new MockHttpServletResponse();
        var uploadChain = new MockFilterChain();
        filter.doFilter(upload, uploadResponse, uploadChain);
        assertSame(upload, uploadChain.getRequest());

        var other = new MockHttpServletRequest("POST", "/other");
        other.setContentType("application/json");
        other.setContent(new byte[1024]);
        var otherResponse = new MockHttpServletResponse();
        var otherChain = new MockFilterChain();
        filter.doFilter(other, otherResponse, otherChain);
        assertSame(other, otherChain.getRequest());
    }
}
