package com.creator.video;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.application.UploadAdmission;
import com.creator.video.application.UploadProperties;
import com.creator.video.application.UploadStaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class UploadResourceTest {
    @TempDir Path directory;

    @Test
    void diskBudgetRejectsBeforeReadingOrCreatingTemporaryFiles() throws Exception {
        var properties = properties(Long.MAX_VALUE, 120);
        var staging = new UploadStaging(properties);
        var admission = new UploadAdmission(properties, staging);
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> admission.acquire(1, -1)).getCode());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test
    void receiveDeadlineCleansTemporaryFileAndAllowsLaterUpload() throws Exception {
        var staging = new UploadStaging(properties(0, 1));
        var slow = new java.io.FilterInputStream(new ByteArrayInputStream(UploadIntegrationTest.mp4(1024))) {
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                try { Thread.sleep(1100); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                return super.read(buffer, offset, length);
            }
        };
        assertEquals(ErrorCode.UPLOAD_TIMEOUT, assertThrows(BusinessException.class, () -> staging.receive(slow, -1)).getCode());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        var good = staging.receive(new ByteArrayInputStream(UploadIntegrationTest.mp4(1024)), 1024);
        staging.discard(good.path());
    }

    @Test
    void abandonedCleanupKeepsActiveRecentAndUnrelatedFiles() throws IOException {
        var staging = new UploadStaging(properties(0, 120));
        var active = staging.receive(new ByteArrayInputStream(UploadIntegrationTest.mp4(1024)), 1024);
        var old = Files.writeString(directory.resolve("incoming-crashed.tmp"), "stale");
        var unrelated = Files.writeString(directory.resolve("keep.txt"), "keep");
        var recent = Files.writeString(directory.resolve("incoming-recent.tmp"), "recent");
        var ancient = FileTime.from(Instant.now().minusSeconds(7200));
        Files.setLastModifiedTime(old, ancient);
        Files.setLastModifiedTime(active.path(), ancient);
        staging.cleanAbandoned();
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(active.path()));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(recent));
        staging.discard(active.path());
    }

    private UploadProperties properties(long freeBytes, int receiveSeconds) {
        return new UploadProperties(2, 1024, directory.toString(), freeBytes, receiveSeconds, 2048, 2, 900);
    }
}
