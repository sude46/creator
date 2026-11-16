package com.creator.identity;

import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.AccountCredentials;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Role;
import com.creator.identity.infrastructure.security.ActiveAccountFilter;
import com.creator.identity.infrastructure.security.SecurityHandlers;
import com.creator.identity.infrastructure.security.SecurityResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityFailureTest {
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginInfrastructureFailureIsNotReportedAsWrongPassword() throws Exception {
        var handlers = new SecurityHandlers(unavailableQueries(), new SecurityResponseWriter(json));
        var response = new MockHttpServletResponse();
        handlers.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new InternalAuthenticationServiceException("private database details"));
        assertEquals(503, response.getStatus());
        assertEquals("SERVICE_UNAVAILABLE", json.readTree(response.getContentAsString()).get("code").asText());
        assertFalse(response.getContentAsString().contains("private database details"));
    }

    @Test
    void accountCheckFailsClosedDuringDatabaseOutageWithoutDestroyingSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/me");
        var session = request.getSession();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "author_one", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        new ActiveAccountFilter(unavailableQueries(), new SecurityResponseWriter(json))
                .doFilter(request, response, chain);
        assertEquals(503, response.getStatus());
        assertNull(chain.getRequest());
        assertSame(session, request.getSession(false));
        assertNull(request.getAttribute("currentActor"));
    }

    private AccountQueryService unavailableQueries() {
        return new AccountQueryService(new AccountRepository() {
            public Optional<AccountCredentials> findCredentials(String username) {
                throw new UnsupportedOperationException();
            }

            public Optional<AccountSummary> findSummary(String username) {
                throw new DataAccessResourceFailureException("private database details");
            }

            public AccountSummary insert(String username, String passwordHash, Role role) {
                throw new UnsupportedOperationException();
            }
        });
    }
}
