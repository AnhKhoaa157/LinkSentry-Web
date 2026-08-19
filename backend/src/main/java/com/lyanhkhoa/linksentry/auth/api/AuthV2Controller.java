package com.lyanhkhoa.linksentry.auth.api;

import com.lyanhkhoa.linksentry.auth.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Versioned email-verification registration flow; v1 registration remains compatible. */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    private final AuthService authService;

    public AuthV2Controller(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegistrationStartedResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/register/verify")
    public AuthResponse verifyRegistration(@Valid @RequestBody RegistrationVerificationRequest request) {
        return authService.verifyRegistration(request);
    }

    @PostMapping("/register/resend")
    public RegistrationStartedResponse resendRegistrationCode(
            @Valid @RequestBody RegistrationResendRequest request) {
        return authService.resendRegistrationCode(request);
    }
}
