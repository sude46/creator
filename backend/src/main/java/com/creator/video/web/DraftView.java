package com.creator.video.web;

import com.creator.video.domain.VideoDraft;

public record DraftView(String id, String categoryId, String title, String description, long version) {
    public static DraftView from(VideoDraft draft) {
        return new DraftView(draft.id().toString(), Long.toString(draft.metadata().categoryId()),
                draft.metadata().title(), draft.metadata().description(), draft.version());
    }
}
