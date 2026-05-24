# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
#   Uses the same Maven + Temurin-21 Alpine image as gateway / auth / problem
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# ── Copy parent POM first (better layer caching) ──
COPY pom.xml                          ./pom.xml
COPY coderank-common/pom.xml          ./coderank-common/pom.xml
COPY coderank-submission/pom.xml      ./coderank-submission/pom.xml

# ── Pre-fetch all dependencies (cached unless pom changes) ──
RUN mvn -pl coderank-common,coderank-submission \
        dependency:go-offline \
        --no-transfer-progress \
        -q 2>/dev/null || true

# ── Copy source ──
COPY coderank-common/src   ./coderank-common/src
COPY coderank-submission/src ./coderank-submission/src

# ── Build — skip tests (run separately in CI) ──
RUN mvn -pl coderank-common,coderank-submission -am \
        -DskipTests \
        --no-transfer-progress \
        package 2>/dev/null || \
    mvn -pl coderank-common,coderank-submission -am \
        -DskipTests \
        --no-transfer-progress \
        package

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
#   Minimal JRE Alpine image — no JDK, no Maven, no build tools
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder \
     /workspace/coderank-submission/target/coderank-submission-*.jar \
     app.jar

# Switch to non-root user
USER appuser

# Submission service port
EXPOSE 8083

# JVM tuning for containers:
#   - UseContainerSupport  → honours Docker memory limits
#   - MaxRAMPercentage=75  → use up to 75% of container memory for heap
#   - ExitOnOutOfMemoryError → crash fast instead of hanging
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
