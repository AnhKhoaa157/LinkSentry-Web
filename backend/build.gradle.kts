plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lyanhkhoa"
version = "0.1.0"
description = "LinkSentry — explainable phishing URL analysis API"

java {
    // Java 26 is the JDK configured for this development environment.
    // See docs/ARCHITECTURE.md §7 for why this differs from the original
    // Java 21 specification; changing the number here is the whole migration.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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

    // --- Persistence foundation (no entity exists yet — see Exercise 10) ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
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

    // Wired for the repository integration tests of Exercise 10. Nothing uses
    // Testcontainers yet, so `./gradlew test` does not require a Docker daemon.
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
