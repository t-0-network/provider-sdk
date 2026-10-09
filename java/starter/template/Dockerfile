# Build stage
FROM eclipse-temurin:21-jdk-noble AS build

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
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

RUN groupadd --system --gid 999 provider \
    && useradd --system --gid 999 --uid 999 --create-home provider

# Copy the installed distribution (includes all dependencies)
COPY --from=build --chown=provider:provider /app/build/install/provider/ ./

USER provider

# Expose the provider server port
EXPOSE 8080

# Run the application
ENTRYPOINT ["./bin/provider"]
