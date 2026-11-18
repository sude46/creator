package com.creator.common.web;

import com.creator.common.ApiError;
import com.creator.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds JSON request bodies before Jackson can allocate an unbounded object graph.
 * Raw video uploads use their own admission filter and are not matched here.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.LOWEST_PRECEDENCE)
public class JsonRequestSizeFilter extends OncePerRequestFilter {
    private final HttpLimitProperties properties;
    private final ObjectMapper json;

    public JsonRequestSizeFilter(HttpLimitProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!isJson(request.getContentType())) return true;
        return !RequestPaths.decoded(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > properties.maxJsonBytes()) {
            tooLarge(response);
            return;
        }
        byte[] body = readBounded(request);
        if (body == null) {
            tooLarge(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private byte[] readBounded(HttpServletRequest request) throws IOException {
        try (var input = request.getInputStream()) {
            var output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > properties.maxJsonBytes()) return null;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(),
                new ApiError(ErrorCode.REQUEST_TOO_LARGE.name(), ErrorCode.REQUEST_TOO_LARGE.getMessage()));
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        int separator = contentType.indexOf(';');
        String mediaType = (separator < 0 ? contentType : contentType.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/json") || mediaType.endsWith("+json");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() { return input.read(); }

                @Override
                public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }

                @Override
                public boolean isFinished() { return input.available() == 0; }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setReadListener(ReadListener listener) { }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset charset;
            try {
                charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            } catch (IllegalArgumentException exception) {
                charset = StandardCharsets.UTF_8;
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() { return body.length; }

        @Override
        public long getContentLengthLong() { return body.length; }
    }
}
