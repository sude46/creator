package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.DispatchProperties;
import com.creator.video.application.processing.DispatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DispatchScheduler implements AutoCloseable {
    private final DispatchService service;
    private final DispatchProperties properties;
    private ScheduledExecutorService executor;

    public DispatchScheduler(DispatchService service, DispatchProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("processing-dispatch").factory());
        executor.scheduleWithFixedDelay(service, properties.scanMilliseconds(), properties.scanMilliseconds(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (executor != null) executor.shutdownNow();
    }
}
