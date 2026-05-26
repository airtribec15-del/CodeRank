# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY coderank-common/pom.xml coderank-common/
COPY coderank-common/src coderank-common/src
COPY coderank-execution/pom.xml coderank-execution/
COPY coderank-execution/src coderank-execution/src

RUN mvn install -N -DskipTests
RUN cd coderank-common && mvn install -DskipTests -Dmaven.test.skip=true
RUN cd coderank-execution && mvn install -DskipTests -Dmaven.test.skip=true

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app


# GID in-place without deleting it (avoids the delgroup-with-members failure).
RUN apk add --no-cache docker-cli su-exec shadow

RUN addgroup -S coderank \
 && adduser  -S coderank -G coderank \
 && addgroup -S docker \
 && addgroup coderank docker

COPY --from=builder /app/coderank-execution/target/coderank-execution-*.jar app.jar

RUN chown coderank:coderank app.jar

EXPOSE 8084

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1

# groupmod modifies the GID in-place — no need to remove members first.
# The coderank user's docker membership is preserved automatically.
ENTRYPOINT ["/bin/sh", "-c", "\
  if [ -S /var/run/docker.sock ]; then \
    SOCK_GID=$(stat -c '%g' /var/run/docker.sock); \
    CUR_GID=$(getent group docker | cut -d: -f3); \
    if [ \"$SOCK_GID\" != \"$CUR_GID\" ]; then \
      EXISTING=$(getent group \"$SOCK_GID\" | cut -d: -f1); \
      if [ -n \"$EXISTING\" ]; then \
        addgroup coderank \"$EXISTING\" 2>/dev/null || true; \
      else \
        groupmod -g \"$SOCK_GID\" docker; \
      fi; \
    fi; \
  fi; \
  exec su-exec coderank java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -jar app.jar \
"]