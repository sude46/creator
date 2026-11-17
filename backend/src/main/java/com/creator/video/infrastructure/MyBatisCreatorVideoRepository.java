package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCreatorVideoRepository implements CreatorVideoRepository {
    private final CreatorVideoMapper mapper;
    public MyBatisCreatorVideoRepository(CreatorVideoMapper mapper) { this.mapper = mapper; }
    public Optional<CreatorVideo> findOwned(long ownerId, long videoId, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockOwned(ownerId, videoId) : mapper.findOwned(ownerId, videoId));
    }
    public List<CreatorVideo> findPage(long ownerId, PageRequest page) { return mapper.findPage(ownerId, page.size(), page.offset()); }
    public long count(long ownerId) { return mapper.count(ownerId); }
    public boolean retry(long ownerId, long videoId, long version, long generation) { return mapper.retry(ownerId, videoId, version, generation) == 1; }
    public void insertTask(long videoId, long generation) { mapper.insertTask(videoId, generation); }
}
