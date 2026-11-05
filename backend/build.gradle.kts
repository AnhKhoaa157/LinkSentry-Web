plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lyanhkhoa"
version = "0.1.0"
description = "LinkSentry — explainable phishing URL analysis API"

java {
    // Java 25 (LTS) is the JDK for this repository, CI, and the Docker image.
    // Keep this toolchain, backend/Dockerfile, and backend-ci.yml aligned.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val springdocVersion = "3.2.0"
val testcontainersVersion = "1.21.4"

dependencies {
    // --- Web / API ---
    // Spring Boot 4 renamed the servlet-web starter to `-webmvc`.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("com.google.guava:guava:33.7.2-jre")
    implementation("com.ibm.icu:icu4j:78.3")

    // --- Security ---
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Single-instance, in-memory token-bucket rate limiting. See common.ratelimit.
    implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")

    // --- AI explanation (optional, disabled by default) ---
    // No dedicated dependency: explanation.provider.DeepSeekExplanationProvider
    // calls DeepSeek's OpenAI-compatible chat-completions endpoint with the JDK's
    // own java.net.http.HttpClient and the Jackson ObjectMapper already provided by
    // spring-boot-starter-webmvc. See
    // docs/adr/0005-deepseek-scan-explanation-integration.md.

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

    // Architecture boundary test for the analysis package: test-only, zero production
    // runtime impact. Apache 2.0. Transitive footprint is just archunit-junit5-api ->
    // archunit (core, ASM shaded internally) + slf4j-api. See
    // docs/LinkSentry-Domain-Analysis-Roadmap.md M2 for the dependency assessment.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")

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

// Offline accuracy check: scores a labeled url,label CSV (the ml/ dataset schema)
// with the production rules from application.yml and prints precision, recall,
// and per-rule firing rates. Never prints a URL. Test-only tooling, so it is not
// part of the boot jar. Pass your own dataset with --args="path/to/file.csv".
tasks.register<JavaExec>("evaluateRules") {
    group = "verification"
    description = "Measures the production rule engine against a labeled URL dataset."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.lyanhkhoa.linksentry.common.config.RuleEngineEvaluation"
    args("../ml/data/sample_dataset.csv")
}
