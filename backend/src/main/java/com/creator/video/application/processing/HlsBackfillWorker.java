package com.creator.video.application.processing;

import com.creator.video.domain.HlsBackfillRepository;
import com.creator.video.domain.HlsVariant;
import com.creator.video.domain.ProcessingFailure;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HlsBackfillWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(HlsBackfillWorker.class);
    private final HlsBackfillTransactions transactions;
    private final ProcessingStorage storage;
    private final MediaProcessor processor;
    private final ProcessingWorkspace workspace;
    private final WorkerProperties properties;
    private final Semaphore permit = new Semaphore(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Thread> active = new AtomicReference<>();

    public HlsBackfillWorker(HlsBackfillTransactions transactions, ProcessingStorage storage,
            MediaProcessor processor, ProcessingWorkspace workspace, WorkerProperties properties) {
        this.transactions = transactions;
        this.storage = storage;
        this.processor = processor;
        this.workspace = workspace;
        this.properties = properties;
    }

    public void runOnce() {
        if (closed.get() || !permit.tryAcquire()) return;
        active.set(Thread.currentThread());
        HlsBackfillRepository.Task task = null;
        Path directory = null;
        var uploaded = new ArrayList<String>();
        boolean committed = false;
        try {
            var claimed = transactions.claim();
            if (claimed.isEmpty()) return;
            task = claimed.get();
            if (task.sourceKey() == null || task.sourceBytes() <= 0) {
                transactions.fail(task, "SOURCE_MISSING");
                return;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.taskSeconds());
            directory = workspace.create();
            var source = directory.resolve("source.mp4");
            var hlsDirectory = directory.resolve("hls");
            storage.download(task.sourceKey(), source, task.sourceBytes(), deadline);
            var variants = processor.processHlsOnly(source, hlsDirectory, deadline);
            if (variants.isEmpty()) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            var published = HlsUploader.upload(task.videoId(), task.generation(), variants, hlsDirectory,
                    storage, deadline, uploaded);
            committed = transactions.succeed(task, published);
        } catch (ProcessingException exception) {
            if (task != null) transactions.fail(task, exception.failure().name());
            LOGGER.warn("HLS backfill attempt failed [videoId={}, failure={}]",
                    task == null ? null : task.videoId(), exception.failure());
        } catch (IOException exception) {
            if (task != null) transactions.fail(task, ProcessingFailure.TEMP_UNAVAILABLE.name());
            LOGGER.warn("HLS backfill workspace failed [videoId={}]", task == null ? null : task.videoId());
        } catch (RuntimeException exception) {
            if (task != null) transactions.fail(task, "INTERNAL");
            LOGGER.warn("HLS backfill attempt failed [videoId={}, failure={}]",
                    task == null ? null : task.videoId(), exception.getClass().getSimpleName());
        } finally {
            if (!committed) HlsUploader.cleanup(storage, uploaded, LOGGER);
            if (directory != null) {
                try { workspace.discard(directory); }
                catch (IOException exception) { LOGGER.warn("HLS backfill workspace cleanup deferred"); }
            }
            active.set(null);
            permit.release();
        }
    }

    public void close() {
        closed.set(true);
        var thread = active.get();
        if (thread != null) thread.interrupt();
    }
}
