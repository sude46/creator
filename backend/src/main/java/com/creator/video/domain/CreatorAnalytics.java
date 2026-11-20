package com.creator.video.domain;

import java.time.LocalDate;
import java.util.List;

public record CreatorAnalytics(long videoCount, long views, long likes, long favorites, long comments, long followers,
        LocalDate from, LocalDate to, List<DailyViews> daily, List<TopVideo> topVideos) { }
