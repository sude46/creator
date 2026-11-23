package com.creator.video.domain;

public record CommentAdmin(long id, long videoId, long userId, String content, Long parentId, String status) { }
