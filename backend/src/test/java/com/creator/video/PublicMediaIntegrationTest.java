package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PublicMediaIntegrationTest.Fixtures.class)
class PublicMediaIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired PublicVideoService publicVideos;
    @Autowired CreatorVideoService creatorVideos;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired MediaAuthorizationService media;
    @Autowired VideoQueryRepository videoQueries;
    @Autowired AccountQueryService accountQueries;
    @Autowired RecordingSigner signer;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    Actor author;
    Actor other;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        signer.calls.clear();
        signer.fail = false;
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_account");
        registration.execute("media_author", "SafePassword123");
        registration.execute("media_other", "SafePassword123");
        bootstrap.execute("media_admin", "SafePassword123");
        author = actor("media_author");
        other = actor("media_other");
        admin = actor("media_admin");
        categoryId = categories.create(admin, "media", 0).id();
    }

    @Test
    void publicListDetailsAndSignedSourceCoverShareVisibilityRules() {
        long id = published("public");
        assertEquals(id, listing("").items().getFirst().id());
        assertEquals(id, publicVideos.get(id).id());
        grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE);
        grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.COVER);
        assertEquals(List.of(MediaKind.SOURCE, MediaKind.COVER), signer.calls.stream().map(Call::kind).toList());
        database.update("UPDATE video SET publication_status='OFFLINE' WHERE id=?", id);
        assertInvisible(id);
        assertEquals(2, signer.calls.size());
        grant(author, id, MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE);
    }

    @Test
    void draftPendingRejectedDeletedBlockedAndInactiveOwnerAreNeverPublic() {
        long id = published("hidden");
        for (String update : List.of("publication_status='UNPUBLISHED', review_status='NOT_SUBMITTED'",
                "publication_status='UNPUBLISHED', review_status='PENDING'", "publication_status='UNPUBLISHED', review_status='REJECTED'",
                "publication_status='OFFLINE', moderation_blocked=TRUE", "publication_status='OFFLINE', deleted_at=CURRENT_TIMESTAMP(6)")) {
            database.update("UPDATE video SET " + update + " WHERE id=?", id);
            assertInvisible(id);
        }
        database.update("UPDATE video SET deleted_at=NULL,moderation_blocked=FALSE,review_status='APPROVED',publication_status='PUBLISHED' WHERE id=?", id);
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", author.id());
        assertInvisible(id);
        assertTrue(signer.calls.isEmpty());
    }

    @Test
    void incompleteResourcesAndStaleProcessingGenerationCannotBeQueriedOrSigned() {
        long id = published("resource");
        database.update("UPDATE video SET processing_generation=2 WHERE id=?", id);
        assertInvisible(id);
        database.update("UPDATE video SET processing_generation=1 WHERE id=?", id);
        database.update("DELETE FROM media_asset WHERE kind='COVER' AND video_id=?", id);
        assertInvisible(id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(author, id, MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE)));
        assertTrue(signer.calls.isEmpty());
    }

    @Test
    void titleSearchTreatsWildcardsEscapeAndSqlTextAsLiteralsAndSupportsStablePagination() {
        long first = published("100%_! short drama");
        long second = published("ordinary title");
        assertEquals(first, listing("%_!").items().getFirst().id());
        assertEquals(1, listing("%").total());
        assertEquals(0, listing("' OR 1=1 --").total());
        assertEquals(2, listing("").total());
        database.update("UPDATE video SET published_at='2026-01-01 00:00:00' WHERE id IN (?,?)", first, second);
        assertEquals(second, publicVideos.list(new VideoSearch("", null), new PageRequest(1, 1)).items().getFirst().id());
        assertEquals(first, publicVideos.list(new VideoSearch("", null), new PageRequest(2, 1)).items().getFirst().id());
        assertEquals(0, publicVideos.list(new VideoSearch("", categoryId + 10000), new PageRequest(1, 20)).total());
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> new VideoSearch("a".repeat(121), null)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> new VideoSearch("\u0000", null)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> new VideoSearch("", 0L)));
    }

    @Test
    void categoryDisablePreventsNewSelectionButDoesNotHideExistingApprovedWorks() {
        long id = published("existing");
        var category = categories.managed(admin, new PageRequest(1, 20)).items().getFirst();
        categories.update(admin, categoryId, category.version(), category.name(), 0, false);
        assertEquals(id, listing("").items().getFirst().id());
        grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.COVER);
    }

    @Test
    void ownerPreviewIsPrivateAndAdminStatusDoesNotPermitOtherAuthorsPreview() {
        long id = ready(author, "private");
        grant(author, id, MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE);
        grant(author, id, MediaPurpose.OWNER_PREVIEW, MediaKind.COVER);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(other, id, MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(admin, id, MediaPurpose.OWNER_PREVIEW, MediaKind.COVER)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(author, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE)));
        assertEquals(2, signer.calls.size());
    }

    @Test
    void reviewPreviewRequiresAdminPendingVersionAndRoundAndRejectsSelfReview() {
        long id = ready(author, "pending");
        reviews.submit(author, id, 0);
        media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 1L, 1L);
        media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.COVER, 1L, 1L);
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> media.authorize(author, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 1L, 1L)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 0L, 1L)));
        reviews.withdraw(author, id, 1);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 1L, 1L)));
        reviews.submit(author, id, 2);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 3L, 1L)));
        media.authorize(admin, id, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 3L, 2L);
        long self = ready(admin, "self");
        reviews.submit(admin, self, 0);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> media.authorize(admin, self, MediaPurpose.REVIEW_PREVIEW, MediaKind.SOURCE, 1L, 1L)));
    }

    @Test
    void publishedEditStopsNewPublicLinksButLeavesOwnerPreviewAvailable() {
        long id = published("before");
        var issued = grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE);
        reviews.update(author, id, 2, new VideoMetadata("after", "", categoryId));
        assertInvisible(id);
        grant(author, id, MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE);
        assertNotNull(issued.url());
        assertEquals(2, signer.calls.size());
    }

    @Test
    void internalSigningRequiresActiveActorAndDoesNotSignInsideDatabaseTransaction() {
        long id = published("actor");
        grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE);
        database.update("UPDATE user_account SET role='ADMIN' WHERE id=?", other.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE)));
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", other.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> grant(other, id, MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE)));
        assertEquals(1, signer.calls.size());
    }

    @Test
    void disabledSigningReturnsUnavailableOnlyAfterCheckingVideoAccess() {
        long id = ready(author, "disabled");
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var disabled = new MediaAuthorizationService(videoQueries, beans.getBeanProvider(MediaSigner.class), accountQueries);
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure(() -> disabled.authorize(author, id,
                MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE, null, null)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> disabled.authorize(other, id,
                MediaPurpose.OWNER_PREVIEW, MediaKind.SOURCE, null, null)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> disabled.authorize(other, id,
                MediaPurpose.PUBLIC_PLAY, MediaKind.SOURCE, null, null)));
        assertTrue(signer.calls.isEmpty());
    }

    @Test
    void httpQueriesRequireLoginAndReturnOnlyPublicProjectionWithStringIds() throws Exception {
        long id = published("http");
        mvc.perform(get("/api/videos")).andExpect(status().isUnauthorized());
        var session = login(other);
        var response = mvc.perform(get("/api/videos/{id}", id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.toString(id))).andExpect(jsonPath("$.authorId").isString())
                .andExpect(jsonPath("$.failureCode").doesNotExist()).andExpect(jsonPath("$.reviewRound").doesNotExist()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("sources/"));
        mvc.perform(get("/api/videos").session(session).param("title", "http").param("categoryId", Long.toString(categoryId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/videos").session(session).param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void signingHttpRequiresCsrfUsesNoStoreAndCannotAcceptArbitraryObjectKeys() throws Exception {
        long id = published("link");
        var session = login(other);
        mvc.perform(post("/api/videos/{id}/media", id).session(session).param("kind", "SOURCE")).andExpect(status().isForbidden());
        mvc.perform(post("/api/videos/{id}/media", id).session(session).with(csrf()).param("kind", "SOURCE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(post("/api/videos/{id}/media", id).session(session).with(csrf()).param("kind", "SECRET"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reviews/{id}/preview", id).session(session).with(csrf())
                .param("kind", "SOURCE").param("version", "2").param("reviewRound", "1")).andExpect(status().isForbidden());
        signer.fail = true;
        mvc.perform(post("/api/videos/{id}/media", id).session(session).with(csrf()).param("kind", "COVER"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void ownerAndReviewerCanDownloadWithoutOpeningPublicAccess() throws Exception {
        long readyId = ready(author, "download ready");
        mvc.perform(post("/api/creator/videos/{id}/download", readyId).session(login(author)).with(csrf())
                        .param("kind", "SOURCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(post("/api/creator/videos/{id}/download", readyId).session(login(other)).with(csrf())
                        .param("kind", "SOURCE"))
                .andExpect(status().isNotFound());

        long pendingId = ready(author, "download review");
        reviews.submit(author, pendingId, 0);
        var pending = reviews.pending(admin, pendingId);
        mvc.perform(post("/api/admin/reviews/{id}/download", pendingId).session(login(admin)).with(csrf())
                        .param("kind", "SOURCE").param("version", Long.toString(pending.version()))
                        .param("reviewRound", Long.toString(pending.reviewRound())))
                .andExpect(status().isOk());
    }

    private MediaSigner.SignedMedia grant(Actor actor, long id, MediaPurpose purpose, MediaKind kind) {
        return media.authorize(actor, id, purpose, kind, null, null);
    }
    private PageResult<PublicVideo> listing(String title) { return publicVideos.list(new VideoSearch(title, null), new PageRequest(1, 20)); }
    private void assertInvisible(long id) {
        assertEquals(0, listing("").total());
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publicVideos.get(id)));
        for (var kind : MediaKind.values()) assertEquals(ErrorCode.NOT_FOUND, failure(() -> grant(other, id, MediaPurpose.PUBLIC_PLAY, kind)));
    }
    private long published(String title) {
        long id = ready(author, title);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        return id;
    }
    private long ready(Actor owner, String title) {
        long id = drafts.create(owner, UUID.randomUUID().toString(), new VideoMetadata(title, "public description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID() + ".jpg");
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        return id;
    }
    private ErrorCode failure(java.util.concurrent.Callable<?> operation) { return assertThrows(BusinessException.class, operation::call).getCode(); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username()).param("password", "SafePassword123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    record Call(MediaTarget target, MediaKind kind) { }
    static class RecordingSigner implements MediaSigner {
        final List<Call> calls = new ArrayList<>();
        boolean fail;
        public SignedMedia sign(MediaTarget target, MediaKind kind) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (fail) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
            calls.add(new Call(target, kind));
            return new SignedMedia("https://media.example.test/signed", Instant.now().plusSeconds(60));
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean RecordingSigner recordingSigner() { return new RecordingSigner(); }
    }
}
