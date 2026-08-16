/**
 * Centralised exception translation.
 *
 * <p>Every failure leaves the application through
 * {@link com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler}, so the
 * error contract is defined in exactly one place and controllers stay free of
 * try/catch blocks.
 *
 * <p>Domain exceptions (for example an invalid-URL failure from the normalizer)
 * are declared in their own domain package and mapped here — the domain must not
 * know about HTTP status codes.
 */
package com.lyanhkhoa.linksentry.common.exception;
