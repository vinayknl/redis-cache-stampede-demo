# --- Build stage -----------------------------------------------------------
# Uses the official Gradle image (bundles a JDK + Gradle) so the project does
# not need to ship a committed Gradle wrapper jar to be buildable in Docker.
FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace

# Copy build files first so Gradle's dependency cache layer is reused when
# only application source changes.
COPY build.gradle settings.gradle gradle.properties ./
COPY src ./src

RUN gradle build -x test --no-daemon

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
