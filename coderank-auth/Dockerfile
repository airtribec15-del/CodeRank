# ── Stage 1: Build ──────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent POM and all module POMs first (layer cache optimization)
COPY pom.xml .
COPY coderank-common/pom.xml coderank-common/
COPY coderank-auth/pom.xml coderank-auth/
COPY coderank-gateway/pom.xml coderank-gateway/
COPY coderank-submission/pom.xml coderank-submission/
COPY coderank-execution/pom.xml coderank-execution/
COPY coderank-result-processor/pom.xml coderank-result-processor/

# Resolve dependencies (cached layer)
RUN mvn dependency:go-offline -pl coderank-common,coderank-auth -am -q

# Copy source for common + auth only
COPY coderank-common/src coderank-common/src
COPY coderank-auth/src coderank-auth/src

# ✅ FIX: plain package — spring-boot-maven-plugin repackages auth automatically, skips common (no plugin there)
RUN mvn clean package -pl coderank-common,coderank-auth -am -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S coderank && adduser -S coderank -G coderank

WORKDIR /app

COPY --from=builder /build/coderank-auth/target/coderank-auth-1.0.0.jar app.jar

RUN chown coderank:coderank app.jar

USER coderank

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]