package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FolderHistoryIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired PublicVideoService publicVideos;
    @Autowired FavoriteService favorites;
    @Autowired FavoriteFolderService folders;
    @Autowired HistoryService histories;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    Actor author;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_comment_like");
        database.update("DELETE FROM video_comment WHERE parent_id IS NOT NULL");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM favorite_folder_item");
        database.update("DELETE FROM favorite_folder");
        database.update("DELETE FROM view_history");
        database.update("DELETE FROM video_tag_item");
        database.update("DELETE FROM video_tag");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM user_account");
        registration.execute("folder_author", "SafePassword123");
        bootstrap.execute("folder_admin", "SafePassword123");
        author = actor("folder_author");
        admin = actor("folder_admin");
        categoryId = categories.create(admin, "folder", 0).id();
    }

    @Test
    void foldersGroupFavoritesAndRemovingFavoriteClearsMembership() {
        long video = published("folder target");
        var folder = folders.create(author, "  周末   重看 ");
        assertEquals("周末 重看", folder.name());
        folders.addVideo(author, folder.id(), video);
        folders.addVideo(author, folder.id(), video);
        assertTrue(favorites.contains(author, video));
        assertEquals(1, folders.list(author).getFirst().itemCount());
        assertEquals(video, folders.videos(author, folder.id(), new PageRequest(1, 12)).items().getFirst().id());

        var renamed = folders.rename(author, folder.id(), folder.version(), "灵感");
        assertEquals("灵感", renamed.name());
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> folders.rename(author, folder.id(), folder.version(), "过期")));
        assertEquals(ErrorCode.FOLDER_NAME_TAKEN, failure(() -> {
            var other = folders.create(author, "其他");
            folders.rename(author, other.id(), other.version(), "灵感");
        }));
        assertEquals(ErrorCode.INVALID_FOLDER_NAME, failure(() -> folders.create(author, " ")));

        favorites.remove(author, video);
        assertEquals(0, folders.videos(author, folder.id(), new PageRequest(1, 12)).total());
        folders.delete(author, folder.id());
        assertEquals(List.of("其他"), folders.list(author).stream().map(FavoriteFolder::name).toList());
    }

    @Test
    void folderLimitAndOwnershipBoundariesHold() {
        long video = published("owned");
        var mine = folders.create(author, "我的");
        registration.execute("folder_other", "SafePassword123");
        var other = actor("folder_other");
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> folders.addVideo(other, mine.id(), video)));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> folders.videos(other, mine.id(), new PageRequest(1, 12))));
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> folders.delete(other, mine.id())));
        for (int index = 1; index < 20; index++) folders.create(author, "夹" + index);
        assertEquals(ErrorCode.FOLDER_LIMIT_REACHED, failure(() -> folders.create(author, "第二十一")));
    }

    @Test
    void historyDeduplicatesKeepsOrderAndSupportsRemoval() {
        long first = published("history one");
        long second = published("history two");
        publicVideos.recordView(first, author.id());
        publicVideos.recordView(second, author.id());
        publicVideos.recordView(first, author.id());
        var page = histories.list(author, new PageRequest(1, 12));
        assertEquals(2, page.total());
        assertEquals(first, page.items().getFirst().videoId());
        assertNotNull(page.items().getFirst().video());
        histories.remove(author, first);
        assertEquals(1, histories.list(author, new PageRequest(1, 12)).total());
        histories.clear(author);
        assertEquals(0, histories.list(author, new PageRequest(1, 12)).total());
    }

    private long published(String title) {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata(title, "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID() + ".jpg");
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        return id;
    }
    private ErrorCode failure(org.junit.jupiter.api.function.Executable operation) { return assertThrows(BusinessException.class, operation).getCode(); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
}
