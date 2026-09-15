# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies separately from source so code-only changes don't
# re-download the internet on every build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/sampleMCP.jar app.jar

# Render sets $PORT at runtime; application.properties reads it via server.port.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
