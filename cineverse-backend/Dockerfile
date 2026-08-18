# Multi-stage build: the Maven/JDK toolchain used to compile never ships in
# the final image, only the built jar + a JRE.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# pom.xml copied and dependencies resolved before the source tree, so a
# source-only change reuses this layer's Docker cache instead of
# re-downloading every dependency on every build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# -DskipTests: the Testcontainers-backed integration test suite needs a real
# Docker daemon, which isn't available inside a build stage (no
# Docker-in-Docker here) — this isn't a substitute for CI's own
# `mvn clean test` (.github/workflows/ci.yml), which already runs the full
# suite before an image would ever be built from a given commit.
RUN mvn -B -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/*.jar app.jar
# LocalStorageService creates ./uploads relative to the working directory at
# startup (app.storage.upload-dir, default "uploads") — /app is root-owned
# by default, so the app crashed on boot under the non-root spring user
# until this chown, caught by actually running the built image locally
# rather than assuming a non-root USER alone was enough.
RUN chown -R spring:spring /app
USER spring:spring

# Documents the default; the app actually binds to $PORT if the platform
# sets one (see application.yml's server.port), which is very likely a
# different value than 8080 on most PaaS platforms.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
