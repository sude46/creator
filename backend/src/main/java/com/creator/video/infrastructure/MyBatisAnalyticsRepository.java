package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAnalyticsRepository implements AnalyticsRepository {
    private final AnalyticsMapper mapper;
    public MyBatisAnalyticsRepository(AnalyticsMapper mapper) { this.mapper = mapper; }

    public void recordView(long videoId, LocalDate date) {
        if (mapper.incrementDaily(videoId, date) == 0) {
            try { mapper.insertDaily(videoId, date); }
            catch (DuplicateKeyException ignored) { mapper.incrementDaily(videoId, date); }
        }
    }
    public long followerCount(long ownerId) { return mapper.followerCount(ownerId); }
    public CreatorAnalytics totalsAndTop(long ownerId, LocalDate from, LocalDate to) {
        return new CreatorAnalytics(mapper.videoCount(ownerId), mapper.totalViews(ownerId), mapper.totalLikes(ownerId),
                mapper.totalFavorites(ownerId), mapper.totalComments(ownerId), mapper.followerCount(ownerId),
                from, to, dailyViews(ownerId, from, to), mapper.topVideos(ownerId));
    }
    public List<DailyViews> dailyViews(long ownerId, LocalDate from, LocalDate to) {
        return mapper.dailyViews(ownerId, from, to);
    }
}
