/**
 * Device-installation and admin-granted licensing domain model.
 *
 * <p>Replaces email/password/OTP authentication. A {@link com.lyanhkhoa.linksentry.license.domain.Device}
 * is an anonymous installation identity; a {@link com.lyanhkhoa.linksentry.license.domain.License} is
 * created and granted only by an administrator; a
 * {@link com.lyanhkhoa.linksentry.license.domain.DeviceLicenseAssignment} is the (possibly historical)
 * record of one device being granted one license. These types are framework-free; JPA mapping lives in
 * {@code license.persistence}.
 */
package com.lyanhkhoa.linksentry.license.domain;
