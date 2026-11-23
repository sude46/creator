package com.creator.identity;

import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class DeviceSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM user_session");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM user_account");
        registration.execute("session_user", "SafePassword123");
    }

    @Test
    void sessionsAreListedAndCanBeRevokedIndividuallyOrInBulk() throws Exception {
        var first = login("session_user");
        var second = login("session_user");
        var list = json.readTree(mvc.perform(get("/api/me/sessions").session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sessionHash").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, countCurrent(list));
        String other = otherId(list);
        mvc.perform(delete("/api/me/sessions/{id}", other).session(first).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(first)).andExpect(status().isOk());
        assertEquals(1, countCurrent(json.readTree(mvc.perform(get("/api/me/sessions").session(first))
                .andReturn().getResponse().getContentAsString())));

        var third = login("session_user");
        mvc.perform(delete("/api/me/sessions/others").session(first).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(third)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(first)).andExpect(status().isOk());
    }

    @Test
    void changingPasswordInvalidatesEverySessionAndAllowsNewLogin() throws Exception {
        var first = login("session_user");
        var second = login("session_user");
        mvc.perform(put("/api/me/password").session(first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SafePassword123\",\"newPassword\":\"NewPassword12345\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "session_user")
                        .param("password", "SafePassword123"))
                .andExpect(status().isUnauthorized());
        var replacement = login("session_user", "NewPassword12345");
        mvc.perform(get("/api/me").session(replacement)).andExpect(status().isOk());
        assertEquals(1L, database.queryForObject(
                "SELECT session_version FROM user_account WHERE username='session_user'", Long.class));
    }

    @Test
    void superAdminResetRevokesTargetSessionsButNotTheAdmin() throws Exception {
        bootstrap.execute("session_admin", "SafePassword123");
        var targetFirst = login("session_user");
        var targetSecond = login("session_user");
        var admin = login("session_admin");
        long target = accounts.findSummary("session_user").orElseThrow().id();
        mvc.perform(put("/api/admin/users/{id}/password", target).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"ResetPassword123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(targetFirst)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(targetSecond)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(admin)).andExpect(status().isOk());
        login("session_user", "ResetPassword123");
    }

    @Test
    void logoutAndRevokeAllRemoveServerSideSessions() throws Exception {
        var first = login("session_user");
        var second = login("session_user");
        mvc.perform(post("/api/auth/logout").session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/sessions").session(first)).andExpect(status().isUnauthorized());
        assertEquals(1L, database.queryForObject(
                "SELECT COUNT(*) FROM user_session session JOIN user_account account ON account.id=session.user_id"
                        + " WHERE account.username='session_user' AND session.revoked_at IS NULL", Long.class));
        mvc.perform(delete("/api/me/sessions/all").session(second).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(second)).andExpect(status().isUnauthorized());
        assertEquals(0L, database.queryForObject(
                "SELECT COUNT(*) FROM user_session session JOIN user_account account ON account.id=session.user_id"
                        + " WHERE account.username='session_user' AND session.revoked_at IS NULL", Long.class));
        login("session_user");
    }

    private MockHttpSession login(String username) throws Exception {
        return login(username, "SafePassword123");
    }
    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private int countCurrent(com.fasterxml.jackson.databind.JsonNode list) {
        int count = 0;
        for (var item : list.get("items")) if (item.get("current").asBoolean()) count++;
        return count;
    }
    private String otherId(com.fasterxml.jackson.databind.JsonNode list) {
        for (var item : list.get("items")) if (!item.get("current").asBoolean()) return item.get("id").asText();
        throw new AssertionError("No other session");
    }
}
