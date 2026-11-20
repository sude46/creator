package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface AppealRepository {
    void add(long appellantId, String targetType, long targetId, String reason);
    boolean openExists(long appellantId, String targetType, long targetId);
    List<ContentAppeal> page(Long appellantId, String targetType, Long targetId, String status, PageRequest page);
    long count(Long appellantId, String targetType, Long targetId, String status);
    Optional<ContentAppeal> lock(long id);
    boolean resolve(long id, long resolverId, String status, String reason);
}
