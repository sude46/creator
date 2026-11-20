package com.creator.video;

import com.creator.audit.application.AuditLog;
import com.creator.audit.infrastructure.MyBatisAuditLog;
import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.time.Instant;
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
@Import(PublicationIntegrationTest.Fixtures.class)
class PublicationIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired PublicationService publications;
    @Autowired CreatorVideoService videos;
    @Autowired PublicVideoService publicVideos;
    @Autowired MediaAuthorizationService media;
    @Autowired ReviewService reviews;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired FailingAudit audit;
    @Autowired MockMvc mvc;
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
        registration.execute("publication_author", "SafePassword123");
        registration.execute("publication_other", "SafePassword123");
        bootstrap.execute("publication_admin", "SafePassword123");
        bootstrap.execute("publication_second", "SafePassword123");
        author = actor("publication_author");
        other = actor("publication_other");
        admin = actor("publication_admin");
        secondAdmin = actor("publication_second");
        categoryId = categories.create(admin, "publication", 0).id();
    }

    @Test
    void ownerOfflinePreservesApprovalAndRepublishRechecksVisibilityWithoutNewReview() {
        long id = published();
        var issued = grant(other, id, MediaPurpose.PUBLIC_PLAY);
        var offline = publications.offline(author, id, 2);
        assertEquals("OFFLINE", offline.publicationStatus());
        assertEquals("APPROVED", offline.reviewStatus());
        assertEquals(3, offline.version());
        assertNull(offline.publishedAt());
        assertHidden(id);
        grant(author, id, MediaPurpose.OWNER_PREVIEW);
        assertNotNull(issued.url());
        var category = categories.managed(admin, new PageRequest(1, 20)).items().getFirst();
        categories.update(admin, categoryId, category.version(), category.name(), 0, false);
        var published = publications.publish(author, id, 3);
        assertEquals(4, published.version());
        assertEquals("PUBLISHED", published.publicationStatus());
        assertNotNull(published.publishedAt());
        assertEquals(id, publicVideos.get(id).id());
        grant(other, id, MediaPurpose.PUBLIC_PLAY);
        assertEquals(1, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals(1, countAction("VIDEO_OFFLINE"));
        assertEquals(1, countAction("VIDEO_REPUBLISHED"));
    }

    @Test
    void blockStopsAllNewMediaAndUnblockRequiresFreshReviewWhileKeepingHistory() {
        long id = published();
        var blocked = publications.block(admin, id, 2, reason("  标题违规  "));
        assertTrue(blocked.moderationBlocked());
        assertEquals("OFFLINE", blocked.publicationStatus());
        assertEquals("APPROVED", blocked.reviewStatus());
        assertHidden(id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(author, id, MediaPurpose.OWNER_PREVIEW)));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.publish(author, id, 3)));
        assertEquals(ErrorCode.REVIEW_STATE_CONFLICT, failure(() -> reviews.update(author, id, 3, new VideoMetadata("new", "", categoryId))));
        var unblocked = publications.unblock(secondAdmin, id, 3, reason("允许修订后重审"));
        assertFalse(unblocked.moderationBlocked());
        assertEquals("OFFLINE", unblocked.publicationStatus());
        assertEquals("NOT_SUBMITTED", unblocked.reviewStatus());
        assertNull(unblocked.reviewSubmittedAt());
        assertNull(unblocked.publishedAt());
        assertHidden(id);
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.publish(author, id, 4)));
        grant(author, id, MediaPurpose.OWNER_PREVIEW);
        var records = publications.ownerHistory(author, id, new PageRequest(1, 20));
        assertEquals(2, records.total());
        assertEquals("VIDEO_UNBLOCKED", records.items().getFirst().action());
        assertEquals("标题违规", records.items().getLast().reason());
        assertEquals(admin.id().longValue(), records.items().getLast().actorId());
        reviews.submit(author, id, 4);
        reviews.decide(admin, id, 5, 2, approve());
        assertEquals(id, publicVideos.get(id).id());
        assertEquals(2, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
    }

    @Test
    void blockingPendingReviewInvalidatesDecisionAndOldPreviewEvenAfterUnblock() {
        long id = ready();
        reviews.submit(author, id, 0);
        publications.block(admin, id, 1, reason("pending issue"));
        assertEquals(0, reviews.queue(secondAdmin, new PageRequest(1, 20)).total());
        assertThrows(BusinessException.class, () -> reviews.decide(secondAdmin, id, 1, 1, approve()));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(secondAdmin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 1L, 1L)));
        publications.unblock(admin, id, 2, reason("resolved"));
        reviews.submit(author, id, 3);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(secondAdmin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 4L, 1L)));
        media.authorize(secondAdmin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 4L, 2L);
    }

    @Test
    void ownershipAndRoleChecksApplyToInternalCommandsAndHistory() {
        long id = published();
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publications.offline(other, id, 2)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publications.offline(admin, id, 2)));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> publications.block(author, id, 2, reason("no"))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> publications.unblock(other, id, 2, reason("no"))));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publications.ownerHistory(other, id, new PageRequest(1, 20))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> publications.managed(author, new PageRequest(1, 20))));
        assertEquals(ErrorCode.UNAUTHENTICATED, failure(() -> publications.block(null, id, 2, reason("no"))));
    }

    @Test
    void disabledOrRoleChangedActorsCannotMutateOrReadAdministration() {
        long id = published();
        database.update("UPDATE user_account SET role='USER' WHERE id=?", admin.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> publications.block(admin, id, 2, reason("stale"))));
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> publications.managed(admin, id)));
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", author.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> publications.offline(author, id, 2)));
        publications.block(secondAdmin, id, 2, reason("disabled author still moderatable"));
        assertEquals(1, publications.managed(secondAdmin, new PageRequest(1, 20)).total());
    }

    @Test
    void staleDuplicateAndInvalidStateCommandsNeverCreateExtraAudit() {
        long id = published();
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> publications.offline(author, id, 1)));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.publish(author, id, 2)));
        publications.offline(author, id, 2);
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> publications.offline(author, id, 2)));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.offline(author, id, 3)));
        reviews.update(author, id, 3, new VideoMetadata("changed", "", categoryId));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.publish(author, id, 4)));
        publications.block(admin, id, 4, reason("block"));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.block(secondAdmin, id, 5, reason("duplicate"))));
        assertEquals(1, countAction("VIDEO_BLOCKED"));
        assertEquals(1, countAction("VIDEO_OFFLINE"));
    }

    @Test
    void incompleteUnprocessedAndDeletedVideosCannotBeChanged() {
        long draft = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("draft", "", categoryId)).id();
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.block(admin, draft, 0, reason("no"))));
        assertEquals(ErrorCode.PUBLICATION_STATE_CONFLICT, failure(() -> publications.offline(author, draft, 0)));
        long id = published();
        database.update("DELETE FROM media_asset WHERE video_id=? AND kind='COVER'", id);
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> publications.offline(author, id, 2)));
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> publications.block(admin, id, 2, reason("no"))));
        database.update("UPDATE video SET publication_status='OFFLINE',deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publications.block(admin, id, 2, reason("deleted"))));
        assertEquals(0, countAction("VIDEO_BLOCKED"));
    }

    @Test
    void auditFailureRollsBackEveryStateChangeAndReason() {
        long id = published();
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> publications.offline(author, id, 2));
        assertThrows(DataIntegrityViolationException.class, () -> publications.block(admin, id, 2, reason("failed")));
        assertEquals("PUBLISHED", videos.get(author, id).publicationStatus());
        assertFalse(videos.get(author, id).moderationBlocked());
        audit.fail = false;
        publications.offline(author, id, 2);
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> publications.publish(author, id, 3));
        assertEquals("OFFLINE", videos.get(author, id).publicationStatus());
        audit.fail = false;
        publications.block(admin, id, 3, reason("retained"));
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> publications.unblock(admin, id, 4, reason("failed release")));
        assertEquals(4, videos.get(author, id).version());
        assertTrue(videos.get(author, id).moderationBlocked());
        assertEquals(1, publications.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals(0, countAction("VIDEO_UNBLOCKED"));
    }

    @Test
    void httpCommandsRequireCsrfVersionReasonAndRejectUnknownFields() throws Exception {
        long id = published();
        var user = login(author);
        var administrator = login(admin);
        mvc.perform(post("/api/creator/videos/{id}/offline", id).session(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/videos/{id}/block", id).session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"reason\":\"no\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/videos/{id}/block", id).session(administrator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/videos/{id}/block", id).session(administrator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"reason\":\"no\",\"ownerId\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/creator/videos/{id}/offline", id).session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(post("/api/creator/videos/{id}/publication", id).session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3}")).andExpect(status().isOk()).andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"));
        mvc.perform(post("/api/admin/videos/{id}/block", id).session(administrator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":4,\"reason\":\"violation\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.moderationBlocked").value(true));
        mvc.perform(post("/api/admin/videos/{id}/unblock", id).session(administrator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":5,\"reason\":\"resolved\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("NOT_SUBMITTED"));
        mvc.perform(post("/api/creator/videos/{id}/publication", id).session(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":6}")).andExpect(status().isConflict());
    }

    @Test
    void managementAndHistoryQueriesAreBoundedPrivateAndUseStringIds() throws Exception {
        long id = published();
        publications.block(admin, id, 2, reason("moderation reason"));
        var administrator = login(admin);
        var owner = login(author);
        var outsider = login(other);
        mvc.perform(get("/api/admin/videos").session(administrator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ownerId").isString()).andExpect(jsonPath("$.items[0].video.id").isString());
        mvc.perform(get("/api/admin/videos/{id}", id).session(administrator)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/videos").session(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/creator/videos/{id}/moderation-history", id).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/creator/videos/{id}/moderation-history", id).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("moderation reason")).andExpect(jsonPath("$.items[0].id").isString());
        mvc.perform(get("/api/admin/videos/{id}/moderation-history", id).session(administrator)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/videos").session(administrator).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/creator/videos/{id}/moderation-history", id).session(owner).param("page", "1001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/videos")).andExpect(status().isUnauthorized());
    }

    @Test
    void moderationReasonsAreValidatedForInternalCalls() {
        for (String value : Arrays.asList(null, " ", "\u0000", "a".repeat(501), "\uD800")) {
            assertEquals(ErrorCode.INVALID_MODERATION_REASON, failure(() -> reason(value)));
        }
        assertEquals("valid\nreason", reason("  valid\nreason  ").value());
        assertEquals(ErrorCode.INVALID_MODERATION_REASON, failure(() -> publications.block(admin, 1, 0, null)));
        assertEquals(ErrorCode.INVALID_MODERATION_REASON, failure(() -> publications.unblock(admin, 1, 0, null)));
    }

    @Test
    void concurrentOfflineRequestsCreateOnlyOneVersionAndAudit() throws Exception {
        long id = published();
        assertOneSuccess(race(() -> publications.offline(author, id, 2), () -> publications.offline(author, id, 2)));
        assertEquals(3, videos.get(author, id).version());
        assertEquals(1, countAction("VIDEO_OFFLINE"));
    }

    @RepeatedTest(3)
    void concurrentRepublishAndBlockCannotBypassAdminRestriction() throws Exception {
        long id = published();
        publications.offline(author, id, 2);
        assertOneSuccess(race(() -> publications.publish(author, id, 3), () -> publications.block(admin, id, 3, reason("block"))));
        var current = videos.get(author, id);
        if (!current.moderationBlocked()) publications.block(admin, id, current.version(), reason("block latest"));
        assertHidden(id);
        assertEquals(1, countAction("VIDEO_BLOCKED"));
    }

    @RepeatedTest(3)
    void concurrentReviewAndBlockHaveOneWinnerAndNoPartialHistory() throws Exception {
        long id = ready();
        reviews.submit(author, id, 0);
        assertOneSuccess(race(() -> reviews.decide(secondAdmin, id, 1, 1, approve()),
                () -> publications.block(admin, id, 1, reason("pending issue"))));
        var current = videos.get(author, id);
        assertEquals(current.moderationBlocked() ? 0 : 1, reviews.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals(current.moderationBlocked() ? 1 : 0, countAction("VIDEO_BLOCKED"));
    }

    @Test
    void competingAdministratorsCannotDuplicateBlockReasons() throws Exception {
        long id = published();
        assertOneSuccess(race(() -> publications.block(admin, id, 2, reason("first")),
                () -> publications.block(secondAdmin, id, 2, reason("second"))));
        assertEquals(1, publications.ownerHistory(author, id, new PageRequest(1, 20)).total());
        assertEquals(3, videos.get(author, id).version());
    }

    private long published() {
        long id = ready();
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, approve());
        return id;
    }
    private long ready() {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("original title", "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID());
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID());
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        return id;
    }
    private void assertHidden(long id) {
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publicVideos.get(id)));
        assertEquals(0, publicVideos.list(new VideoSearch("", null), new PageRequest(1, 20)).total());
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(other, id, MediaPurpose.PUBLIC_PLAY)));
    }
    private MediaSigner.SignedMedia grant(Actor actor, long id, MediaPurpose purpose) {
        return media.authorize(actor, id, purpose, MediaKind.SOURCE, null, null);
    }
    private ModerationReason reason(String value) { return new ModerationReason(value); }
    private ReviewDecision approve() { return new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
    private int countAction(String action) { return database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action=?", Integer.class, action); }
    private ErrorCode failure(Callable<?> action) { return assertThrows(BusinessException.class, action::call).getCode(); }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username()).param("password", "SafePassword123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private void assertOneSuccess(List<Object> results) {
        assertEquals(1, results.stream().filter(CreatorVideo.class::isInstance).count());
        assertEquals(1, results.stream().filter(ErrorCode.class::isInstance).count());
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
        public void record(Actor actor, String action, String targetType, long id, String reason) {
            delegate.record(actor, action, targetType, id, reason);
            if (fail) throw new DataIntegrityViolationException("injected audit failure");
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary FailingAudit failingAudit(MyBatisAuditLog delegate) { return new FailingAudit(delegate); }
        @Bean MediaSigner mediaSigner() { return (target, kind) -> new MediaSigner.SignedMedia("https://media.example.test/signed", Instant.now().plusSeconds(60)); }
    }
}
