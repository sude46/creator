package com.creator.video.domain;

public enum ProcessingFailure {
    INVALID_MEDIA(false, false), UNSUPPORTED_MEDIA(false, true), STORAGE_UNAVAILABLE(true, true),
    PROCESS_FAILED(true, true), PROCESS_TIMEOUT(true, true), TEMP_UNAVAILABLE(true, true), LEASE_EXPIRED(true, true);

    private final boolean retryable;
    private final boolean manuallyRetryable;
    ProcessingFailure(boolean retryable, boolean manuallyRetryable) {
        this.retryable = retryable;
        this.manuallyRetryable = manuallyRetryable;
    }
    public boolean retryable() { return retryable; }
    public boolean manuallyRetryable() { return manuallyRetryable; }
}
