# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy parent POM
COPY pom.xml .

# Copy common module
COPY coderank-common/pom.xml coderank-common/
COPY coderank-common/src coderank-common/src

# Copy submission module
COPY coderank-submission/pom.xml coderank-submission/
COPY coderank-submission/src coderank-submission/src

# Step 1: Install parent POM without reactor validation
RUN mvn install -N -DskipTests

# Step 2: Build and install common directly
RUN cd coderank-common && mvn install -DskipTests

# Step 3: Build submission directly
RUN cd coderank-submission && mvn install -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S coderank && adduser -S coderank -G coderank

COPY --from=builder /app/coderank-submission/target/coderank-submission-*.jar app.jar

RUN chown coderank:coderank app.jar

USER coderank

EXPOSE 8083

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]