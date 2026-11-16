package com.creator.identity;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.application.AuthenticationWorkBudget;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Role;
import com.creator.identity.web.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityIntegrationTest.HashingObservation.class)
class IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired AccountRepository accounts;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired AuthenticationWorkBudget workBudget;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video");
        database.update("DELETE FROM user_account");
    }

    @Test
    void registrationHashesPasswordAndAlwaysCreatesUser() throws Exception {
        register("author_one");
        var credentials = accounts.findCredentials("author_one").orElseThrow();
        assertEquals(Role.USER, credentials.account().role());
        assertNotEquals("SafePassword123", credentials.passwordHash());
        assertTrue(encoder.matches("SafePassword123", credentials.passwordHash()));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"another\",\"password\":\"SafePassword123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(accounts.findSummary("another").isEmpty());
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
        bootstrapAdmin.execute("admin_one", "SafePassword123");
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

    @Test
    void usernameIsCanonicalAcrossRegistrationLoginAndUniqueness() throws Exception {
        register("Author_One");
        var session = login("AUTHOR_ONE");
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("author_one"));
        var error = assertThrows(BusinessException.class,
                () -> registerUser.execute("AUTHOR_ONE", "AnotherPassword123"));
        assertEquals(ErrorCode.USERNAME_TAKEN, error.getCode());
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM user_account", Long.class));
    }

    @Test
    void internalUseCasesEnforcePoliciesWithoutControllerValidation() {
        var usernameError = assertThrows(BusinessException.class,
                () -> registerUser.execute("invalid name", "SafePassword123"));
        assertEquals(ErrorCode.INVALID_USERNAME, usernameError.getCode());
        var passwordError = assertThrows(BusinessException.class,
                () -> registerUser.execute("internal_user", "123456789012"));
        assertEquals(ErrorCode.WEAK_PASSWORD, passwordError.getCode());
        var bytesError = assertThrows(BusinessException.class,
                () -> registerUser.execute("internal_user", "汉".repeat(30) + "abc123"));
        assertEquals(ErrorCode.WEAK_PASSWORD, bytesError.getCode());
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM user_account", Long.class));
    }

    @Test
    void bootstrapCannotPromoteAnExistingUserOrOverwriteAdminPassword() {
        registerUser.execute("ordinary", "SafePassword123");
        var conflict = assertThrows(BusinessException.class,
                () -> bootstrapAdmin.execute("ORDINARY", "DifferentPassword123"));
        assertEquals(ErrorCode.ADMIN_BOOTSTRAP_CONFLICT, conflict.getCode());
        var user = accounts.findCredentials("ordinary").orElseThrow();
        assertEquals(Role.USER, user.account().role());
        assertTrue(encoder.matches("SafePassword123", user.passwordHash()));

        var first = bootstrapAdmin.execute("studio_admin", "SafePassword123");
        var second = bootstrapAdmin.execute("STUDIO_ADMIN", "DifferentPassword123");
        assertEquals(first.id(), second.id());
        assertTrue(encoder.matches("SafePassword123",
                accounts.findCredentials("studio_admin").orElseThrow().passwordHash()));
        database.update("UPDATE user_account SET status = 'DISABLED' WHERE username = ?", "studio_admin");
        var disabled = assertThrows(BusinessException.class,
                () -> bootstrapAdmin.execute("studio_admin", "SafePassword123"));
        assertEquals(ErrorCode.ADMIN_BOOTSTRAP_CONFLICT, disabled.getCode());
    }

    @Test
    void roleChangeInvalidatesPreviouslyAuthenticatedAdmin() throws Exception {
        bootstrapAdmin.execute("studio_admin", "SafePassword123");
        var session = login("studio_admin");
        database.update("UPDATE user_account SET role = 'USER' WHERE username = ?", "studio_admin");
        mvc.perform(get("/api/admin/overview").session(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        assertTrue(session.isInvalid());
        var newSession = login("studio_admin");
        mvc.perform(get("/api/admin/overview").session(newSession)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void frameworkFailuresPreserveHttpStatusAndSafeErrorShape() throws Exception {
        register("author_one");
        var session = login("author_one");
        mvc.perform(get("/api/does-not-exist").session(session)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(post("/api/me").session(session).with(csrf())).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void malformedAndInvalidRequestsExposeNoCredentials() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertFalse(new RegisterRequest("someone", "SensitivePassword123").toString().contains("SensitivePassword123"));
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneAccount() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<ErrorCode> registration = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent registration start timed out");
            }
            try {
                registerUser.execute("racing_user", "SafePassword123");
                return null;
            } catch (BusinessException exception) {
                return exception.getCode();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(registration);
            var second = executor.submit(registration);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, (firstResult == null ? 1 : 0) + (secondResult == null ? 1 : 0));
            assertTrue(firstResult == ErrorCode.USERNAME_TAKEN || secondResult == ErrorCode.USERNAME_TAKEN);
        }
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM user_account WHERE username = 'racing_user'", Long.class));
    }

    @Test
    void saturatedWorkBudgetRejectsLoginAndRegistrationAndRecovers() throws Exception {
        var occupied = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Callable<Boolean> holdPermit = () -> workBudget.execute(() -> {
            occupied.countDown();
            try {
                return release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(holdPermit);
            var second = executor.submit(holdPermit);
            try {
                assertTrue(occupied.await(5, TimeUnit.SECONDS));
                mvc.perform(post("/api/auth/login").with(csrf()).param("username", "unknown_user")
                        .param("password", "SafePassword123"))
                        .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.code").value("AUTH_BUSY"));
                mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("budget_user", "SafePassword123"))))
                        .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.code").value("AUTH_BUSY"));
                mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
                assertTrue(accounts.findSummary("budget_user").isEmpty());
            } finally {
                release.countDown();
            }
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
        }
        register("budget_user");
        var session = login("budget_user");
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HashingObservation {
        @Bean
        @Primary
        PasswordEncoder observingPasswordEncoder(@Qualifier("passwordEncoder") PasswordEncoder delegate) {
            return new PasswordEncoder() {
                public String encode(CharSequence password) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                            "Password hashing must not hold a database transaction");
                    return delegate.encode(password);
                }

                public boolean matches(CharSequence password, String hash) {
                    return delegate.matches(password, hash);
                }

                public boolean upgradeEncoding(String hash) {
                    return delegate.upgradeEncoding(hash);
                }
            };
        }
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
