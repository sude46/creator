package com.creator.video.domain;

public record DispatchClaim(Long id, Long videoId, Long generation, String dispatchToken, Long dispatchAttempts) {
    public ProcessingMessage message() { return new ProcessingMessage(1, id, videoId, generation); }
}
