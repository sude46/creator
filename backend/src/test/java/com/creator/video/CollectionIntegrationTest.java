package com.creator.video;

import static org.junit.jupiter.api.Assertions.*;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.VideoMetadata;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CollectionIntegrationTest {
    @Autowired CollectionService collections;
    @Autowired PublicCollectionService publicCollections;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired AccountRepository accounts;
    @Autowired CategoryService categories;
    @Autowired DraftService drafts;
    @Autowired JdbcTemplate database;
    Actor author; Actor other; Actor admin; long categoryId;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
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
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM user_account");
        registerUser.execute("collection_author", "SafePassword123");
        registerUser.execute("collection_other", "SafePassword123");
        bootstrapAdmin.execute("collection_admin", "SafePassword123");
        author = actor("collection_author"); other = actor("collection_other"); admin = actor("collection_admin");
        categoryId = categories.create(admin, "合集分类", 0).id();
    }

    @Test
    void ownerCreatesAddsUpdatesAndPublishesCollection() {
        long video = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("作品", "", categoryId)).id();
        var created = collections.create(author, "我的合集", "说明", "PUBLIC");
        collections.add(author, created.id(), video);
        assertThrows(BusinessException.class, () -> collections.add(other, created.id(), video));
        var page = collections.list(author, new PageRequest(1, 20));
        assertEquals(1, page.total());
        assertEquals(1, page.items().getFirst().itemCount());
        var updated = collections.update(author, created.id(), created.version(), "新标题", "新说明", "PUBLIC");
        assertEquals("新标题", updated.title());
        var publicList = publicCollections.listByAuthor(author.id(), new PageRequest(1, 20));
        assertEquals(1, publicList.total());
        var detail = publicCollections.get(created.id(), new PageRequest(1, 12));
        assertEquals("新标题", detail.collection().title());
        collections.delete(author, created.id(), updated.version());
        assertEquals(0, collections.list(author, new PageRequest(1, 20)).total());
    }

    @Test
    void ownerReordersItemsAndOtherCannotMoveOrRead() {
        long first = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("一", "", categoryId)).id();
        long second = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("二", "", categoryId)).id();
        long third = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("三", "", categoryId)).id();
        var collection = collections.create(author, "排序合集", "", "PRIVATE");
        collections.add(author, collection.id(), first);
        collections.add(author, collection.id(), second);
        collections.add(author, collection.id(), third);
        assertEquals("排序合集", collections.get(author, collection.id()).title());
        assertThrows(BusinessException.class, () -> collections.ownerItems(other, collection.id()));

        collections.move(author, collection.id(), third, "UP");
        assertEquals(java.util.List.of(first, third, second), collections.ownerItems(author, collection.id())
                .stream().map(com.creator.video.domain.CollectionItem::videoId).toList());
        collections.move(author, collection.id(), third, "UP");
        collections.move(author, collection.id(), third, "UP");
        collections.move(author, collection.id(), first, "DOWN");
        assertEquals(java.util.List.of(third, second, first), collections.ownerItems(author, collection.id())
                .stream().map(com.creator.video.domain.CollectionItem::videoId).toList());
        assertThrows(BusinessException.class, () -> collections.move(other, collection.id(), second, "UP"));
        assertThrows(BusinessException.class, () -> collections.move(author, collection.id(), second, "LEFT"));
    }

    private Actor actor(String username) {
        var a = accounts.findSummary(username).orElseThrow();
        return new Actor(a.id(), username, a.role());
    }
}
