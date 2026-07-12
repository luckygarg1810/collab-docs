# Multi-stage build for Spring Boot application

# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first (for better layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Give execute permission
RUN chmod +x mvnw

# Download dependencies (cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds, run tests in CI/CD)
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime stage
# Using debian-slim so apt-get is available (needed for pandoc install)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install pandoc for high-quality DOCX→HTML conversion
# pandoc is a native binary (~15 MB), no JVM overhead
RUN apt-get update && \
    apt-get install -y --no-install-recommends pandoc && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev}", \
    "-jar", \
    "app.jar"]
