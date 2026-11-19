package com.creator.video.application;

import com.creator.common.*;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCollectionService {
    private final CollectionRepository collections;
    private final VideoQueryRepository videos;

    public PublicCollectionService(CollectionRepository collections, VideoQueryRepository videos) {
        this.collections = collections;
        this.videos = videos;
    }

    @Transactional(readOnly = true)
    public PageResult<VideoCollection> listByAuthor(long authorId, PageRequest page) {
        if (authorId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return new PageResult<>(collections.publicByOwner(authorId, page), collections.publicCountByOwner(authorId), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public Detail get(long id, PageRequest page) {
        var collection = collections.findPublic(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        var items = new PageResult<>(collections.publicItems(id, page), collections.publicItemCount(id), page.page(), page.size());
        return new Detail(collection, items);
    }

    public record Detail(VideoCollection collection, PageResult<PublicVideo> items) { }
}
