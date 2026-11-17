package com.creator.video.infrastructure.messaging;

import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDispatchRepository implements DispatchRepository {
    private final DispatchMapper mapper;
    public MyBatisDispatchRepository(DispatchMapper mapper) { this.mapper = mapper; }
    @Override
    public List<Long> dueIds(LocalDateTime now, int limit) { return mapper.dueIds(now, limit); }
    @Override
    public boolean claim(long id, String token, LocalDateTime now, LocalDateTime leaseUntil) {
        return mapper.claim(id, token, now, leaseUntil) == 1;
    }
    @Override
    public Optional<DispatchClaim> claimed(long id) { return Optional.ofNullable(mapper.claimed(id)); }
    @Override
    public boolean markPublished(DispatchClaim claim, LocalDateTime now, LocalDateTime redriveAt) {
        return mapper.markPublished(claim.id(), claim.dispatchToken(), now, redriveAt) == 1;
    }
    @Override
    public boolean markFailed(DispatchClaim claim, LocalDateTime now, LocalDateTime retryAt, PublicationResult error) {
        return mapper.markFailed(claim.id(), claim.dispatchToken(), now, retryAt, error.name()) == 1;
    }
}
