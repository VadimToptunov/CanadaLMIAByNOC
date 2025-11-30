# Multi-stage build for Spring Boot application
FROM --platform=linux/arm64 maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM --platform=linux/arm64 eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health check (Debian-based image)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r spring && useradd -r -g spring spring

# Create directory for downloaded datasets and give ownership to spring user
RUN mkdir -p /app/savedDatasets/NOCs && chown -R spring:spring /app

USER spring:spring

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check using curl (available in alpine)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

