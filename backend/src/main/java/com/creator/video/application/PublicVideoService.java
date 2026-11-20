package com.creator.video.application;

import com.creator.common.*;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicVideoService {
    private final VideoQueryRepository videos;
    private final HistoryService histories;
    public PublicVideoService(VideoQueryRepository videos, HistoryService histories) {
        this.videos = videos;
        this.histories = histories;
    }
    @Transactional(readOnly = true)
    public PageResult<PublicVideo> list(VideoSearch search, PageRequest page) {
        return list(search, page, "latest");
    }

    @Transactional(readOnly = true)
    public PageResult<PublicVideo> list(VideoSearch search, PageRequest page, String sort) {
        String order = sort == null ? "latest" : sort.trim().toLowerCase();
        if (!order.equals("latest") && !order.equals("popular")) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var items = order.equals("popular") ? videos.findPopularPage(search, page) : videos.findPage(search, page);
        return new PageResult<>(items, videos.count(search), page.page(), page.size());
    }
    @Transactional(readOnly = true)
    public PageResult<PublicVideo> listByAuthor(long authorId, PageRequest page) {
        if (authorId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return new PageResult<>(videos.findPageByAuthor(authorId, page), videos.countByAuthor(authorId), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public PublicVideo get(long id) { return videos.findPublic(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); }

    @Transactional(timeout = 5)
    public long recordView(long id) { return recordView(id, null); }

    @Transactional(timeout = 5)
    public long recordView(long id, Long userId) {
        if (!videos.incrementView(id)) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (userId != null) histories.record(userId, id);
        return videos.findPublic(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)).viewCount();
    }
}
