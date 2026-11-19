package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.Notification;
import com.creator.video.domain.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisNotificationRepository implements NotificationRepository {
    private final NotificationMapper mapper;
    public MyBatisNotificationRepository(NotificationMapper mapper) { this.mapper = mapper; }
    public void add(long userId, String type, String title, String body, String link) { mapper.add(userId, type, title, body, link); }
    public List<Notification> page(long userId, PageRequest page) { return mapper.page(userId, page.size(), page.offset()); }
    public long count(long userId) { return mapper.count(userId); }
    public long unread(long userId) { return mapper.unread(userId); }
    public boolean markRead(long userId, long id) { return mapper.markRead(userId, id) == 1; }
    public void markAll(long userId) { mapper.markAll(userId); }
}
