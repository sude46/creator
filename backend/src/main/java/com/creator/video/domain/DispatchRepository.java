package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DispatchRepository {
    List<Long> dueIds(LocalDateTime now, int limit);
    boolean claim(long id, String token, LocalDateTime now, LocalDateTime leaseUntil);
    Optional<DispatchClaim> claimed(long id);
    boolean markPublished(DispatchClaim claim, LocalDateTime now, LocalDateTime redriveAt);
    boolean markFailed(DispatchClaim claim, LocalDateTime now, LocalDateTime retryAt, PublicationResult error);
}
