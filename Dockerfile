# ---------------------------------------------------------------------------
# Single-container build: React is compiled and bundled INTO the Spring Boot
# jar's static resources. One deploy, one URL, and zero CORS configuration
# because the UI and API share an origin.
# ---------------------------------------------------------------------------

# Stage 1 - build the React app
FROM node:20-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2 - build the Spring Boot jar with the UI inside it
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
# Copy the POM first so dependency resolution is cached across code changes.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/dist ./src/main/resources/static
RUN mvn -B -q clean package -DskipTests

# Stage 3 - runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# Render injects PORT; Spring reads it via server.port in application.yml.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
