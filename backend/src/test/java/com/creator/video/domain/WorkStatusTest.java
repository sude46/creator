package com.creator.video.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorkStatusTest {
    @Test
    void derivesNineStatesAndSafeReasons() {
        assertEquals(WorkStatus.NEEDS_UPLOAD, video(false, "PENDING", "NOT_SUBMITTED", "UNPUBLISHED", false, null, null).workStatus());
        assertEquals(WorkStatus.PROCESSING, video(true, "PENDING", "NOT_SUBMITTED", "UNPUBLISHED", false, null, null).workStatus());
        var failed = video(true, "FAILED", "NOT_SUBMITTED", "UNPUBLISHED", false, ProcessingFailure.TEMP_UNAVAILABLE, null);
        assertEquals(WorkStatus.FAILED, failed.workStatus());
        assertEquals("处理空间暂时不足", failed.statusReason());
        assertEquals(WorkStatus.READY_TO_SUBMIT, video(true, "READY", "NOT_SUBMITTED", "UNPUBLISHED", false, null, null).workStatus());
        assertEquals(WorkStatus.PENDING_REVIEW, video(true, "READY", "PENDING", "UNPUBLISHED", false, null, null).workStatus());
        var rejected = video(true, "READY", "REJECTED", "UNPUBLISHED", false, null, "画质不达标");
        assertEquals(WorkStatus.REJECTED, rejected.workStatus());
        assertEquals("画质不达标", rejected.statusReason());
        assertEquals(WorkStatus.PUBLISHED, video(true, "READY", "APPROVED", "PUBLISHED", false, null, null).workStatus());
        assertEquals(WorkStatus.TAKEN_DOWN, video(true, "READY", "APPROVED", "OFFLINE", false, null, null).workStatus());
        assertEquals(WorkStatus.BLOCKED, video(true, "READY", "APPROVED", "OFFLINE", true, null, null).workStatus());
    }

    private CreatorVideo video(boolean source, String processing, String review, String publication, boolean blocked,
            ProcessingFailure failure, String rejection) {
        return new CreatorVideo(1, 1, 1, "t", "d", 0, 1, source, blocked, processing, review, publication,
                "SUCCEEDED", failure, 0, null, 1000L, 640, 360, 1, null, null, "covers/a.jpg", rejection);
    }
}
