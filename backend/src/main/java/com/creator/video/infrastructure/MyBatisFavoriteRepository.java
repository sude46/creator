package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFavoriteRepository implements FavoriteRepository {
    private final FavoriteMapper mapper;
    public MyBatisFavoriteRepository(FavoriteMapper mapper) { this.mapper = mapper; }
    public void add(long userId, long videoId) {
        try { mapper.add(userId, videoId); } catch (DuplicateKeyException duplicate) { }
    }
    public void remove(long userId, long videoId) { mapper.remove(userId, videoId); }
    public boolean contains(long userId, long videoId) { return mapper.contains(userId, videoId); }
    public List<Favorite> findPage(long userId, PageRequest page) { return mapper.findPage(userId, page.size(), page.offset()); }
    public long count(long userId) { return mapper.count(userId); }
}
