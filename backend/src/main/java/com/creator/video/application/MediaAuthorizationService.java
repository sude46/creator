package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.*;
import com.creator.video.domain.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MediaAuthorizationService {
    private final VideoQueryRepository videos;
    private final ObjectProvider<MediaSigner> signers;
    private final AccountQueryService accounts;
    public MediaAuthorizationService(VideoQueryRepository videos, ObjectProvider<MediaSigner> signers, AccountQueryService accounts) {
        this.videos = videos;
        this.signers = signers;
        this.accounts = accounts;
    }

    public MediaSigner.SignedMedia authorize(Actor actor, long videoId, MediaPurpose purpose, MediaKind kind, Long version, Long round) {
        return authorize(actor, videoId, purpose, kind, version, round, false);
    }

    public MediaSigner.SignedMedia authorize(Actor actor, long videoId, MediaPurpose purpose, MediaKind kind, Long version,
            Long round, boolean attachment) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (purpose == null || kind == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var active = accounts.current(actor.username());
        if (!active.equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        if (purpose == MediaPurpose.REVIEW_PREVIEW || purpose == MediaPurpose.REVIEW_DOWNLOAD) {
            if (!actor.canReview() && !actor.canModerate()) throw new BusinessException(ErrorCode.FORBIDDEN);
            if (version == null || version < 0 || round == null || round < 1) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var target = videos.media(videoId, actor.id(), purpose, kind, version, round)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        var signer = signers.getIfAvailable();
        if (signer == null) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        return signer.sign(target, kind, attachment);
    }
}
