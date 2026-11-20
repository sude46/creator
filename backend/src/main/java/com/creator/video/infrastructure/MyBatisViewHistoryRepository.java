package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.ViewHistory;
import com.creator.video.domain.ViewHistoryRepository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisViewHistoryRepository implements ViewHistoryRepository {
    private final ViewHistoryMapper mapper;
    public MyBatisViewHistoryRepository(ViewHistoryMapper mapper) { this.mapper = mapper; }
    public void record(long userId, long videoId) {
        if (mapper.touch(userId, videoId) == 0) {
            try { mapper.insert(userId, videoId); } catch (DuplicateKeyException ignored) { }
        }
        var stale = mapper.staleIds(userId);
        if (!stale.isEmpty()) mapper.deleteIds(userId, stale);
    }
    public List<ViewHistory> findPage(long userId, PageRequest page) {
        return mapper.findPage(userId, page.size(), page.offset());
    }
    public long count(long userId) { return mapper.count(userId); }
    public void remove(long userId, long videoId) { mapper.remove(userId, videoId); }
    public void clear(long userId) { mapper.clear(userId); }
}
