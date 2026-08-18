package com.lyanhkhoa.linksentry.auth.api;

import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.auth.application.InvalidCredentialsException;
import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Minimal cookie-free account and current-session endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/session")
    public SessionResponse currentSession(Authentication authentication) {
        return authService.currentSession(requireUser(authentication));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        authService.logout(requireUser(authentication));
    }

    private static AuthenticatedUser requireUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        // Normally the security chain produces the 401 before this method. This
        // guard keeps the controller safe in a filter-disabled web slice too.
        throw new InvalidCredentialsException();
    }
}
