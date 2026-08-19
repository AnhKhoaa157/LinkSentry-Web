/**
 * The one package permitted to make an outbound call to Anthropic.
 *
 * <p>{@link com.lyanhkhoa.linksentry.explanation.provider.AnthropicExplanationProvider}
 * is the sole implementation of {@code explanation.domain.ExplanationProvider}: no
 * other class in this codebase imports the Anthropic SDK. See
 * {@code docs/adr/0005-anthropic-scan-explanation-integration.md} for the scope of
 * this deliberate, narrow exception to the static-analysis-only boundary in
 * {@code docs/adr/0001-static-analysis-only.md} — an exception that applies only to
 * this server-built, evidence-free scan summary, never to a submitted URL.
 */
package com.lyanhkhoa.linksentry.explanation.provider;
