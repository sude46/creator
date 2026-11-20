package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface FavoriteFolderRepository {
    List<FavoriteFolder> list(long ownerId);
    long count(long ownerId);
    Optional<FavoriteFolder> findOwned(long ownerId, long id, boolean lock);
    void create(long ownerId, String name);
    boolean rename(long ownerId, long id, long version, String name);
    boolean delete(long ownerId, long id);
    void addItem(long folderId, long videoId);
    void removeItem(long folderId, long videoId);
    void removeVideoFromAll(long ownerId, long videoId);
    List<PublicVideo> items(long folderId, PageRequest page);
    long itemCount(long folderId);
}
