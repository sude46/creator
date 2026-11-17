package com.creator.video.domain;

public enum ProcessingFailure {
    INVALID_MEDIA(false), UNSUPPORTED_MEDIA(false), STORAGE_UNAVAILABLE(true), PROCESS_FAILED(true),
    PROCESS_TIMEOUT(true), TEMP_UNAVAILABLE(true), LEASE_EXPIRED(true);

    private final boolean retryable;
    ProcessingFailure(boolean retryable) { this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
