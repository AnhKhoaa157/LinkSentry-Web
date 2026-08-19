package com.lyanhkhoa.linksentry.auth.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthV2Controller.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("version two registration returns an acknowledgement instead of a bearer token")
    void registrationStartsEmailVerification() throws Exception {
        given(authService.register(org.mockito.ArgumentMatchers.any(RegisterRequest.class)))
                .willReturn(new RegistrationStartedResponse(
                        "A verification code was sent to your email address.",
                        Instant.parse("2026-08-19T12:10:00Z")));

        mockMvc.perform(post("/api/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A verification code was sent to your email address."))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-19T12:10:00Z"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
