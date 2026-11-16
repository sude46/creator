package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadStaging {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadStaging.class);
    private final UploadProperties properties;
    private final Path directory;
    private final Set<Path> active = ConcurrentHashMap.newKeySet();

    public UploadStaging(UploadProperties properties) throws IOException {
        this.properties = properties;
        directory = Path.of(properties.tempDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
    }

    public long usableSpace() throws IOException { return Files.getFileStore(directory).getUsableSpace(); }

    public StagedFile receive(InputStream input, long expectedLength) throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.receiveSeconds()).toNanos();
        Path path = Files.createTempFile(directory, "incoming-", ".tmp");
        active.add(path);
        boolean success = false;
        try {
            StagedFile result;
            try (var output = Files.newOutputStream(path)) {
                byte[] header = readHeader(input, deadline);
                requireTime(deadline);
                if (header.length < 24 || !new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
                        || ByteBuffer.wrap(header, 0, 4).getInt() < 16
                        || !Set.of("isom", "iso2", "mp41", "mp42", "avc1").contains(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
                    throw new BusinessException(ErrorCode.INVALID_MEDIA);
                }
                long size = header.length;
                output.write(header);
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    requireTime(deadline);
                    size += count;
                    if (size > properties.maxBytes()) throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
                    output.write(buffer, 0, count);
                }
                requireTime(deadline);
                if (expectedLength >= 0 && size != expectedLength) throw new BusinessException(ErrorCode.INVALID_MEDIA);
                result = new StagedFile(path, size);
            }
            success = true;
            return result;
        } finally {
            if (!success) {
                try { discard(path); }
                catch (IOException exception) { LOGGER.warn("Temporary upload cleanup failed; maintenance will retry"); }
            }
        }
    }

    public void discard(Path path) throws IOException {
        try { Files.deleteIfExists(path); }
        finally { active.remove(path); }
    }

    public void cleanAbandoned() throws IOException {
        Instant cutoff = Instant.now().minusSeconds(3600);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().matches("incoming-.*\\.tmp"))
                    .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).limit(100).toList()) {
                if (!active.contains(path) && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) Files.deleteIfExists(path);
            }
        }
    }

    private void requireTime(long deadline) {
        if (System.nanoTime() > deadline) throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
    }

    private byte[] readHeader(InputStream input, long deadline) throws IOException {
        byte[] header = new byte[32];
        int received = 0;
        while (received < header.length) {
            requireTime(deadline);
            int count = input.read(header, received, header.length - received);
            requireTime(deadline);
            if (count < 0) break;
            received += count;
        }
        return java.util.Arrays.copyOf(header, received);
    }

    public record StagedFile(Path path, long size) { }
}
