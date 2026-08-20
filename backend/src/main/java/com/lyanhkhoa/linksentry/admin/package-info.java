/**
 * Administrator-user authentication and the protected {@code /admin} dashboard's backing API.
 *
 * <p>This is not end-user authentication: end users have no account, email, OTP, password, or
 * login flow. It is also distinct from {@code linksentry.admin}'s {@code ADMIN_API_KEY} — an
 * optional curl-only, backend-operator credential for {@code /api/v1/admin/**} defined in {@code
 * common.config.AdminProperties} / {@code common.security.AdminApiKeyFilter}; a browser's
 * {@code ROLE_ADMIN} bearer session is also accepted there. This package instead models a small
 * number of human administrator
 * accounts who log in from a browser to {@code /api/v1/admin-auth/**}, get an opaque, short-lived,
 * revocable bearer session (hashed the same way the former end-user bearer session was), and see a
 * dashboard shell. Bootstrap creates exactly one account from {@code ADMIN_BOOTSTRAP_USERNAME} /
 * {@code ADMIN_BOOTSTRAP_PASSWORD} the first time the backend starts with no admin account yet; see
 * {@code admin.application.AdminBootstrapRunner}.
 */
package com.lyanhkhoa.linksentry.admin;
