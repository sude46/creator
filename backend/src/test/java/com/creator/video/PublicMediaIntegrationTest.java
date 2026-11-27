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
    @Autowired com.creator.video.application.PublicVideoAnchorService anchorVideos;
    @Autowired com.creator.video.application.LikeService likes;
    @Autowired com.creator.video.application.CommentService commentService;
    @Autowired com.creator.video.application.FollowService followService;
    @Autowired com.creator.video.application.PublicFollowingService followingService;
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
    @Autowired HlsStorage hlsStorage;
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
        database.update("DELETE FROM video_subtitle");
        database.update("DELETE FROM video_hls_segment");
        database.update("DELETE FROM video_hls");
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
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM content_appeal");
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
    void followingFeedReturnsOnlyFollowedAuthorsPublicWorks() {
        published("followed work");
        followService.follow(other, author.id());
        var page = followingService.list(other, new PageRequest(1, 12));
        assertEquals(1, page.total());
        assertEquals(author.id(), page.items().getFirst().ownerId());
        followService.unfollow(other, author.id());
        assertEquals(0, followingService.list(other, new PageRequest(1, 12)).total());
    }

    @Test
    void commentsListDeleteAndEnforceOwnership() {
        long id = published("comment target");
        var comment = commentService.add(author, id, "  第一条评论  ");
        assertEquals("第一条评论", comment.content());
        assertEquals(1, commentService.list(author, id, new PageRequest(1, 10)).total());
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> { commentService.delete(other, comment.id()); return null; }));
        commentService.delete(admin, comment.id());
        assertEquals(0, commentService.list(author, id, new PageRequest(1, 10)).total());
    }

    @Test
    void likesAreIdempotentAndOnlyTargetPublicWorks() {
        long id = published("like target");
        likes.add(author, id); likes.add(author, id);
        assertTrue(likes.state(author, id));
        assertEquals(1, listing("like target").items().getFirst().likeCount());
        likes.remove(author, id); likes.remove(author, id);
        assertFalse(likes.state(author, id));
        database.update("UPDATE video SET publication_status='OFFLINE' WHERE id=?", id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> { likes.add(author, id); return null; }));
    }

    @Test
    void authorFeedReturnsOnlyThatAuthorsPublicWorks() {
        published("author one");
        published("author two");
        assertEquals(2, publicVideos.listByAuthor(author.id(), new PageRequest(1, 20)).total());
        assertEquals(0, publicVideos.listByAuthor(other.id(), new PageRequest(1, 20)).total());
    }

    @Test
    void publicViewsIncrementOnlyForVisibleWorks() {
        long id = published("view count");
        assertEquals(0, publicVideos.get(id).viewCount());
        assertEquals(1, publicVideos.recordView(id));
        assertEquals(1, publicVideos.get(id).viewCount());
        assertEquals(2, publicVideos.recordView(id));
        database.update("UPDATE video SET publication_status='OFFLINE' WHERE id=?", id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> publicVideos.recordView(id)));
    }

    @Test
    void anchoredWindowReturnsAnchorIndexAndFallsBackForUnknownIds() {
        long first = published("anchor one");
        long second = published("anchor two");
        published("anchor three");
        var window = anchorVideos.anchor(new VideoSearch("", null), first, 1, 1);
        assertEquals(1, window.anchorIndex());
        assertEquals(first, window.items().get(window.anchorIndex()).id());
        assertEquals(second, window.items().getFirst().id());
        assertTrue(window.hasBefore());
        assertFalse(window.hasAfter());
        var missing = anchorVideos.anchor(new VideoSearch("", null), 999999, 3, 4);
        assertNull(missing.anchorIndex());
        assertFalse(missing.hasBefore());
        assertFalse(missing.hasAfter());
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
        database.update("UPDATE video SET description='unique-search-needle' WHERE id=?", first);
        assertEquals(first, listing("unique-search-needle").items().getFirst().id());
        assertEquals(1, listing("unique-search-needle").total());
        assertEquals(2, publicVideos.list(new VideoSearch("", null, null, "media_author"), new PageRequest(1, 20)).total());
        assertEquals(0, publicVideos.list(new VideoSearch("", null, null, "missing-author"), new PageRequest(1, 20)).total());
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> new VideoSearch("", null, null, "a".repeat(33))));
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
        mvc.perform(get("/api/videos")).andExpect(status().isOk());
        mvc.perform(get("/api/videos/{id}/comments", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/videos/{id}/comments", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"anonymous\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/videos/{id}/view", id).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/authors/{id}/profile", author.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.self").value(false));
        mvc.perform(get("/api/authors/{id}/videos", author.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/search/suggestions").param("q", "http"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videos[0].title").value("http"));
        mvc.perform(get("/api/search/suggestions").param("q", "media_author"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authors[0].username").value("media_author"));
        mvc.perform(get("/api/search/suggestions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.videos.length()").value(0));
        mvc.perform(get("/api/search/suggestions").param("q", "a".repeat(51)))
                .andExpect(status().isBadRequest());
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
    void anonymousCanSignPublicMediaButNotPrivatePurposes() throws Exception {
        long id = published("anon-sign");
        mvc.perform(post("/api/videos/{id}/media", id).with(csrf()).param("kind", "COVER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").exists());
        mvc.perform(post("/api/creator/videos/{id}/preview", id).with(csrf()).param("kind", "SOURCE"))
                .andExpect(status().isUnauthorized());
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
    @Test
    void hlsMasterAndPlaylistArePublicAndRewriteSegmentsWithSignedUrls() throws Exception {
        long id = published("hls public");
        String base = "videos/" + id + "/1/hls/720p";
        database.update("INSERT INTO video_hls (video_id,rendition,bandwidth,width,height,base_key,playlist_key,playlist)"
                + " VALUES (?,?,?,?,?,?,?,?)", id, "720p", 2500, 1280, 720, base, base + "/index.m3u8",
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXTINF:3.0,\nseg-00000.ts\n#EXT-X-ENDLIST\n");
        byte[] segmentBytes = new byte[] {0x47, 0x40, 0x11, 0x10};
        hlsStorage.objects.put(base + "/seg-00000.ts", segmentBytes);
        String master = mvc.perform(get("/api/videos/{id}/hls/master.m3u8", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.apple.mpegurl"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(master.contains("RESOLUTION=1280x720"));
        assertTrue(master.contains("/api/videos/" + id + "/hls/720p/index.m3u8"));
        String playlist = mvc.perform(get("/api/videos/{id}/hls/720p/index.m3u8", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(playlist.contains("/api/videos/" + id + "/hls/720p/seg-00000.ts"));
        assertFalse(playlist.lines().anyMatch(line -> line.equals("seg-00000.ts")));
        mvc.perform(get("/api/videos/{id}/hls/720p/seg-00000.ts", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("video/mp2t"))
                .andExpect(content().bytes(segmentBytes));
        mvc.perform(get("/api/videos/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasHls").value(true));
        assertTrue(signer.calls.isEmpty());

        mvc.perform(get("/api/videos/{id}/hls/1080p/index.m3u8", id)).andExpect(status().isNotFound());
        database.update("UPDATE video SET publication_status='OFFLINE' WHERE id=?", id);
        mvc.perform(get("/api/videos/{id}/hls/master.m3u8", id)).andExpect(status().isNotFound());
    }

    @Test
    void ownerCanReplaceAndDeleteWebVttAndPublicPlaybackExposesIt() throws Exception {
        long id = published("subtitle video");
        var session = login(author);
        byte[] vtt = "WEBVTT\n\n00:00.000 --> 00:02.000\n你好，帧间。\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(put("/api/creator/videos/{id}/subtitle", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content(vtt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.sizeBytes").value(vtt.length));
        mvc.perform(get("/api/creator/videos/{id}/subtitle", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.language").value("zh-CN"));
        byte[] english = "WEBVTT\n\n00:00.000 --> 00:02.000\nHello from Frame.\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(put("/api/creator/videos/{id}/subtitles/en", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content(english))
                .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.label").value("English"));
        mvc.perform(get("/api/creator/videos/{id}/subtitles", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].language").value("en"));
        mvc.perform(get("/api/videos/{id}/subtitles", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/videos/{id}/subtitles/en.vtt", id))
                .andExpect(status().isOk()).andExpect(content().bytes(english));
        mvc.perform(get("/api/videos/{id}/subtitle.vtt", id))
                .andExpect(status().isOk()).andExpect(content().bytes(vtt))
                .andExpect(content().contentTypeCompatibleWith("text/vtt"));
        mvc.perform(get("/api/videos/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubtitle").value(true));

        mvc.perform(put("/api/creator/videos/{id}/subtitles/fr", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content(vtt))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/creator/videos/{id}/subtitle", id).session(login(other)).with(csrf().asHeader())
                        .contentType("text/vtt").content(vtt))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/creator/videos/{id}/subtitle", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content("not a subtitle"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/creator/videos/{id}/subtitle", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content("WEBVTT\n\n<script>alert(1)</script>"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/creator/videos/{id}/subtitle", id).session(session).with(csrf().asHeader())
                        .contentType("text/vtt").content(new byte[524289]))
                .andExpect(status().isPayloadTooLarge());

        mvc.perform(delete("/api/creator/videos/{id}/subtitles/en", id).session(session).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/videos/{id}/subtitles", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].language").value("zh-CN"));
        mvc.perform(delete("/api/creator/videos/{id}/subtitle", id).session(session).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/videos/{id}/subtitle.vtt", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/videos/{id}/subtitles", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/videos/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubtitle").value(false));
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
        @Bean HlsStorage hlsStorage() { return new HlsStorage(); }
        @Bean com.creator.video.infrastructure.media.MediaProperties mediaProperties() {
            return new com.creator.video.infrastructure.media.MediaProperties("https://media.example.test", "test-region", 60, 300);
        }
    }

    static class HlsStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void verifyPrivateBucket() { }
        @Override public void put(String objectKey, java.nio.file.Path file, long size) { }
        @Override public void delete(String objectKey) { objects.remove(objectKey); }
        @Override public byte[] read(String objectKey) {
            var value = objects.get(objectKey);
            if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND);
            return value.clone();
        }
    }
}
