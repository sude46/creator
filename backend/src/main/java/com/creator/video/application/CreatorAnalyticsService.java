package com.creator.video.application;

import com.creator.common.*;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorAnalyticsService {
    private final AnalyticsRepository analytics;
    private final AccountQueryService accounts;
    public CreatorAnalyticsService(AnalyticsRepository analytics, AccountQueryService accounts) {
        this.analytics = analytics; this.accounts = accounts;
    }
    @Transactional(readOnly = true)
    public CreatorAnalytics get(Actor actor, int days) {
        active(actor);
        if (days < 1 || days > 90) throw new BusinessException(ErrorCode.INVALID_PAGE);
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(days - 1L);
        var raw = analytics.totalsAndTop(actor.id(), from, to);
        var byDay = new HashMap<LocalDate, Long>();
        raw.daily().forEach(item -> byDay.put(item.date(), item.views()));
        var daily = new ArrayList<DailyViews>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            daily.add(new DailyViews(date, byDay.getOrDefault(date, 0L)));
        }
        return new CreatorAnalytics(raw.videoCount(), raw.views(), raw.likes(), raw.favorites(), raw.comments(),
                raw.followers(), from, to, daily, raw.topVideos());
    }
    private void active(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!accounts.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
    }
}
