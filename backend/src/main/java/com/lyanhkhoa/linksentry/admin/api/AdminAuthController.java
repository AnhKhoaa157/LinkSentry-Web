package com.lyanhkhoa.linksentry.admin.api;

import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal cookie-free administrator login and current-session endpoints, entirely separate from
 * {@code /api/v1/admin/**} (the device-licensing surface accepting either an administrator session or
 * the operator-only {@code ADMIN_API_KEY}).
 */
@RestController
@RequestMapping("/api/v1/admin-auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public AdminAuthResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

    @GetMapping("/session")
    public AdminSessionResponse currentSession(Authentication authentication) {
        return adminAuthService.currentSession(requireAdmin(authentication));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        adminAuthService.logout(requireAdmin(authentication));
    }

    /**
     * {@code SecurityConfig} requires {@code admin.security.AdminSessionAuthenticationFilter#ADMIN_AUTHORITY}
     * for this route, so a present-but-wrong-type principal (e.g. {@code license.security.LicensedDeviceContext})
     * should be structurally unreachable here — this is defense-in-depth, and also what a filter-disabled
     * web slice falls back to. {@code AccessDeniedException}, not {@code InvalidAdminCredentialsException}:
     * this is never a login failure, and reusing that exception's message would misdescribe an
     * authenticated-but-wrong-domain request as a bad username or password.
     */
    private static AdminIdentity requireAdmin(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AdminIdentity identity) {
            return identity;
        }
        throw new AccessDeniedException("An administrator session is required for this resource.");
    }
}
