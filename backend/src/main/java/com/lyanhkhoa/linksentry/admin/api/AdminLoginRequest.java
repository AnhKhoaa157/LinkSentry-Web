package com.lyanhkhoa.linksentry.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated admin login payload. */
public record AdminLoginRequest(
        @NotBlank(message = "Enter your username.") @Size(max = 120, message = "Enter your username.")
                String username,
        @NotBlank(message = "Enter your password.") @Size(max = 72, message = "Enter your password.")
                String password) {}
