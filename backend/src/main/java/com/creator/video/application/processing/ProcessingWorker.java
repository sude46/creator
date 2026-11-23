package com.creator.video.application.processing;

import com.creator.video.domain.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingWorker implements AutoCloseable {
    public enum Decision { ACK, REJECT }
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorker.class);
    private final ExecutionTransactions transactions;
    private final ProcessingStorage storage;
    private final MediaProcessor processor;
    private final ProcessingWorkspace workspace;
    private final WorkerProperties properties;
    private final Semaphore permit = new Semaphore(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Thread> active = new AtomicReference<>();

    public ProcessingWorker(ExecutionTransactions transactions, ProcessingStorage storage, MediaProcessor processor,
            ProcessingWorkspace workspace, WorkerProperties properties) {
        this.transactions = transactions;
        this.storage = storage;
        this.processor = processor;
        this.workspace = workspace;
        this.properties = properties;
    }

    public Decision consume(ProcessingMessage message) {
        if (!permit.tryAcquire()) throw new IllegalStateException("Worker concurrency exceeded");
        Path directory = null;
        try {
            active.set(Thread.currentThread());
            if (closed.get()) throw new IllegalStateException("Worker is stopping");
            var claimed = transactions.claim(message);
            if (claimed.isEmpty()) return Decision.ACK;
            var claim = claimed.get();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.taskSeconds());
            try {
                directory = workspace.create();
                var source = directory.resolve("source.mp4");
                var cover = directory.resolve("cover.jpg");
                var hlsDirectory = directory.resolve("hls");
                storage.download(claim.sourceKey(), source, claim.sourceBytes(), deadline);
                var processed = processor.processWithHls(source, cover, hlsDirectory, deadline);
                var metadata = processed.metadata();
                requireTime(deadline);
                long coverBytes = Files.size(cover);
                if (coverBytes < 1 || coverBytes > 1048576) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
                storage.uploadCover(claim.coverKey(), cover, coverBytes, deadline);
                requireTime(deadline);
                var uploadedHls = new ArrayList<String>();
                boolean committed = false;
                try {
                    var variants = uploadHls(claim, processed.variants(), hlsDirectory, deadline, uploadedHls);
                    committed = transactions.succeed(claim, metadata, coverBytes, variants);
                } finally {
                    if (!committed) cleanupHls(uploadedHls);
                }
            } catch (ProcessingException exception) {
                transactions.fail(claim, exception.failure());
                LOGGER.warn("Processing attempt failed [taskId={}, failure={}]", claim.taskId(), exception.failure());
            } catch (IOException exception) {
                transactions.fail(claim, ProcessingFailure.TEMP_UNAVAILABLE);
            }
            return Decision.ACK;
        } catch (ExecutionTransactions.InvalidProcessingMessage exception) {
            return Decision.REJECT;
        } finally {
            if (directory != null) {
                try { workspace.discard(directory); }
                catch (IOException exception) { LOGGER.warn("Processing workspace cleanup deferred"); }
            }
            active.set(null);
            permit.release();
        }
    }

    private List<HlsVariant> uploadHls(ExecutionClaim claim, List<ProcessedMedia.HlsVariant> processed, Path root,
            long deadline, List<String> uploaded) throws IOException {
        var variants = new ArrayList<HlsVariant>();
        for (var variant : processed) {
            var directory = root.resolve(variant.name());
            var baseKey = "videos/" + claim.videoId() + "/" + claim.generation() + "/hls/" + variant.name();
            var playlist = directory.resolve("index.m3u8");
            var playlistKey = baseKey + "/index.m3u8";
            storage.uploadHls(playlistKey, playlist, Files.size(playlist), "application/vnd.apple.mpegurl", deadline);
            uploaded.add(playlistKey);
            var segmentKeys = new ArrayList<String>();
            try (var files = Files.list(directory)) {
                for (var segment : files.filter(path -> path.getFileName().toString().endsWith(".ts")).sorted().toList()) {
                    requireTime(deadline);
                    String key = baseKey + "/" + segment.getFileName();
                    storage.uploadHls(key, segment, Files.size(segment), "video/mp2t", deadline);
                    uploaded.add(key);
                    segmentKeys.add(key);
                }
            }
            if (segmentKeys.isEmpty()) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            variants.add(new HlsVariant(variant.name(), variant.bandwidth(), variant.width(), variant.height(),
                    baseKey, playlistKey, variant.playlist(), segmentKeys));
        }
        return variants;
    }

    private void cleanupHls(List<String> keys) {
        for (String key : keys) {
            try { storage.delete(key); }
            catch (RuntimeException exception) { LOGGER.warn("HLS cleanup deferred [key={}, failure={}]", key, exception.getClass().getSimpleName()); }
        }
    }

    @Override
    public void close() {
        closed.set(true);
        var thread = active.get();
        if (thread != null) thread.interrupt();
    }

    public static void requireTime(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
            throw new ProcessingException(ProcessingFailure.PROCESS_TIMEOUT);
        }
    }
}
