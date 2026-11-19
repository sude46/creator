package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.*;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreatorVideoService {
    private final CreatorVideoRepository videos;
    private final UploadRepository uploads;
    private final AuditLog audit;
    private final int maxPending;

    public CreatorVideoService(CreatorVideoRepository videos, UploadRepository uploads, AuditLog audit,
            @Value("${creator.upload.max-pending:5}") int maxPending) {
        if (maxPending < 1 || maxPending > 20) throw new IllegalArgumentException("Pending video limit must be 1..20");
        this.videos = videos;
        this.uploads = uploads;
        this.audit = audit;
        this.maxPending = maxPending;
    }

    @Transactional(readOnly = true)
    public CreatorVideo get(Actor actor, long id) { return owned(actor, id, false); }

    @Transactional(readOnly = true)
    public PageResult<CreatorVideo> list(Actor actor, PageRequest page) {
        return new PageResult<>(videos.findPage(actor.id(), page), videos.count(actor.id()), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(Actor actor) {
        var rows = new ArrayList<CreatorVideo>();
        for (int page = 1; page <= 20; page++) {
            var batch = videos.findPage(actor.id(), new PageRequest(page, PageRequest.MAX_SIZE));
            rows.addAll(batch);
            if (batch.size() < PageRequest.MAX_SIZE) break;
        }
        var csv = new StringBuilder("id,title,categoryId,processingStatus,reviewStatus,publicationStatus,version,durationMillis,width,height,publishedAt\n");
        for (var video : rows) {
            csv.append(cell(Long.toString(video.id()))).append(',')
                    .append(cell(video.title())).append(',')
                    .append(cell(Long.toString(video.categoryId()))).append(',')
                    .append(cell(video.processingStatus())).append(',')
                    .append(cell(video.reviewStatus())).append(',')
                    .append(cell(video.publicationStatus())).append(',')
                    .append(video.version()).append(',')
                    .append(video.durationMillis() == null ? "" : video.durationMillis()).append(',')
                    .append(video.mediaWidth() == null ? "" : video.mediaWidth()).append(',')
                    .append(video.mediaHeight() == null ? "" : video.mediaHeight()).append(',')
                    .append(cell(video.publishedAt() == null ? "" : video.publishedAt().toString())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    @Transactional(timeout = 5)
    public CreatorVideo retry(Actor actor, long id, long version) {
        if (!uploads.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        var video = owned(actor, id, true);
        video.requireVersion(version);
        video.requireRetryAllowed();
        if (uploads.pendingCount(actor.id()) >= maxPending) throw new BusinessException(ErrorCode.UPLOAD_QUOTA_EXCEEDED);
        if (!videos.retry(actor.id(), id, version, video.processingGeneration())) throw new BusinessException(ErrorCode.STALE_WRITE);
        videos.insertTask(id, video.processingGeneration() + 1);
        audit.record(actor, "PROCESSING_RETRIED", "VIDEO", id);
        return owned(actor, id, false);
    }

    private CreatorVideo owned(Actor actor, long id, boolean lock) {
        return videos.findOwned(actor.id(), id, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
