package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.MyBatisFavoriteRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
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
@Import(FavoriteIntegrationTest.Fixtures.class)
class FavoriteIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired FavoriteService favorites;
    @Autowired PublicationService publications;
    @Autowired ReviewService reviews;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired FailingFavorites repository;
    @Autowired MockMvc mvc;
    Actor author;
    Actor viewer;
    Actor other;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        repository.fail = false;
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_account");
        registration.execute("favorite_author", "SafePassword123");
        registration.execute("favorite_viewer", "SafePassword123");
        registration.execute("favorite_other", "SafePassword123");
        bootstrap.execute("favorite_admin", "SafePassword123");
        author = actor("favorite_author");
        viewer = actor("favorite_viewer");
        other = actor("favorite_other");
        admin = actor("favorite_admin");
        categoryId = categories.create(admin, "favorites", 0).id();
    }

    @Test
    void addAndRemoveAreIdempotentAndRepeatedAddKeepsOriginalOrdering() {
        long id = published("first");
        favorites.add(viewer, id);
        long savedId = database.queryForObject("SELECT id FROM video_favorite", Long.class);
        var savedAt = list(viewer).items().getFirst().createdAt();
        favorites.add(viewer, id);
        assertEquals(1, list(viewer).total());
        assertEquals(savedId, database.queryForObject("SELECT id FROM video_favorite", Long.class));
        assertEquals(savedAt, list(viewer).items().getFirst().createdAt());
        assertTrue(favorites.contains(viewer, id));
        favorites.remove(viewer, id);
        favorites.remove(viewer, id);
        assertFalse(favorites.contains(viewer, id));
        assertEquals(0, list(viewer).total());
        favorites.add(viewer, id);
        assertTrue(database.queryForObject("SELECT id FROM video_favorite", Long.class) > savedId);
    }

    @Test
    void usersAndAdminsOnlyReadOrRemoveTheirOwnRelationships() {
        long id = published("shared");
        favorites.add(viewer, id);
        favorites.add(other, id);
        assertEquals(0, list(admin).total());
        assertFalse(favorites.contains(admin, id));
        favorites.remove(admin, id);
        favorites.remove(viewer, id);
        assertTrue(favorites.contains(other, id));
        assertEquals(1, list(other).total());
        assertEquals(0, list(viewer).total());
        favorites.add(author, id);
        assertTrue(favorites.contains(author, id));
    }

    @Test
    void privateMissingAndIncompleteVideosCannotBeAddedEvenByOwnerOrAdmin() {
        long draft = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("private", "", categoryId)).id();
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(author, draft)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(admin, draft)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(viewer, Long.MAX_VALUE)));
        long id = published("missing cover");
        database.update("DELETE FROM media_asset WHERE video_id=? AND kind='COVER'", id);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(viewer, id)));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM video_favorite", Integer.class));
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> favorites.add(viewer, 0)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> favorites.remove(viewer, -1)));
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> favorites.contains(viewer, 0)));
    }

    @Test
    void offlineEditedAndBlockedWorksBecomePlaceholdersAndReappearOnlyWhenPublic() {
        long id = published("public title");
        favorites.add(viewer, id);
        publications.offline(author, id, 2);
        assertPlaceholder(id);
        assertTrue(favorites.contains(viewer, id));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(viewer, id)));
        publications.publish(author, id, 3);
        assertEquals("public title", list(viewer).items().getFirst().video().title());
        reviews.update(author, id, 4, new VideoMetadata("private changed title", "secret description", categoryId));
        assertPlaceholder(id);
        reviews.submit(author, id, 5);
        reviews.decide(admin, id, 6, 2, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        publications.block(admin, id, 7, new ModerationReason("private moderation reason"));
        assertPlaceholder(id);
        favorites.remove(viewer, id);
        assertEquals(0, list(viewer).total());
    }

    @Test
    void inactiveAuthorDeletedVideoAndStaleGenerationHideEveryContentField() {
        long id = published("sensitive title");
        favorites.add(viewer, id);
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", author.id());
        assertPlaceholder(id);
        database.update("UPDATE user_account SET status='ACTIVE' WHERE id=?", author.id());
        database.update("UPDATE video SET processing_generation=2 WHERE id=?", id);
        assertPlaceholder(id);
        database.update("UPDATE video SET processing_generation=1,publication_status='OFFLINE',deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        assertPlaceholder(id);
        favorites.remove(viewer, id);
        assertFalse(favorites.contains(viewer, id));
    }

    @Test
    void disabledCategoryDoesNotHideAlreadyApprovedFavorites() {
        long id = published("existing");
        var category = categories.managed(admin, new PageRequest(1, 20)).items().getFirst();
        categories.update(admin, categoryId, category.version(), category.name(), 0, false);
        favorites.add(viewer, id);
        assertNotNull(list(viewer).items().getFirst().video());
    }

    @Test
    void stablePaginationIncludesUnavailableEntriesWithoutLeakingOtherUsersTotals() {
        long first = published("first");
        long second = published("second");
        long third = published("third");
        for (long id : List.of(first, second, third)) favorites.add(viewer, id);
        favorites.add(other, first);
        publications.offline(author, second, 2);
        var page = favorites.list(viewer, new PageRequest(1, 2));
        assertEquals(3, page.total());
        assertEquals(List.of(third, second), page.items().stream().map(FavoriteItem::videoId).toList());
        assertNotNull(page.items().getFirst().video());
        assertNull(page.items().getLast().video());
        assertEquals(first, favorites.list(viewer, new PageRequest(2, 2)).items().getFirst().videoId());
        assertTrue(favorites.list(viewer, new PageRequest(3, 2)).items().isEmpty());
        assertEquals(1, list(other).total());
    }

    @Test
    void internalCallsRejectDisabledAccountsChangedRolesAndMissingActor() {
        long id = published("identity");
        favorites.add(viewer, id);
        database.update("UPDATE user_account SET role='ADMIN' WHERE id=?", viewer.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> favorites.add(viewer, id)));
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> favorites.remove(viewer, id)));
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> favorites.contains(viewer, id)));
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> list(viewer)));
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", viewer.id());
        assertEquals(ErrorCode.SESSION_INVALID, failure(() -> favorites.remove(viewer, id)));
        assertEquals(ErrorCode.UNAUTHENTICATED, failure(() -> favorites.add(null, id)));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM video_favorite", Integer.class));
    }

    @Test
    void storageFailureRollsBackAddAndRemoveRatherThanReturningSuccess() {
        long id = published("rollback");
        repository.fail = true;
        assertThrows(DataAccessResourceFailureException.class, () -> favorites.add(viewer, id));
        assertEquals(0, list(viewer).total());
        repository.fail = false;
        favorites.add(viewer, id);
        repository.fail = true;
        assertThrows(DataAccessResourceFailureException.class, () -> favorites.remove(viewer, id));
        assertTrue(favorites.contains(viewer, id));
    }

    @Test
    void httpRequiresLoginCsrfAndReturnsNoPrivateContentForUnavailableVideo() throws Exception {
        long id = published("original");
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
        var session = login(viewer);
        mvc.perform(put("/api/favorites/{id}", id).session(session)).andExpect(status().isForbidden());
        mvc.perform(put("/api/favorites/{id}", id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/favorites/{id}", id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/favorites/{id}", id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.favorited").value(true));
        mvc.perform(get("/api/favorites").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").isString()).andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].video.title").value("original"));
        reviews.update(author, id, 2, new VideoMetadata("never leak this title", "secret", categoryId));
        var response = mvc.perform(get("/api/favorites").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(false)).andExpect(jsonPath("$.items[0].video").isEmpty()).andReturn();
        String body = response.getResponse().getContentAsString();
        for (String hidden : List.of("never leak", "secret", "sources/", "reviewRound", "authorId", "title")) assertFalse(body.contains(hidden));
        mvc.perform(get("/api/favorites").session(session).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/favorites").session(session).param("page", "1001")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/favorites/{id}", id).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/favorites/{id}", id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/favorites/{id}", id).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/favorites/{id}", Long.MAX_VALUE).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/favorites/{id}", Long.MAX_VALUE).session(session)).andExpect(jsonPath("$.favorited").value(false));
    }

    @RepeatedTest(3)
    void concurrentDuplicateAddsLeaveExactlyOneRelationship() throws Exception {
        long id = published("race");
        var results = race(() -> { favorites.add(viewer, id); return "added"; }, () -> { favorites.add(viewer, id); return "added"; });
        assertEquals(List.of("added", "added"), results);
        assertEquals(1, list(viewer).total());
    }

    @Test
    void concurrentAddAndOfflineNeverExposeUnavailableContentAfterBothCommit() throws Exception {
        long id = published("race visibility");
        var results = race(() -> { favorites.add(viewer, id); return "added"; }, () -> publications.offline(author, id, 2));
        assertTrue(results.getFirst().equals("added") || results.getFirst() == ErrorCode.NOT_FOUND);
        var page = list(viewer);
        if (!page.items().isEmpty()) assertNull(page.items().getFirst().video());
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> favorites.add(other, id)));
    }

    private long published(String title) {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata(title, "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID());
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID());
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        return id;
    }
    private void assertPlaceholder(long id) {
        var item = list(viewer).items().getFirst();
        assertEquals(id, item.videoId());
        assertNull(item.video());
    }
    private PageResult<FavoriteItem> list(Actor actor) { return favorites.list(actor, new PageRequest(1, 20)); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
    private ErrorCode failure(org.junit.jupiter.api.function.Executable operation) { return assertThrows(BusinessException.class, operation).getCode(); }
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
    static class FailingFavorites implements FavoriteRepository {
        private final MyBatisFavoriteRepository delegate;
        volatile boolean fail;
        FailingFavorites(MyBatisFavoriteRepository delegate) { this.delegate = delegate; }
        public void add(long userId, long videoId) { delegate.add(userId, videoId); check(); }
        public void remove(long userId, long videoId) { delegate.remove(userId, videoId); check(); }
        public boolean contains(long userId, long videoId) { return delegate.contains(userId, videoId); }
        public List<Favorite> findPage(long userId, PageRequest page) { return delegate.findPage(userId, page); }
        public long count(long userId) { return delegate.count(userId); }
        private void check() { if (fail) throw new DataAccessResourceFailureException("injected favorite failure"); }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary FailingFavorites favoriteRepository(MyBatisFavoriteRepository delegate) { return new FailingFavorites(delegate); }
    }
}
