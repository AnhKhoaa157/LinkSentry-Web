plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lyanhkhoa"
version = "0.1.0"
description = "LinkSentry — explainable phishing URL analysis API"

java {
    // Java 26 is the JDK configured for this repository and CI. Keep the
    // toolchain version here aligned with docs/ARCHITECTURE.md section 7.
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

val springdocVersion = "3.1.0"
val testcontainersVersion = "1.21.3"

dependencies {
    // --- Web / API ---
    // Spring Boot 4 renamed the servlet-web starter to `-webmvc`.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("com.google.guava:guava:33.4.8-jre")

    // --- Security ---
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Registration verification email goes out over the Resend HTTPS API via the
    // JDK's built-in java.net.http.HttpClient — see auth.provider.ResendRegistrationCodeSender.
    // No mail-specific dependency is needed.
    // Single-instance, in-memory token-bucket rate limiting. See common.ratelimit.
    implementation("com.bucket4j:bucket4j_jdk17-core:8.19.0")

    // --- AI explanation (optional, disabled by default) ---
    // Official Anthropic Java SDK. Pinned to the latest stable release as of this
    // integration (2026-08-19). Used only by explanation.provider.
    // AnthropicOkHttpClient.builder() is called with an explicit apiKey, timeout,
    // and maxRetries(0) — never AnthropicOkHttpClient.fromEnv(), so the process's
    // own environment cannot silently widen what this one adapter is allowed to do.
    // See docs/adr/0005-anthropic-scan-explanation-integration.md.
    implementation("com.anthropic:anthropic-java:2.54.0")

    // --- Persistence ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- Operations ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- Test ---
    // `-webmvc-test` transitively brings spring-boot-starter-test
    // (JUnit 5, AssertJ, Mockito) plus the MockMvc slice support.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")

    // Repository integration tests use real PostgreSQL through Testcontainers.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")

    // In-memory database for the `test` profile so context-loading tests run
    // without Docker. Real SQL must be verified against PostgreSQL instead.
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
