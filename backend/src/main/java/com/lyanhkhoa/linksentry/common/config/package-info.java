/**
 * Typed configuration and framework wiring.
 *
 * <p>Configuration is bound to validated {@code @ConfigurationProperties}
 * records rather than read through scattered {@code @Value} annotations, so that
 * a misconfigured deployment fails at startup with a precise message instead of
 * misbehaving at request time.
 *
 * <p>The analyzer's {@code @Bean} definitions wiring rules, normalizer and
 * scorer belong here or in the analysis feature — never as annotations on the
 * domain classes themselves, which must stay framework-free.
 */
package com.lyanhkhoa.linksentry.common.config;
