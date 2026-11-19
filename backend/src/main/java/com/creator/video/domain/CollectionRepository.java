package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository {
    List<VideoCollection> ownPage(long ownerId, PageRequest page);
    long ownCount(long ownerId);
    Optional<VideoCollection> findOwned(long ownerId, long id, boolean lock);
    long insert(long ownerId, String title, String description, String visibility);
    boolean update(long ownerId, long id, long version, String title, String description, String visibility);
    boolean delete(long ownerId, long id, long version);
    void deleteItems(long collectionId);
    boolean videoOwned(long ownerId, long videoId);
    boolean itemExists(long collectionId, long videoId);
    int nextOrder(long collectionId);
    void addItem(long collectionId, long videoId, int sortOrder);
    boolean removeItem(long collectionId, long videoId);
    List<VideoCollection> publicByOwner(long ownerId, PageRequest page);
    long publicCountByOwner(long ownerId);
    Optional<VideoCollection> findPublic(long id);
    List<PublicVideo> publicItems(long collectionId, PageRequest page);
    long publicItemCount(long collectionId);
}
