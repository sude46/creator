package com.creator.identity.infrastructure.security;

import com.creator.common.ApiError;
import com.creator.common.ErrorCode;
import com.creator.common.web.ErrorHttpMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class SecurityResponseWriter {
    private final ObjectMapper json;

    public SecurityResponseWriter(ObjectMapper json) {
        this.json = json;
    }

    public void error(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(ErrorHttpMapping.status(code).value());
        write(response, new ApiError(code.name(), code.getMessage()));
    }

    public void write(HttpServletResponse response, Object body) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }
}
