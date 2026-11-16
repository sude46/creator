package com.creator.video.domain;

public record UploadVideo(Long id, Long version, Long sourceAssetId, String processingStatus, Boolean writable) {
}
