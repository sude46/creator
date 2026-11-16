package com.creator.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired UserAccountMapper accounts;
    @Autowired IdentityService identity;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM user_account");
    }

    @Test
    void registrationHashesPasswordAndAlwaysCreatesUser() throws Exception {
        register("author_one");
        var account = accounts.findByUsername("author_one");
        assertEquals("USER", account.getRole());
        assertNotEquals("SafePassword123", account.getPasswordHash());
        assertTrue(encoder.matches("SafePassword123", account.getPasswordHash()));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"another\",\"password\":\"SafePassword123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertNull(accounts.findByUsername("another"));
    }

    @Test
    void duplicateUsernameAndWeakPasswordAreRejected() throws Exception {
        register("author_one");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest("author_one", "SafePassword123"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest("weak_user", "123456789012"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void anonymousAndCsrfProtectionAreEnforced() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").param("username", "someone").param("password", "secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEstablishesSessionAndLogoutInvalidatesIt() throws Exception {
        register("author_one");
        var session = login("author_one");
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("author_one"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/overview").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountLosesExistingSession() throws Exception {
        register("author_one");
        var session = login("author_one");
        database.update("UPDATE user_account SET status = 'DISABLED' WHERE username = ?", "author_one");
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "author_one")
                .param("password", "SafePassword123")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAccessAdminEndpointAndWrongPasswordIsRejected() throws Exception {
        identity.create("admin_one", "SafePassword123", "ADMIN");
        var session = login("admin_one");
        mvc.perform(get("/api/admin/overview").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin_one")
                .param("password", "wrong")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void browserCanUseRealCsrfTokenAcrossAuthentication() throws Exception {
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        var token = json.readTree(tokenResult.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/register").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest("real_browser", "SafePassword123"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", "real_browser").param("password", "SafePassword123"))
                .andExpect(status().isOk());
        var refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var newToken = json.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(session)
                .header(newToken.get("headerName").asText(), newToken.get("token").asText()))
                .andExpect(status().isNoContent());
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(username, "SafePassword123"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
    }

    private MockHttpSession login(String username) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", username)
                .param("password", "SafePassword123")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
