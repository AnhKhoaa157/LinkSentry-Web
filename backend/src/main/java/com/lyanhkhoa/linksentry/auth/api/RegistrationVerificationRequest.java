package com.lyanhkhoa.linksentry.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Email and one-time code used to complete a pending registration. */
public record RegistrationVerificationRequest(
        @NotBlank(message = "Enter a valid email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Enter a valid email address.")
        String email,
        @NotBlank(message = "Enter the 6-digit verification code.")
        @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit verification code.")
        String code) {}
