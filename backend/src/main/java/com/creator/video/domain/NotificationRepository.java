package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;

public interface NotificationRepository {
    void add(long userId, String type, String title, String body, String link);
    List<Notification> page(long userId, PageRequest page);
    long count(long userId);
    long unread(long userId);
    boolean markRead(long userId, long id);
    void markAll(long userId);
}
