package com.creator.video;

import com.creator.audit.application.AuditLog;
import com.creator.audit.infrastructure.MyBatisAuditLog;
import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReviewIntegrationTest.Fixtures.class)
class ReviewIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired ReviewService reviews;
    @Autowired BatchReviewService batchReviews;
    @Autowired CreatorVideoService videos;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired FailingAudit audit;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    Actor author;
    Actor other;
    Actor admin;
    Actor secondAdmin;
    long categoryId;

    @BeforeEach
    void reset() {
        audit.fail = false;
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment_like");
        database.update("DELETE FROM video_comment WHERE parent_id IS NOT NULL");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM favorite_folder_item");
        database.update("DELETE FROM favorite_folder");
        database.update("DELETE FROM view_history");
        database.update("DELETE FROM video_tag_item");
        database.update("DELETE FROM video_tag");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM user_account");
        registration.execute("review_author", "SafePassword123");
        registration.execute("review_other", "SafePassword123");
        bootstrap.execute("review_admin", "SafePassword123");
        bootstrap.execute("review_second_admin", "SafePassword123");
        author = actor("review_author");
        other = actor("review_other");
        admin = actor("review_admin");
        secondAdmin = actor("review_second_admin");
        categoryId = categories.create(admin, "review", 0).id();
    }

    @Test
    void submitApprovePublishesAtomicallyAndRecordsReviewedContentVersion() {
        long id = ready(author);
        var submitted = reviews.submit(author, id, 0);
        assertEquals(1, submitted.reviewRound());
        assertEquals(1, submitted.version());
        assertEquals("PENDING", submitted.reviewStatus());
        assertNotNull(submitted.reviewSubmittedAt());
        assertEquals(1, reviews.queue(admin, new PageRequest(1, 20)).total());
        var approved = reviews.decide(admin, id, 1, 1, approve());
        assertEquals("APPROVED", approved.reviewStatus());
        assertEquals("PUBLISHED", approved.publicationStatus());
        assertNotNull(approved.publishedAt());
        assertEquals(2, approved.version());
        var history = reviews.ownerHistory(author, id, new PageRequest(1, 20));
        assertEquals(1, history.total());
        var record = history.items().getFirst();
        assertEquals(1, record.contentVersion());
        assertEquals("original title", record.title());
        assertEquals(admin.id().longValue(), record.reviewerId());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='REVIEW_APPROVED'", Integer.class));
    }

    @Test
    void batchReviewReturnsPerItemResultsWithoutAtomicRollback() {
        long good = ready(author); long stale = ready(author);
        reviews.submit(author, good, 0);
        var pending = reviews.pending(admin, good);
        var result = batchReviews.decide(admin, List.of(
                new BatchReviewService.Item(good, pending.version(), pending.reviewRound(), ReviewDecision.Outcome.APPROVED, ""),
                new BatchReviewService.Item(stale, 0, 1, ReviewDecision.Outcome.APPROVED, "")));
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertTrue(result.results().getFirst().ok());
        assertFalse(result.results().get(1).ok());
    }

    @Test
    void rejectionEditAndResubmissionPreserveHistoricalReasonAndSnapshot() {
        long id = ready(author);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.REJECTED, " 请修改标题 "));
        assertEquals("UNPUBLISHED", videos.get(author, id).publicationStatus());
        assertNull(videos.get(author, id).publishedAt());
        var edited = reviews.update(author, id, 2, new VideoMetadata("new title", "new description", categoryId));
        assertEquals("NOT_SUBMITTED", edited.reviewStatus());
        var submitted = reviews.submit(author, id, 3);
        assertEquals(2, submitted.reviewRound());
        reviews.decide(admin, id, 4, 2, approve());
        var history = reviews.ownerHistory(author, id, new PageRequest(1, 20)).items();
        assertEquals(2, history.size());
        assertEquals("new title", history.getFirst().title());
        assertEquals("original title", history.getLast().title());
        assertEquals("请修改标题", history.getLast().reason());
        assertEquals(ReviewDecision.Outcome.REJECTED, history.getLast().outcome());
    }

    @Test
    void pendingContentCannotBeEditedAndWithdrawalInvalidatesOldReview() {
        long id = ready(author);
        reviews.submit(author, id, 0);
        assertEquals(ErrorCode.REVIEW_STATE_CONFLICT, failure(() -> reviews.update(author, id, 1, new VideoMetadata("changed", "", categoryId))));
        var withdrawn = reviews.withdraw(author, id, 1);
        assertEquals("NOT_SUBMITTED", withdrawn.reviewStatus());
        assertNull(withdrawn.reviewSubmittedAt());
        assertThrows(BusinessException.class, () -> reviews.decide(admin, id, 1, 1, approve()));
        var next = reviews.submit(author, id, 2);
        assertEquals(2, next.reviewRound());
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> reviews.decide(admin, id, 1, 1, approve())));
        assertEquals(0, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
    }

    @Test
    void modifyingPublishedMetadataRemovesPublicVisibilityAndRequiresAnotherReview() {
        long id = ready(author);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, approve());
        var edited = reviews.update(author, id, 2, new VideoMetadata("changed", "", categoryId));
        assertEquals("UNPUBLISHED", edited.publicationStatus());
        assertEquals("NOT_SUBMITTED", edited.reviewStatus());
        assertNull(edited.publishedAt());
        assertEquals(1, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
    }

    @Test
    void permissionAndHttpCsrfBoundariesHoldAndAdminCannotReviewOwnVideo() throws Exception {
        long id = ready(author);
        reviews.submit(author, id, 0);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> reviews.submit(other, id, 1)));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reviews.decide(author, id, 1, 1, approve())));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> reviews.ownerHistory(other, id, new PageRequest(1, 20))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reviews.queue(author, new PageRequest(1, 20))));
        long self = ready(admin);
        reviews.submit(admin, self, 0);
        assertEquals(1, reviews.queue(admin, new PageRequest(1, 20)).total());
        assertThrows(BusinessException.class, () -> reviews.decide(admin, self, 1, 1, approve()));
        var userSession = login(author);
        mvc.perform(get("/api/admin/reviews").session(userSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/creator/videos/{id}/review-withdrawal", id).session(userSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isForbidden());
        var adminSession = login(admin);
        mvc.perform(get("/api/admin/reviews").session(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reviewRound\":1,\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reviewRound\":1,\"outcome\":\"REJECTED\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REVIEW_REASON"));
    }

    @Test
    void invalidVersionsRoundsStatesAndMissingCoverCannotBeApproved() {
        long id = ready(author);
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> reviews.submit(author, id, 1)));
        reviews.submit(author, id, 0);
        assertEquals(ErrorCode.REVIEW_STATE_CONFLICT, failure(() -> reviews.decide(admin, id, 1, 2, approve())));
        database.update("DELETE FROM media_asset WHERE video_id=? AND kind='COVER'", id);
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> reviews.decide(admin, id, 1, 1, approve())));
        assertEquals(0, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals("PENDING", videos.get(author, id).reviewStatus());
    }

    @Test
    void disabledCategoryBlocksSubmitAndApprovalButAllowsRejectionAndExistingMetadataEdit() {
        long id = ready(author);
        reviews.submit(author, id, 0);
        var category = categories.managed(admin, new PageRequest(1, 20)).items().getFirst();
        categories.update(admin, categoryId, category.version(), category.name(), 0, false);
        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, failure(() -> reviews.decide(admin, id, 1, 1, approve())));
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.REJECTED, "category disabled"));
        reviews.update(author, id, 2, new VideoMetadata("edited", "", categoryId));
        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, failure(() -> reviews.submit(author, id, 3)));
    }

    @Test
    void blockedDeletedAndInactiveOwnersAreExcludedFromQueueAndDecision() {
        long id = ready(author);
        reviews.submit(author, id, 0);
        database.update("UPDATE video SET moderation_blocked=TRUE WHERE id=?", id);
        assertEquals(0, reviews.queue(admin, new PageRequest(1, 20)).total());
        assertThrows(BusinessException.class, () -> reviews.decide(admin, id, 1, 1, approve()));
        database.update("UPDATE video SET moderation_blocked=FALSE WHERE id=?", id);
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", author.id());
        assertEquals(0, reviews.queue(admin, new PageRequest(1, 20)).total());
        assertThrows(BusinessException.class, () -> reviews.decide(admin, id, 1, 1, approve()));
        database.update("UPDATE user_account SET status='ACTIVE' WHERE id=?", author.id());
        database.update("UPDATE video SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        assertEquals(0, reviews.queue(admin, new PageRequest(1, 20)).total());
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void concurrentReviewersProduceExactlyOneDecisionRecord() throws Exception {
        long id = ready(author);
        reviews.submit(author, id, 0);
        var results = race(() -> reviews.decide(admin, id, 1, 1, approve()),
                () -> reviews.decide(secondAdmin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.REJECTED, "no")));
        assertEquals(1, results.stream().filter(CreatorVideo.class::isInstance).count());
        assertEquals(1, results.stream().filter(ErrorCode.class::isInstance).count());
        assertEquals(1, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action IN ('REVIEW_APPROVED','REVIEW_REJECTED')", Integer.class));
    }

    @Test
    void concurrentWithdrawalAndDecisionOnlyOneSucceeds() throws Exception {
        long id = ready(author);
        reviews.submit(author, id, 0);
        var results = race(() -> reviews.withdraw(author, id, 1), () -> reviews.decide(admin, id, 1, 1, approve()));
        assertEquals(1, results.stream().filter(CreatorVideo.class::isInstance).count());
        var current = videos.get(author, id);
        assertTrue(current.reviewStatus().equals("APPROVED") || current.reviewStatus().equals("NOT_SUBMITTED"));
        assertEquals(current.reviewStatus().equals("APPROVED") ? 1 : 0, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
    }

    @Test
    void auditFailureRollsBackSubmitWithdrawalDecisionAndPublishedEdit() {
        long id = ready(author);
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> reviews.submit(author, id, 0));
        assertEquals(0, videos.get(author, id).reviewRound());
        audit.fail = false;
        reviews.submit(author, id, 0);
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> reviews.withdraw(author, id, 1));
        assertThrows(DataIntegrityViolationException.class, () -> reviews.decide(admin, id, 1, 1, approve()));
        assertEquals("PENDING", videos.get(author, id).reviewStatus());
        assertEquals(0, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
        audit.fail = false;
        reviews.decide(admin, id, 1, 1, approve());
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> reviews.update(author, id, 2, new VideoMetadata("new", "", categoryId)));
        assertEquals("PUBLISHED", videos.get(author, id).publicationStatus());
        assertEquals("original title", videos.get(author, id).title());
    }

    @Test
    void creatorAndAdminHttpCommandsCarryVersionAndRoundAndRejectUnknownFields() throws Exception {
        long id = ready(author);
        var ownerSession = login(author);
        var adminSession = login(admin);
        mvc.perform(put("/api/creator/videos/{id}", id).session(ownerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", 0, "title", "http title", "description", "", "categoryId", categoryId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/creator/videos/{id}/review-submissions", id).session(ownerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewRound").value(1));
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"reviewRound\":1,\"outcome\":\"APPROVED\",\"skipPermission\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"reviewRound\":1,\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"));
        mvc.perform(post("/api/admin/reviews/{id}/decision", id).session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"reviewRound\":1,\"outcome\":\"APPROVED\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
    }

    @Test
    void concurrentSubmissionsOpenOnlyOneRound() throws Exception {
        long id = ready(author);
        var results = race(() -> reviews.submit(author, id, 0), () -> reviews.submit(author, id, 0));
        assertEquals(1, results.stream().filter(CreatorVideo.class::isInstance).count());
        assertEquals(1, videos.get(author, id).reviewRound());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='REVIEW_SUBMITTED'", Integer.class));
    }

    @Test
    void internalDecisionValidationAndStaleAdminSnapshotCannotBypassRules() {
        for (String reason : List.of(" ", "\u0000", "reason".repeat(101))) {
            assertEquals(ErrorCode.INVALID_REVIEW_REASON,
                    assertThrows(BusinessException.class, () -> new ReviewDecision(ReviewDecision.Outcome.REJECTED, reason)).getCode());
        }
        long id = ready(author);
        reviews.submit(author, id, 0);
        database.update("UPDATE user_account SET role='USER' WHERE id=?", admin.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> reviews.decide(admin, id, 1, 1, approve())));
        assertEquals("PENDING", videos.get(author, id).reviewStatus());
        long draft = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("draft", "", categoryId)).id();
        assertEquals(ErrorCode.REVIEW_STATE_CONFLICT, failure(() -> reviews.submit(author, draft, 0)));
    }

    @Test
    void reviewHistoryIsBoundedAndPreservesSnapshotWhileHidingOtherPrivateDrafts() throws Exception {
        long draft = drafts.create(other, UUID.randomUUID().toString(), new VideoMetadata("private", "", categoryId)).id();
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> reviews.adminHistory(admin, draft, new PageRequest(1, 20))));
        long id = ready(author);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, approve());
        var session = login(author);
        mvc.perform(get("/api/creator/videos/{id}/reviews", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].title").value("original title"));
        assertEquals(1, reviews.adminHistory(admin, id, new PageRequest(1, 20)).total());
        mvc.perform(get("/api/creator/videos/{id}/reviews", id).session(session).param("size", "51")).andExpect(status().isBadRequest());
    }

    private long ready(Actor owner) {
        long id = drafts.create(owner, UUID.randomUUID().toString(), new VideoMetadata("original title", "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID());
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID());
        database.update("UPDATE video SET source_asset_id=?, processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        return id;
    }
    private ReviewDecision approve() { return new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
    private ErrorCode failure(Callable<?> action) {
        return assertThrows(BusinessException.class, action::call).getCode();
    }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username()).param("password", "SafePassword123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> attempt(ready, start, first));
            var secondResult = executor.submit(() -> attempt(ready, start, second));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }
    private Object attempt(CountDownLatch ready, CountDownLatch start, Callable<Object> action) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { return action.call(); } catch (BusinessException exception) { return exception.getCode(); }
    }
    static class FailingAudit implements AuditLog {
        private final MyBatisAuditLog delegate;
        volatile boolean fail;
        FailingAudit(MyBatisAuditLog delegate) { this.delegate = delegate; }
        public void record(Actor actor, String action, String targetType, long targetId, String reason) {
            delegate.record(actor, action, targetType, targetId, reason);
            if (fail) throw new DataIntegrityViolationException("injected audit failure");
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary FailingAudit failingAudit(MyBatisAuditLog delegate) { return new FailingAudit(delegate); }
    }
}
