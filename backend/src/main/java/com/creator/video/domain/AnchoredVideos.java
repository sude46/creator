package com.creator.video.domain;

import java.util.List;

public record AnchoredVideos(List<PublicVideo> items, Integer anchorIndex, boolean hasBefore, boolean hasAfter) {
}
