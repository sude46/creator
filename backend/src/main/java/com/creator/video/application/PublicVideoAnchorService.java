package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicVideoAnchorService {
    private final VideoQueryRepository videos;

    public PublicVideoAnchorService(VideoQueryRepository videos) {
        this.videos = videos;
    }

    @Transactional(readOnly = true)
    public AnchoredVideos anchor(VideoSearch search, long anchorId, int before, int after) {
        if (before < 0 || before > 20 || after < 0 || after > 20 || before + 1 + after > 50) {
            throw new BusinessException(ErrorCode.INVALID_PAGE);
        }
        var anchor = videos.findPublicInSearch(search, anchorId);
        if (anchor.isPresent()) return videos.anchor(search, anchor.get(), before, after);
        var latest = videos.findPage(search, new com.creator.common.PageRequest(1, Math.min(50, before + 1 + after + 1)));
        int window = Math.min(50, before + 1 + after);
        boolean hasAfter = latest.size() > window;
        var items = latest.subList(0, Math.min(window, latest.size()));
        return new AnchoredVideos(items, null, false, hasAfter);
    }
}
