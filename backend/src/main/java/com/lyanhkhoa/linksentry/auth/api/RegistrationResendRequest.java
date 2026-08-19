package com.lyanhkhoa.linksentry.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email used to request a fresh code for a pending registration. */
public record RegistrationResendRequest(
        @NotBlank(message = "Enter a valid email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Enter a valid email address.")
        String email) {}
