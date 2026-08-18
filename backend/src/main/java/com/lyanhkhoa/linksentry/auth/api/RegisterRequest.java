package com.lyanhkhoa.linksentry.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated registration payload. Passwords are consumed only by the auth service. */
public record RegisterRequest(
        @NotBlank(message = "Enter a valid email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Enter a valid email address.")
        String email,
        @NotBlank(message = "Enter a password.")
        @Size(min = 8, max = 72, message = "Use a password between 8 and 72 characters.")
        String password) {}
