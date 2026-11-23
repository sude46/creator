package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.Notification;
import com.creator.video.domain.NotificationAggregate;
import com.creator.video.domain.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisNotificationRepository implements NotificationRepository {
    private final NotificationMapper mapper;
    public MyBatisNotificationRepository(NotificationMapper mapper) { this.mapper = mapper; }
    public void add(long userId, String type, String title, String body, String link) { mapper.add(userId, type, title, body, link); }
    public List<Notification> page(long userId, boolean unreadOnly, String type, PageRequest page) {
        return mapper.page(userId, unreadOnly, type, page.size(), page.offset());
    }
    public long count(long userId, boolean unreadOnly, String type) {
        return mapper.count(userId, unreadOnly, type);
    }
    public NotificationAggregate findAggregate(long userId, String type, String key) {
        return mapper.findAggregate(userId, type, key);
    }
    public void addAggregated(long userId, String type, String key, String title, String body, String link) {
        mapper.addAggregated(userId, type, key, title, body, link);
    }
    public void updateAggregated(long id, String title, String body, int count, java.time.LocalDateTime now) {
        mapper.updateAggregated(id, title, body, count, now);
    }
    public boolean delete(long userId, long id) { return mapper.delete(userId, id) == 1; }
    public void clearRead(long userId) { mapper.clearRead(userId); }
    public long unread(long userId) { return mapper.unread(userId); }
    public boolean markRead(long userId, long id) { return mapper.markRead(userId, id) == 1; }
    public void markAll(long userId) { mapper.markAll(userId); }
}
