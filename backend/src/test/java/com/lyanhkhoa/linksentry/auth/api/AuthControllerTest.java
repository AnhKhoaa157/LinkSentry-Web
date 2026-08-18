package com.lyanhkhoa.linksentry.auth.api;

import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.auth.application.EmailAlreadyRegisteredException;
import com.lyanhkhoa.linksentry.auth.application.InvalidCredentialsException;
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

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("registration validation rejects a short password without reaching the service")
    void registrationValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("login validation rejects an invalid email without reaching the service")
    void loginValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("duplicate registration uses a safe conflict envelope")
    void duplicateRegistrationIsSafe() throws Exception {
        given(authService.register(org.mockito.ArgumentMatchers.any(RegisterRequest.class)))
                .willThrow(new EmailAlreadyRegisteredException());
        String email = "duplicate-email-sentinel@example.com";
        String password = "duplicate-password-sentinel";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("An account already exists for this email address."))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString(email))))
                .andExpect(content().string(not(containsString(password))));
    }

    @Test
    @DisplayName("login failure does not echo email, password, or internal details")
    void loginFailureDoesNotLeakCredentials() throws Exception {
        given(authService.login(org.mockito.ArgumentMatchers.any(LoginRequest.class)))
                .willThrow(new InvalidCredentialsException());
        String password = "correct-horse";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."))
                .andExpect(content().string(not(containsString(password))))
                .andExpect(content().string(not(containsString("person@example.com"))));
    }

    @Test
    @DisplayName("successful auth response is the only controller response that contains the one-time bearer value")
    void authResponseShape() throws Exception {
        given(authService.login(org.mockito.ArgumentMatchers.any(LoginRequest.class)))
                .willReturn(new AuthResponse(
                        "test-only-bearer", "Bearer", Instant.parse("2026-08-19T12:00:00Z"),
                        new AuthUserResponse("person@example.com")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-only-bearer"))
                .andExpect(jsonPath("$.user.email").value("person@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
