/**
 * Application services for device bootstrap/status and admin-only license management. {@link
 * com.lyanhkhoa.linksentry.license.application.DeviceService} is the public-facing boundary; {@link
 * com.lyanhkhoa.linksentry.license.application.LicenseAdminService} is reached only through {@code
 * license.api} routes gated by {@code common.security.AdminApiKeyFilter}.
 */
package com.lyanhkhoa.linksentry.license.application;
