package com.lyanhkhoa.linksentry.explanation.domain;

/**
 * The bounded set of languages an {@link ExplanationProvider} may be asked to
 * write {@link AiAdvisory#summary()} and {@link AiAdvisory#recommendedActions()}
 * in.
 *
 * <p>This is the explanation domain's own type, deliberately independent of
 * {@code common.api.ResponseLocale}: {@code explanation.domain} must not depend
 * on an HTTP/API-layer type (see {@code package-info}). {@code
 * explanation.application.ExplanationService} maps the negotiated {@code
 * ResponseLocale} to this enum before calling a provider, so a provider — and
 * the outbound request it builds — only ever sees this bounded, server-owned
 * value, never raw {@code Accept-Language} header text.
 */
public enum ExplanationLanguage {
    ENGLISH,
    VIETNAMESE
}
