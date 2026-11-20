package com.creator.catalog;

import com.creator.catalog.application.CategoryService;
import com.creator.catalog.domain.CategoryRepository;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class CategoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired RegisterUser registerUser;
    @Autowired AccountRepository accounts;
    @Autowired CategoryService categories;
    @Autowired CategoryRepository repository;
    @Autowired ObjectMapper json;
    Actor admin;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
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
        bootstrapAdmin.execute("catalog_admin", "SafePassword123");
        var account = accounts.findSummary("catalog_admin").orElseThrow();
        admin = new Actor(account.id(), account.username(), account.role());
    }

    @Test
    void adminCrudWritesAuditWithActorAndRequestTrace() throws Exception {
        var session = login("catalog_admin");
        var result = mvc.perform(post("/api/admin/categories").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" 短剧 \",\"sortOrder\":10}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("短剧")).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(result.getResponse().getHeader("X-Request-Id"), database.queryForObject(
                "SELECT request_id FROM operation_log WHERE target_id = ?", String.class, id));
        assertEquals(admin.id(), database.queryForObject("SELECT actor_id FROM operation_log", Long.class));
        mvc.perform(put("/api/admin/categories/{id}", id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("纪录片", false, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/categories").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/categories").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].enabled").value(false));
        mvc.perform(delete("/api/admin/categories/{id}", id).param("version", "1").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(repository.findById(id).isEmpty());
        assertEquals(3L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    @Test
    void roleAndCsrfBoundariesApplyToEveryManagementEndpoint() throws Exception {
        registerUser.execute("catalog_user", "SafePassword123");
        var session = login("catalog_user");
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/categories").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test\",\"sortOrder\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/categories/1").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("test", true, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/categories/1").param("version", "0").session(session).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").session(login("catalog_admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        var user = new Actor(admin.id(), "catalog_user", Role.USER);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> categories.create(user, "test", 0)).getCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> categories.managed(user, new PageRequest(1, 20))).getCode());
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    @Test
    void canonicalUniquenessAndInternalValidationProtectWrites() {
        var first = categories.create(admin, " ＡＢＣ ", 0);
        assertEquals("ABC", first.name());
        assertEquals(ErrorCode.CATEGORY_NAME_TAKEN, assertThrows(BusinessException.class,
                () -> categories.create(admin, "abc", 0)).getCode());
        var second = categories.create(admin, "Other", 0);
        assertEquals(ErrorCode.CATEGORY_NAME_TAKEN, assertThrows(BusinessException.class,
                () -> categories.update(admin, second.id(), 0, "Abc", 0, true)).getCode());
        for (String invalid : new String[] {" ", "a\nb", "a".repeat(33)}) {
            assertEquals(ErrorCode.INVALID_CATEGORY, assertThrows(BusinessException.class,
                    () -> categories.create(admin, invalid, 0)).getCode());
        }
        assertEquals(ErrorCode.INVALID_CATEGORY, assertThrows(BusinessException.class,
                () -> categories.create(admin, "valid", 10001)).getCode());
        assertEquals("Other", repository.findById(second.id()).orElseThrow().name());
        assertEquals(2L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    @Test
    void paginationIsBoundedAndOrderingIsStable() throws Exception {
        categories.create(admin, "Later", 10);
        categories.create(admin, "First", 0);
        categories.create(admin, "Second", 0);
        var session = login("catalog_admin");
        mvc.perform(get("/api/categories").session(session).param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Second"));
        for (String page : new String[] {"0", "1001"}) {
            mvc.perform(get("/api/categories").session(session).param("page", page))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        }
        mvc.perform(get("/api/categories").session(session).param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleWritesAndMissingRecordsDoNotProduceAudit() throws Exception {
        var category = categories.create(admin, "Original", 0);
        categories.update(admin, category.id(), 0, "Updated", 0, true);
        var session = login("catalog_admin");
        mvc.perform(put("/api/admin/categories/{id}", category.id()).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("Lost", true, 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        mvc.perform(delete("/api/admin/categories/{id}", category.id()).param("version", "0")
                .session(session).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/categories/999999").param("version", "0")
                .session(session).with(csrf())).andExpect(status().isNotFound());
        assertEquals("Updated", repository.findById(category.id()).orElseThrow().name());
        assertEquals(2L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    @Test
    void concurrentUpdatesHaveOneWinnerAndOneAuditEntry() throws Exception {
        var category = categories.create(admin, "Original", 0);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    categories.update(admin, category.id(), 0, "Winner" + index, 0, true);
                    return "OK";
                } catch (BusinessException exception) {
                    return exception.getCode().name();
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = new java.util.ArrayList<String>();
            for (var task : tasks) outcomes.add(task.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("OK"::equals).count());
            assertEquals(1, outcomes.stream().filter("STALE_WRITE"::equals).count());
        }
        assertEquals(1L, repository.findById(category.id()).orElseThrow().version());
        assertEquals(2L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    @Test
    void auditFailureRollsBackCreateUpdateAndDelete() {
        var missingAdmin = new Actor(Long.MAX_VALUE, "missing", Role.ADMIN);
        assertThrows(DataIntegrityViolationException.class, () -> categories.create(missingAdmin, "Rollback", 0));
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM video_category", Long.class));
        var category = categories.create(admin, "Original", 0);
        assertThrows(DataIntegrityViolationException.class,
                () -> categories.update(missingAdmin, category.id(), 0, "Rollback", 0, false));
        assertEquals("Original", repository.findById(category.id()).orElseThrow().name());
        assertThrows(DataIntegrityViolationException.class, () -> categories.delete(missingAdmin, category.id(), 0));
        assertTrue(repository.findById(category.id()).isPresent());
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class));
    }

    private String updateBody(String name, boolean enabled, long version) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "sortOrder", 0, "enabled", enabled, "version", version));
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username)
                .param("password", "SafePassword123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
