package com.creator.video.domain;

public record VideoDraft(Long id, Long ownerId, VideoMetadata metadata, long version) {
}
