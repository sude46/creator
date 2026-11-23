package com.creator.video.web;

import com.creator.video.domain.CommentAdmin;

public record CommentAppealContextView(String id, String videoId, String content, String parentId, String status) {
    public static CommentAppealContextView from(CommentAdmin comment) {
        return new CommentAppealContextView(Long.toString(comment.id()), Long.toString(comment.videoId()),
                comment.content(), comment.parentId() == null ? null : Long.toString(comment.parentId()), comment.status());
    }
}
