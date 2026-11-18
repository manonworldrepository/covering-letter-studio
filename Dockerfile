# Stage 1: Build the JAR with Gradle
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime image (distroless, minimal size)
FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=builder /app/build/libs/coveringletter-*.jar app.jar

# Cloud Run uses the PORT environment variable
ENV PORT=8080

# Run with cloud profile to bind to 0.0.0.0
ENTRYPOINT ["java", "-Dspring.profiles.active=cloud", "-jar", "app.jar"]
