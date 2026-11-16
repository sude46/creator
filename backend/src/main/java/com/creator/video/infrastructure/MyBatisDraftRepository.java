package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDraftRepository implements DraftRepository {
    private final DraftMapper mapper;

    public MyBatisDraftRepository(DraftMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DraftCreation> findCreation(long ownerId, String requestKey, boolean lock) {
        return Optional.ofNullable(lock ? mapper.lockCreation(ownerId, requestKey) : mapper.findCreation(ownerId, requestKey))
                .map(DraftRow::toCreation);
    }

    @Override
    public Optional<VideoDraft> findOwned(long ownerId, long id) {
        return Optional.ofNullable(mapper.findOwned(ownerId, id)).map(DraftRow::toDraft);
    }

    @Override
    public List<VideoDraft> findPage(long ownerId, PageRequest page) {
        return mapper.findPage(ownerId, page.size(), page.offset()).stream().map(DraftRow::toDraft).toList();
    }

    @Override
    public long count(long ownerId) {
        return mapper.count(ownerId);
    }

    @Override
    public boolean insert(long ownerId, String requestKey, VideoMetadata metadata) {
        try {
            mapper.insert(ownerId, requestKey, metadata, metadata.fingerprint());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean update(long ownerId, long id, long version, VideoMetadata metadata) {
        return mapper.update(ownerId, id, version, metadata) == 1;
    }
}
