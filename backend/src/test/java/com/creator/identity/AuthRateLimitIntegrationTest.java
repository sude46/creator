package com.creator.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "creator.security.auth-rate-limit.max-attempts=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void realSecurityChainLimitsBothRegistrationAndLoginWithoutTrustingForwardedIp() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "missing_account")
                .param("password", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Forwarded-For", "192.0.2.99")
                .param("username", "missing_account").param("password", "wrong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
