/**
 * Application boundary for the optional AI scan explanation.
 *
 * <p>{@link com.lyanhkhoa.linksentry.explanation.application.ExplanationService}
 * is the only place that combines the owner-scoped retained-scan lookup with a
 * call to {@link com.lyanhkhoa.linksentry.explanation.domain.ExplanationProvider}.
 * Every failure — disabled, unowned scan, provider timeout, provider failure, or
 * a malformed provider response — surfaces to the controller as one of exactly
 * two safe exceptions: the existing {@code ScanNotFoundException} or this
 * package's {@link com.lyanhkhoa.linksentry.explanation.application.ExplanationUnavailableException}.
 */
package com.lyanhkhoa.linksentry.explanation.application;
