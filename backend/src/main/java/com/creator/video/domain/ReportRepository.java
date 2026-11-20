package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    void add(long reporterId, String targetType, long targetId, String reason);
    List<ContentReport> page(String status, PageRequest page);
    long count(String status);
    Optional<ContentReport> lock(long id);
    boolean resolve(long id, long resolverId, String status, String reason);
}
