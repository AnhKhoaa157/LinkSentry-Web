package com.lyanhkhoa.linksentry.analysis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces the offline, deterministic, I/O-free boundary documented in AGENTS.md,
 * docs/SECURITY_BOUNDARY.md, and ADR 0001: {@code com.lyanhkhoa.linksentry.analysis..} must
 * never gain a dependency capable of network access, runtime I/O, framework coupling, or
 * persistence coupling. This is a guardrail against accidental dependency drift, not proof of
 * every runtime behavior.
 */
class AnalysisPackageBoundaryTest {

    private static final String ANALYSIS_PACKAGE = "com.lyanhkhoa.linksentry.analysis..";

    private static final JavaClasses ANALYSIS_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ANALYSIS_PACKAGE);

    @Test
    void analysisPackageDoesNotDependOnNetworkResolutionApis() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ANALYSIS_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .haveNameMatching(
                                "java\\.net\\.(InetAddress|InetSocketAddress|Inet4Address|Inet6Address"
                                        + "|Socket|ServerSocket|DatagramSocket|MulticastSocket"
                                        + "|URLConnection|HttpURLConnection|JarURLConnection"
                                        + "|NetworkInterface|SocketException)")
                        .as(
                                "analysis package classes must not depend on java.net resolver/socket"
                                        + " APIs (InetAddress and friends)")
                        .because(
                                "AGENTS.md and ADR 0001 require the analysis domain to stay"
                                        + " static-string-only: no DNS resolution, no socket I/O, no"
                                        + " outbound connection");
        rule.check(ANALYSIS_CLASSES);
    }

    @Test
    void analysisPackageDoesNotDependOnNetworkOrIoOrFileSystemApis() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ANALYSIS_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "java.net.http..", "java.io..", "java.nio.file..", "java.nio.channels..")
                        .as(
                                "analysis package classes must not depend on java.net.http, java.io,"
                                        + " java.nio.file, or java.nio.channels")
                        .because(
                                "the analysis domain must stay offline and free of runtime I/O per"
                                        + " AGENTS.md and docs/SECURITY_BOUNDARY.md");
        rule.check(ANALYSIS_CLASSES);
    }

    @Test
    void analysisPackageDoesNotDependOnSpringOrPersistenceFrameworks() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ANALYSIS_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "jakarta.persistence..",
                                "javax.persistence..",
                                "org.hibernate..")
                        .as(
                                "analysis package classes must not depend on Spring or JPA/persistence"
                                        + " frameworks")
                        .because(
                                "analysis.domain's package-info requires the analysis domain to stay"
                                        + " framework-free; Spring wiring and persistence live outside"
                                        + " it");
        rule.check(ANALYSIS_CLASSES);
    }
}
