package com.creator.video.domain;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsRepository {
    void recordView(long videoId, LocalDate date);
    long followerCount(long ownerId);
    CreatorAnalytics totalsAndTop(long ownerId, LocalDate from, LocalDate to);
    List<DailyViews> dailyViews(long ownerId, LocalDate from, LocalDate to);
}
