# Ein Image für die ganze Anwendung: Spring Boot liefert API und das statisch gebaute Frontend aus.

FROM node:22-alpine AS frontend
WORKDIR /build/frontend
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
# Content-Pakete werden als Startinhalt ins JAR gepackt (siehe pom.xml)
COPY content /build/content
COPY --from=frontend /build/frontend/build src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 codepath
WORKDIR /app
COPY --from=backend /build/backend/target/backend-*.jar app.jar
USER codepath
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
