package com.creator.video.application.processing;

import com.creator.video.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.transaction.annotation.Transactional;

public class ExecutionTransactions {
    private final ExecutionRepository repository;
    private final WorkerProperties properties;
    private final Clock clock;

    public ExecutionTransactions(ExecutionRepository repository, WorkerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public Optional<ExecutionClaim> claim(ProcessingMessage message) {
        var video = repository.lockVideo(message.videoId());
        var task = repository.lockTask(message.taskId());
        if (video == null || task == null || task.videoId() != message.videoId() || task.generation() != message.generation()) {
            throw new InvalidProcessingMessage();
        }
        var now = now();
        if (!eligible(video, task) || !video.processingStatus().equals("PENDING") || !task.state().equals("PENDING")
                || task.nextAttemptAt().isAfter(now) || task.executionAttempts() >= properties.maxAttempts()) return Optional.empty();
        var source = repository.source(video.sourceAssetId(), video.id());
        if (source == null || source.sizeBytes() > properties.maxSourceBytes()) {
            repository.finishTask(task.id(), "FAILED", ProcessingFailure.INVALID_MEDIA.name(), now);
            repository.videoState(video.id(), "FAILED");
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        var claim = new ExecutionClaim(task.id(), video.id(), task.generation(), token,
                source.objectKey(), source.sizeBytes(), "covers/" + token + ".jpg");
        repository.start(claim, now.plusSeconds(properties.leaseSeconds()));
        repository.createOutput(claim, now.plusSeconds(properties.leaseSeconds() + 120));
        repository.videoState(video.id(), "PROCESSING");
        return Optional.of(claim);
    }

    @Transactional(timeout = 5)
    public boolean succeed(ExecutionClaim claim, MediaMetadata metadata, long coverBytes) {
        return succeed(claim, metadata, coverBytes, java.util.List.of());
    }

    public boolean succeed(ExecutionClaim claim, MediaMetadata metadata, long coverBytes, java.util.List<com.creator.video.domain.HlsVariant> hls) {
        var video = repository.lockVideo(claim.videoId());
        var task = repository.lockTask(claim.taskId());
        var now = now();
        if (!current(video, task, claim, now)) return false;
        var output = repository.lockOutput(claim.executionToken());
        if (output == null || output.taskId() != claim.taskId() || !output.objectKey().equals(claim.coverKey())
                || !output.state().equals("STAGING") || !output.expiresAt().isAfter(now)) return false;
        if (coverBytes < 1 || coverBytes > 1048576) throw new IllegalArgumentException("Invalid cover size");
        repository.bind(claim, metadata, coverBytes, hls);
        repository.finishTask(task.id(), "SUCCEEDED", null, now);
        repository.outputState(claim.executionToken(), "BOUND", now);
        return true;
    }

    @Transactional(timeout = 5)
    public void fail(ExecutionClaim claim, ProcessingFailure failure) {
        var video = repository.lockVideo(claim.videoId());
        var task = repository.lockTask(claim.taskId());
        var now = now();
        if (current(video, task, claim, now)) failed(video, task, failure, now);
    }

    @Transactional(timeout = 5)
    public void recover(ProcessingMessage message) {
        var video = repository.lockVideo(message.videoId());
        var task = repository.lockTask(message.taskId());
        var now = now();
        if (task == null || video == null) return;
        if (!eligible(video, task)) {
            if (task.state().equals("RUNNING") || task.state().equals("RETRY_WAIT") || task.state().equals("PENDING")) {
                repository.finishTask(task.id(), "CANCELLED", null, now);
            }
            return;
        }
        if (task.state().equals("RUNNING") && !task.executionLeaseUntil().isAfter(now)) {
            failed(video, task, ProcessingFailure.LEASE_EXPIRED, now);
        } else if (task.state().equals("RETRY_WAIT") && !task.nextAttemptAt().isAfter(now)) {
            repository.finishTask(task.id(), "PENDING", null, now);
            repository.videoState(video.id(), "PENDING");
        }
    }

    @Transactional(timeout = 5)
    public Optional<ExecutionRepository.Output> claimCleanup(String token) {
        var output = repository.lockOutput(token);
        var now = now();
        if (output == null || output.expiresAt().isAfter(now)
                || !(output.state().equals("STAGING") || output.state().equals("CLEANING"))) return Optional.empty();
        if (repository.referenced(output.objectKey())) {
            repository.outputState(token, "BOUND", now);
            return Optional.empty();
        }
        repository.outputState(token, "CLEANING", now.plusSeconds(120));
        return Optional.of(output);
    }

    @Transactional(timeout = 5)
    public void cleaned(String token) {
        var output = repository.lockOutput(token);
        if (output != null && output.state().equals("CLEANING")) repository.outputState(token, "DELETED", now());
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }

    private boolean eligible(ExecutionRepository.Video video, ExecutionRepository.Task task) {
        return video != null && task != null && video.processingGeneration() == task.generation()
                && video.sourceAssetId() != null && video.deletedAt() == null && !video.moderationBlocked()
                && (video.processingStatus().equals("PENDING") || video.processingStatus().equals("PROCESSING"))
                && repository.activeOwner(video.ownerId());
    }

    private boolean current(ExecutionRepository.Video video, ExecutionRepository.Task task, ExecutionClaim claim, LocalDateTime now) {
        return eligible(video, task) && task.videoId() == video.id() && video.processingStatus().equals("PROCESSING")
                && task.state().equals("RUNNING") && task.generation() == claim.generation()
                && claim.executionToken().equals(task.executionToken()) && task.executionLeaseUntil().isAfter(now);
    }

    private void failed(ExecutionRepository.Video video, ExecutionRepository.Task task, ProcessingFailure failure, LocalDateTime now) {
        boolean retry = failure.retryable() && task.executionAttempts() < properties.maxAttempts();
        long delay = Math.min(120, 10L << Math.min(task.executionAttempts(), 3)) + ThreadLocalRandom.current().nextInt(6);
        repository.finishTask(task.id(), retry ? "RETRY_WAIT" : "FAILED", failure.name(), now.plusSeconds(delay));
        repository.videoState(video.id(), retry ? "PENDING" : "FAILED");
    }

    public static class InvalidProcessingMessage extends RuntimeException { }
}
