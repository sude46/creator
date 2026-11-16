package com.creator.common;

import java.util.Map;
import org.slf4j.MDC;

public record ApiError(String code, String message, Map<String, String> fieldErrors, String requestId) {
    public ApiError(String code, String message) {
        this(code, message, Map.of(), MDC.get("requestId"));
    }

    public ApiError(String code, String message, Map<String, String> fieldErrors) {
        this(code, message, fieldErrors, MDC.get("requestId"));
    }
}
