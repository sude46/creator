package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFavoriteFolderRepository implements FavoriteFolderRepository {
    private final FavoriteFolderMapper mapper;
    public MyBatisFavoriteFolderRepository(FavoriteFolderMapper mapper) { this.mapper = mapper; }
    public List<FavoriteFolder> list(long ownerId) { return mapper.list(ownerId); }
    public long count(long ownerId) { return mapper.count(ownerId); }
    public Optional<FavoriteFolder> findOwned(long ownerId, long id, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockOwned(ownerId, id) : mapper.findOwned(ownerId, id));
    }
    public void create(long ownerId, String name) { mapper.create(ownerId, name); }
    public boolean rename(long ownerId, long id, long version, String name) {
        return mapper.rename(ownerId, id, version, name) == 1;
    }
    public boolean delete(long ownerId, long id) { return mapper.delete(ownerId, id) == 1; }
    public void addItem(long folderId, long videoId) {
        try { mapper.addItem(folderId, videoId); } catch (DuplicateKeyException duplicate) { }
    }
    public void removeItem(long folderId, long videoId) { mapper.removeItem(folderId, videoId); }
    public void removeVideoFromAll(long ownerId, long videoId) { mapper.removeVideoFromAll(ownerId, videoId); }
    public List<PublicVideo> items(long folderId, PageRequest page) {
        return mapper.items(folderId, page.size(), page.offset());
    }
    public long itemCount(long folderId) { return mapper.itemCount(folderId); }
}
