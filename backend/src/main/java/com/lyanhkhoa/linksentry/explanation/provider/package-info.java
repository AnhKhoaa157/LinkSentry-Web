/**
 * The one package permitted to make an outbound call to DeepSeek.
 *
 * <p>{@link com.lyanhkhoa.linksentry.explanation.provider.DeepSeekExplanationProvider}
 * is the sole implementation of {@code explanation.domain.ExplanationProvider}: no
 * other class in this codebase builds or sends a DeepSeek request. See
 * {@code docs/adr/0005-deepseek-scan-explanation-integration.md} for the scope of
 * this deliberate, narrow exception to the static-analysis-only boundary in
 * {@code docs/adr/0001-static-analysis-only.md} — an exception that applies only to
 * this server-built, evidence-free scan summary, never to a submitted URL.
 */
package com.lyanhkhoa.linksentry.explanation.provider;
