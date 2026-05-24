# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# ── Copy parent POM first ──
COPY pom.xml                        ./pom.xml
COPY coderank-common/pom.xml        ./coderank-common/pom.xml
COPY coderank-execution/pom.xml     ./coderank-execution/pom.xml

# ── Pre-fetch dependencies ──
RUN mvn -pl coderank-common,coderank-execution \
        dependency:go-offline \
        --no-transfer-progress \
        -q 2>/dev/null || true

# ── Copy source ──
COPY coderank-common/src   ./coderank-common/src
COPY coderank-execution/src ./coderank-execution/src

# ── Build ──
RUN mvn -pl coderank-common,coderank-execution -am \
        -DskipTests \
        --no-transfer-progress \
        package 2>/dev/null || \
    mvn -pl coderank-common,coderank-execution -am \
        -DskipTests \
        --no-transfer-progress \
        package

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
#   IMPORTANT: The execution service spawns Docker containers at runtime via
#   the Docker Java SDK (unix:///var/run/docker.sock).
#   The Docker socket is mounted at runtime via docker-compose — it is NOT
#   baked into this image.  The host Docker engine is used (Docker-in-Docker
#   is intentionally avoided for performance and security reasons).
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install Docker CLI so the JVM can shell out if needed,
# and curl for health-check probes
RUN apk add --no-cache docker-cli curl

# Non-root user — must be in the 'docker' group to access the socket
# The GID 999 matches the docker group on most Linux hosts;
# override via docker-compose if your host uses a different GID.
RUN addgroup -g 999 docker 2>/dev/null || true && \
    addgroup -S appgroup && \
    adduser  -S appuser -G appgroup && \
    adduser  appuser docker

WORKDIR /app

COPY --from=builder \
     /workspace/coderank-execution/target/coderank-execution-*.jar \
     app.jar

USER appuser

# Execution service port
EXPOSE 8084

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
