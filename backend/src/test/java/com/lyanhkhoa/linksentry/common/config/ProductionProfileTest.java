package com.lyanhkhoa.linksentry.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.common.ratelimit.ClientAddressProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the application with the production profile layered over the H2 {@code test} profile, so
 * a production-only setting that fails to bind fails here instead of during a deploy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProductionProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientAddressProperties clientAddressProperties;

    @Test
    @DisplayName("production keys rate limits on Cloudflare's visitor address header")
    void productionTrustsCloudflareVisitorHeader() {
        assertThat(clientAddressProperties.trustedHeader()).isEqualTo("CF-Connecting-IP");
    }

    @Test
    @DisplayName("production serves neither the OpenAPI document nor Swagger UI")
    void productionDoesNotServeApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
