package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.UploadAttempt;
import com.creator.video.domain.UploadAttemptState;
import com.creator.video.domain.UploadSession;
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
            LOGGER.warn("Upload staging failed [videoId={}, failure={}]", videoId, exception.getClass().getSimpleName());
            if (exception instanceof java.net.SocketTimeoutException) throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        } finally {
            if (staged != null) {
                try { staging.discard(staged.path()); }
                catch (IOException exception) { LOGGER.warn("Temporary upload cleanup failed; maintenance will retry"); }
            }
        }
    }

    public UploadSession start(Actor actor, long videoId, long version, long size, String fingerprint) {
        if (size <= 0) throw new BusinessException(ErrorCode.INVALID_MEDIA);
        if (size > propertiesMaxBytes()) throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        var attempt = transactions.reserveChunked(actor, videoId, version, size, fingerprint);
        return transactions.session(actor, videoId, attempt.id());
    }

    public UploadSession chunk(Actor actor, long videoId, String uploadId, int index, InputStream input, long length) {
        var session = transactions.session(actor, videoId, uploadId);
        int expected = transactions.expectedSize(session.attempt(), index);
        if (length != expected) {
            throw new BusinessException(length >= 0 && length > expected
                    ? ErrorCode.REQUEST_TOO_LARGE : ErrorCode.INVALID_MEDIA);
        }
        UploadStaging.StagedFile staged = null;
        try {
            staged = staging.receiveChunk(input, expected);
            staging.storeChunk(uploadId, index, staged.path());
            staged = null;
            return transactions.recordChunk(actor, videoId, uploadId, index, expected);
        } catch (IOException exception) {
            LOGGER.warn("Chunk staging failed [uploadId={}, index={}, failure={}]", uploadId, index,
                    exception.getClass().getSimpleName());
            if (exception instanceof java.net.SocketTimeoutException) throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        } finally {
            if (staged != null) {
                try { staging.discard(staged.path()); }
                catch (IOException exception) { LOGGER.warn("Temporary chunk cleanup failed; maintenance will retry"); }
            }
        }
    }

    public UploadVideo complete(Actor actor, long videoId, String uploadId, long version) {
        var attempt = transactions.attempt(actor, videoId, uploadId);
        var current = transactions.status(actor, videoId);
        if (attempt.state() == UploadAttemptState.BOUND || current.sourceAssetId() != null) {
            discardUpload(uploadId);
            return current;
        }
        if (attempt.state() != UploadAttemptState.UPLOADING) throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
        var session = transactions.session(actor, videoId, uploadId);
        if (!session.complete()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        UploadStaging.StagedFile assembled = null;
        boolean bound = false;
        try {
            assembled = staging.assemble(uploadId, session.chunks(), attempt.declaredSize());
            storage.put(attempt.objectKey(), assembled.path(), assembled.size());
            var video = transactions.bindChunked(actor, attempt, assembled.size(), version);
            bound = true;
            return video;
        } catch (IOException exception) {
            LOGGER.warn("Chunk assembly failed [uploadId={}, failure={}]", uploadId, exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        } finally {
            if (assembled != null) {
                try { staging.discard(assembled.path()); }
                catch (IOException exception) { LOGGER.warn("Temporary assembly cleanup failed; maintenance will retry"); }
            }
            if (bound) discardUpload(uploadId);
        }
    }

    private void discardUpload(String uploadId) {
        try { staging.discardUpload(uploadId); }
        catch (IOException exception) { LOGGER.warn("Temporary chunk cleanup failed; maintenance will retry"); }
    }

    private long propertiesMaxBytes() { return transactions.maxBytes(); }
}
