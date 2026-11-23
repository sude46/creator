package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.domain.UploadChunk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadStaging {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadStaging.class);
    private static final Pattern ATTEMPT_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern INCOMING = Pattern.compile("incoming-.*\\.tmp");
    private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "mp41", "mp42", "avc1");
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
                requireMp4Header(header);
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

    public StagedFile receiveChunk(InputStream input, long expectedLength) throws IOException {
        if (expectedLength <= 0 || expectedLength > properties.chunkBytes()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA);
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.receiveSeconds()).toNanos();
        Path path = Files.createTempFile(directory, "incoming-", ".tmp");
        active.add(path);
        boolean success = false;
        try {
            long size = 0;
            try (var output = Files.newOutputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    requireTime(deadline);
                    size += count;
                    if (size > expectedLength) throw new BusinessException(ErrorCode.REQUEST_TOO_LARGE);
                    output.write(buffer, 0, count);
                }
            }
            requireTime(deadline);
            if (size != expectedLength) throw new BusinessException(ErrorCode.INVALID_MEDIA);
            success = true;
            return new StagedFile(path, size);
        } finally {
            if (!success) {
                try { discard(path); }
                catch (IOException exception) { LOGGER.warn("Temporary upload cleanup failed; maintenance will retry"); }
            }
        }
    }

    public void storeChunk(String uploadId, int index, Path staged) throws IOException {
        Path target = chunkPath(uploadId, index);
        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            active.remove(staged);
        }
    }

    public StagedFile assemble(String uploadId, List<UploadChunk> chunks, long declaredSize) throws IOException {
        if (chunks.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        for (int index = 0; index < chunks.size(); index++) {
            if (chunks.get(index).chunkIndex() != index) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Path target = Files.createTempFile(directory, "incoming-", ".tmp");
        active.add(target);
        boolean success = false;
        try {
            long total = 0;
            try (var output = Files.newOutputStream(target)) {
                for (UploadChunk chunk : chunks) {
                    Path source = chunkPath(uploadId, chunk.chunkIndex());
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                            || Files.size(source) != chunk.size()) {
                        throw new BusinessException(ErrorCode.INVALID_MEDIA);
                    }
                    Files.copy(source, output);
                    total += chunk.size();
                }
            }
            if (total != declaredSize || declaredSize > properties.maxBytes()) {
                throw new BusinessException(ErrorCode.INVALID_MEDIA);
            }
            requireMp4(target);
            success = true;
            return new StagedFile(target, total);
        } finally {
            if (!success) {
                try { discard(target); }
                catch (IOException exception) { LOGGER.warn("Temporary upload cleanup failed; maintenance will retry"); }
            }
        }
    }

    public void discard(Path path) throws IOException {
        try { Files.deleteIfExists(path); }
        finally { active.remove(path); }
    }

    public void discardUpload(String uploadId) throws IOException {
        String prefix = "upload-" + validId(uploadId) + "-";
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().startsWith(prefix))
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".part"))
                    .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public void cleanAbandoned() throws IOException {
        Instant now = Instant.now();
        Instant staleIncoming = now.minusSeconds(3600);
        Instant staleChunks = now.minusSeconds(7200);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                    .limit(200).toList()) {
                String name = path.getFileName().toString();
                Instant modified = Files.getLastModifiedTime(path).toInstant();
                if (INCOMING.matcher(name).matches() && !active.contains(path) && modified.isBefore(staleIncoming)) {
                    Files.deleteIfExists(path);
                } else if (isChunkFile(name) && modified.isBefore(staleChunks)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Path chunkPath(String uploadId, int index) {
        if (index < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return directory.resolve("upload-" + validId(uploadId) + "-" + index + ".part");
    }

    private String validId(String uploadId) {
        if (uploadId == null || !ATTEMPT_ID.matcher(uploadId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return uploadId;
    }

    private boolean isChunkFile(String name) {
        return name.startsWith("upload-") && name.endsWith(".part")
                && name.substring(7, name.length() - 5).matches("[0-9a-fA-F-]{36}-[0-9]+");
    }

    private void requireMp4(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            requireMp4Header(readHeader(input, Long.MAX_VALUE));
        }
    }

    private void requireMp4Header(byte[] header) {
        if (header.length < 24 || !new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
                || ByteBuffer.wrap(header, 0, 4).getInt() < 16
                || !MP4_BRANDS.contains(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA);
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
