package com.creator.video.application.processing;

import com.creator.video.domain.ProcessingFailure;

public class ProcessingException extends RuntimeException {
    private final ProcessingFailure failure;
    public ProcessingException(ProcessingFailure failure) {
        super(failure.name());
        this.failure = failure;
    }
    public ProcessingFailure failure() { return failure; }
}
