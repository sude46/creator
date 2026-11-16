package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.UploadVideo;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadService.class);
    private final UploadTransactions transactions;
    private final UploadStaging staging;
    private final ObjectStorage storage;

    public UploadService(UploadTransactions transactions, UploadStaging staging, ObjectStorage storage) {
        this.transactions = transactions;
        this.staging = staging;
        this.storage = storage;
    }

    public UploadVideo upload(Actor actor, long videoId, long version, InputStream input, long expectedLength) {
        var attempt = transactions.reserve(actor, videoId, version);
        UploadStaging.StagedFile staged = null;
        try {
            staged = staging.receive(input, expectedLength);
            storage.put(attempt.objectKey(), staged.path(), staged.size());
            return transactions.bind(actor, attempt, staged.size(), version);
        } catch (IOException exception) {
            if (exception instanceof java.net.SocketTimeoutException) throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        } finally {
            if (staged != null) {
                try { staging.discard(staged.path()); }
                catch (IOException exception) { LOGGER.warn("Temporary upload cleanup failed; maintenance will retry"); }
            }
        }
    }
}
