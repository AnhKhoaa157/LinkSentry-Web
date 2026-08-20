package com.lyanhkhoa.linksentry.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the removed email/password/OTP auth surface no longer exists at all — a genuine {@code 404
 * NOT_FOUND}, not a {@code 401} (which would mean the route still exists but now requires something) and
 * not a {@code 500}. Runs against the real security filter chain on the H2-backed {@code test} profile;
 * every route below is rejected before any database access, so no Testcontainers dependency is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemovedAuthRoutesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("every removed auth route returns a genuine 404, not 401 or 500")
    void removedAuthRoutesReturnNotFound() throws Exception {
        assertNotFound(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertNotFound(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertNotFound(get("/api/v1/auth/session"));
        assertNotFound(post("/api/v1/auth/logout"));
        assertNotFound(post("/api/v2/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertNotFound(post("/api/v2/auth/register/verify").contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertNotFound(post("/api/v2/auth/register/resend").contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private void assertNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }
}
