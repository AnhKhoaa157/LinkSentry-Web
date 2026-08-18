# LinkSentry API — container image.
#
# Two stages so the runtime image carries no build tooling and no source.

# ---------- Build ----------
FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /workspace

# Wrapper and build scripts first: these change rarely, so the dependency
# download below stays cached across source-only edits.
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- Runtime ----------
FROM eclipse-temurin:26-jre-alpine AS runtime

# wget is used by the compose healthcheck.
RUN apk add --no-cache wget \
    && addgroup -S linksentry \
    && adduser -S -G linksentry linksentry

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown -R linksentry:linksentry /app

# Never run the API as root.
USER linksentry

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=local

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
