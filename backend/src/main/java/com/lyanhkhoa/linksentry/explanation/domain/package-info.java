/**
 * Framework-free contracts for the optional AI scan explanation.
 *
 * <p>{@link com.lyanhkhoa.linksentry.explanation.domain.ScanSummary} is the entire
 * privacy boundary between a scan and an outbound provider call: it structurally
 * excludes every raw or identifying field. This package must not import Spring,
 * Jackson, an HTTP client, or the Anthropic SDK — see {@code explanation.provider}
 * for the one adapter allowed to do that.
 */
package com.lyanhkhoa.linksentry.explanation.domain;
