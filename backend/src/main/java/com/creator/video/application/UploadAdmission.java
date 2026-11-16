package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class UploadAdmission {
    private final UploadProperties properties;
    private final UploadStaging staging;
    private final Set<Long> owners = new HashSet<>();

    public UploadAdmission(UploadProperties properties, UploadStaging staging) {
        this.properties = properties;
        this.staging = staging;
    }

    public synchronized Lease acquire(long ownerId, long contentLength) throws IOException {
        if (contentLength > properties.maxBytes()) throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        if (owners.size() >= properties.maxConcurrent() || owners.contains(ownerId)) throw new BusinessException(ErrorCode.UPLOAD_BUSY);
        long reserved = (owners.size() + 1L) * properties.maxBytes();
        if (staging.usableSpace() - reserved < properties.minFreeBytes()) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        owners.add(ownerId);
        return new Lease(ownerId);
    }

    public final class Lease implements AutoCloseable {
        private final long ownerId;
        private boolean closed;
        private Lease(long ownerId) { this.ownerId = ownerId; }
        @Override
        public void close() {
            synchronized (UploadAdmission.this) {
                if (!closed) owners.remove(ownerId);
                closed = true;
            }
        }
    }
}
