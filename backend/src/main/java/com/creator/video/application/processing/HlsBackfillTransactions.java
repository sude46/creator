package com.creator.video.application.processing;

import com.creator.video.domain.HlsBackfillRepository;
import com.creator.video.domain.HlsVariant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class HlsBackfillTransactions {
    private final HlsBackfillRepository repository;
    private final WorkerProperties properties;
    private final Clock clock;

    public HlsBackfillTransactions(HlsBackfillRepository repository, WorkerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public Optional<HlsBackfillRepository.Task> claim() {
        var now = LocalDateTime.now(clock);
        var task = repository.lockNext(now).orElse(null);
        if (task == null) return Optional.empty();
        String token = UUID.randomUUID().toString();
        repository.markRunning(task.id(), token, now.plusSeconds(properties.leaseSeconds()));
        return Optional.of(new HlsBackfillRepository.Task(task.id(), task.videoId(), task.generation(),
                task.sourceKey(), task.sourceBytes(), task.attempts() + 1, token));
    }

    @Transactional(timeout = 5)
    public boolean succeed(HlsBackfillRepository.Task task, List<HlsVariant> variants) {
        if (repository.lockOwned(task.id(), task.token(), LocalDateTime.now(clock)).isEmpty()) return false;
        if (!repository.ready(task.videoId())) {
            repository.fail(task.id(), task.token(), "VIDEO_STATE");
            return false;
        }
        if (repository.hasHls(task.videoId())) {
            repository.done(task.id(), task.token());
            return false;
        }
        for (var variant : variants) repository.insertHls(task.videoId(), variant);
        repository.done(task.id(), task.token());
        return true;
    }

    @Transactional(timeout = 5)
    public void fail(HlsBackfillRepository.Task task, String error) {
        if (repository.lockOwned(task.id(), task.token(), LocalDateTime.now(clock)).isEmpty()) return;
        if (task.attempts() >= properties.maxAttempts()) {
            repository.fail(task.id(), task.token(), error);
        } else {
            long delay = Math.min(300, 10L << Math.min(task.attempts(), 4));
            repository.retry(task.id(), task.token(), LocalDateTime.now(clock).plusSeconds(delay), error);
        }
    }
}
