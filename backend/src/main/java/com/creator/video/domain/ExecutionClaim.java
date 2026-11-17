package com.creator.video.domain;

public record ExecutionClaim(long taskId, long videoId, long generation, String executionToken,
        String sourceKey, long sourceBytes, String coverKey) { }
