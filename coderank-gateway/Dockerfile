# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy parent POM
COPY pom.xml .

# Copy common module
COPY coderank-common/pom.xml coderank-common/
COPY coderank-common/src coderank-common/src

# Copy gateway module
COPY coderank-gateway/pom.xml coderank-gateway/
COPY coderank-gateway/src coderank-gateway/src

# Step 1: Install parent POM without reactor validation
RUN mvn install -N -DskipTests

# Step 2: Build common directly — cd into module, bypass parent reactor entirely
RUN cd coderank-common && mvn install -DskipTests

# Step 3: Build gateway directly
RUN cd coderank-gateway && mvn install -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S coderank && adduser -S coderank -G coderank

COPY --from=builder /app/coderank-gateway/target/coderank-gateway-*.jar app.jar

RUN chown coderank:coderank app.jar

USER coderank

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]