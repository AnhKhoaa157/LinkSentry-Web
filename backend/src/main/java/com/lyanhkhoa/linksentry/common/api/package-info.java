/**
 * Wire types shared by more than one feature.
 *
 * <p>Only types that genuinely cross feature boundaries belong here — currently
 * just the error envelope. Feature-specific request and response DTOs live with
 * their feature (for example {@code scan.api}), so that changing one endpoint's
 * contract does not ripple through unrelated code.
 */
package com.lyanhkhoa.linksentry.common.api;
