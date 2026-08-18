package com.lyanhkhoa.linksentry.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated login payload. */
public record LoginRequest(
        @NotBlank(message = "Enter a valid email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Enter a valid email address.")
        String email,
        @NotBlank(message = "Enter your password.")
        @Size(max = 72, message = "Enter your password.")
        String password) {}
