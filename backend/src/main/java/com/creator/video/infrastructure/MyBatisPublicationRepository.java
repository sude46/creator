package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisPublicationRepository implements PublicationRepository {
    private final PublicationMapper mapper;
    public MyBatisPublicationRepository(PublicationMapper mapper) { this.mapper = mapper; }
    public Optional<CreatorVideo> find(long id, boolean lock) { return Optional.ofNullable(lock ? mapper.lock(id) : mapper.find(id)); }
    public List<CreatorVideo> managed(PageRequest page) { return mapper.managed(page.size(), page.offset()); }
    public long managedCount() { return mapper.managedCount(); }
    public List<CreatorVideo> processing(PageRequest page) { return mapper.processing(page.size(), page.offset()); }
    public long processingCount() { return mapper.processingCount(); }
    public boolean offline(long ownerId, long id, long version) { return mapper.offline(ownerId, id, version) == 1; }
    public boolean publish(long ownerId, long id, long version, LocalDateTime now) { return mapper.publish(ownerId, id, version, now) == 1; }
    public boolean block(long id, long version) { return mapper.block(id, version) == 1; }
    public boolean unblock(long id, long version) { return mapper.unblock(id, version) == 1; }
    public List<ModerationRecord> history(long id, PageRequest page) { return mapper.history(id, page.size(), page.offset()); }
    public long historyCount(long id) { return mapper.historyCount(id); }
}
