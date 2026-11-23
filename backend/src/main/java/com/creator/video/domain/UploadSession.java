package com.creator.video.domain;

import java.util.List;

public record UploadSession(UploadAttempt attempt, List<UploadChunk> chunks) {
    public long receivedBytes() {
        return chunks.stream().mapToLong(UploadChunk::size).sum();
    }

    public boolean complete() {
        return attempt.chunked() && chunks.size() == attempt.chunkCount();
    }
}
