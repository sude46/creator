package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCollectionRepository implements CollectionRepository {
    private final CollectionMapper mapper;
    private final VideoQueryMapper videos;
    public MyBatisCollectionRepository(CollectionMapper mapper, VideoQueryMapper videos) { this.mapper = mapper; this.videos = videos; }
    public List<VideoCollection> ownPage(long ownerId, PageRequest p) { return mapper.ownPage(ownerId, p.size(), p.offset()); }
    public long ownCount(long ownerId) { return mapper.ownCount(ownerId); }
    public Optional<VideoCollection> findOwned(long ownerId, long id, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockOwned(ownerId, id) : mapper.findOwned(ownerId, id));
    }
    public long insert(long ownerId, String title, String description, String visibility) {
        mapper.insert(ownerId, title, description, visibility); return mapper.lastId();
    }
    public boolean update(long ownerId, long id, long version, String title, String description, String visibility) {
        return mapper.update(ownerId, id, version, title, description, visibility) == 1;
    }
    public boolean delete(long ownerId, long id, long version) { return mapper.delete(ownerId, id, version) == 1; }
    public void deleteItems(long collectionId) { mapper.deleteItems(collectionId); }
    public boolean videoOwned(long ownerId, long videoId) { return Long.valueOf(ownerId).equals(mapper.videoOwner(videoId)); }
    public boolean itemExists(long collectionId, long videoId) { return mapper.itemExists(collectionId, videoId); }
    public int nextOrder(long collectionId) { return mapper.nextOrder(collectionId); }
    public void addItem(long collectionId, long videoId, int sortOrder) { mapper.addItem(collectionId, videoId, sortOrder); }
    public boolean removeItem(long collectionId, long videoId) { return mapper.removeItem(collectionId, videoId) == 1; }
    public List<VideoCollection> publicByOwner(long ownerId, PageRequest p) { return mapper.publicByOwner(ownerId, p.size(), p.offset()); }
    public long publicCountByOwner(long ownerId) { return mapper.publicCountByOwner(ownerId); }
    public Optional<VideoCollection> findPublic(long id) { return Optional.ofNullable(mapper.findPublic(id)); }
    public List<PublicVideo> publicItems(long collectionId, PageRequest p) {
        return videos.findPublicByCollection(collectionId, p.size(), p.offset());
    }
    public long publicItemCount(long collectionId) { return videos.countPublicByCollection(collectionId); }
}
