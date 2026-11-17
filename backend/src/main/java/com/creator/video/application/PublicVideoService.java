package com.creator.video.application;

import com.creator.common.*;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicVideoService {
    private final VideoQueryRepository videos;
    public PublicVideoService(VideoQueryRepository videos) { this.videos = videos; }
    @Transactional(readOnly = true)
    public PageResult<PublicVideo> list(VideoSearch search, PageRequest page) {
        return new PageResult<>(videos.findPage(search, page), videos.count(search), page.page(), page.size());
    }
    @Transactional(readOnly = true)
    public PublicVideo get(long id) { return videos.findPublic(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); }
}
