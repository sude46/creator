package com.creator.common.web;

import com.creator.common.ErrorCode;
import org.springframework.http.HttpStatus;

public final class ErrorHttpMapping {
    private ErrorHttpMapping() {
    }

    public static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case INVALID_USERNAME, WEAK_PASSWORD, INVALID_REQUEST, VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case USERNAME_TAKEN, ADMIN_BOOTSTRAP_CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHENTICATED, SESSION_INVALID, LOGIN_FAILED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, CSRF_INVALID -> HttpStatus.FORBIDDEN;
            case AUTH_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
