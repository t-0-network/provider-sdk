# Build stage
FROM eclipse-temurin:17-jdk-noble AS build

WORKDIR /app

# Copy gradle wrapper and build files first for better caching
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew installDist --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-noble

WORKDIR /app

# Copy the installed distribution (includes all dependencies)
COPY --from=build /app/build/install/provider/ ./

# Copy .env file if it exists (for local development)
COPY .env* ./

# Expose the provider server port
EXPOSE 8080

# Run the application
ENTRYPOINT ["./bin/provider"]
