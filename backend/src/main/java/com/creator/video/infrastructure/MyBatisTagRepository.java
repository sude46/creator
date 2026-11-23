package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisTagRepository implements TagRepository {
    private final TagMapper mapper;
    public MyBatisTagRepository(TagMapper mapper) { this.mapper = mapper; }

    public List<String> tags(long videoId) { return mapper.names(videoId); }
    public List<TagCount> popular(int limit) { return mapper.popular(limit); }
    public List<TagCount> suggest(String pattern, int limit) { return mapper.suggest(pattern, limit); }

    public void replace(long videoId, List<String> tags) {
        mapper.deleteItems(videoId);
        for (String name : tags) {
            long tagId = tagId(name);
            mapper.insertItem(videoId, tagId);
        }
    }

    public List<TagAdmin> adminList() { return mapper.adminList(); }
    public Optional<TagAdmin> find(long id) { return Optional.ofNullable(mapper.findAdmin(id)); }
    public boolean rename(long id, String name, String key) { return mapper.rename(id, name, key) == 1; }
    public void merge(long sourceId, long targetId) {
        mapper.moveItems(sourceId, targetId);
        mapper.deleteTagItems(sourceId);
        mapper.deleteTag(sourceId);
    }
    public boolean delete(long id) { return mapper.deleteTag(id) == 1; }

    private long tagId(String name) {
        String key = VideoTags.key(name);
        Long existing = mapper.idByKey(key);
        if (existing != null) return existing;
        try {
            mapper.insertTag(name, key);
        } catch (DuplicateKeyException exception) {
            Long raced = mapper.idByKey(key);
            if (raced == null) throw exception;
            return raced;
        }
        Long created = mapper.idByKey(key);
        if (created == null) throw new IllegalStateException("Tag insert did not persist");
        return created;
    }
}
