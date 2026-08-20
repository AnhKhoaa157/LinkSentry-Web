package com.lyanhkhoa.linksentry.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Fixed JSON response for a genuinely authenticated caller whose principal has the wrong domain
 * authority for the route — a licensed device on an admin-only route, or an administrator on a
 * device-only route. Spring Security only reaches this handler for a non-anonymous principal
 * ({@code ExceptionTranslationFilter} routes an anonymous caller to {@link ApiAuthenticationEntryPoint}
 * instead, keeping "never authenticated" at {@code 401} and "authenticated but wrong domain" at
 * {@code 403} — the same distinction {@code common.exception.GlobalExceptionHandler}'s own {@code
 * AccessDeniedException} handler makes for the identical case thrown directly from a controller.
 */
public final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "You do not have permission to access this resource.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // No detail beyond the fixed safe message: never the required authority, the principal's
        // actual type, or the route.
        OBJECT_MAPPER.writeValue(
                response.getWriter(), ErrorResponse.of("FORBIDDEN", MESSAGE, UUID.randomUUID().toString()));
    }
}
