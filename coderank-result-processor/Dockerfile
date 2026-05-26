# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copy parent POM and all module POMs first (layer-cache friendly)
COPY pom.xml .
COPY coderank-common/pom.xml coderank-common/pom.xml
COPY coderank-auth/pom.xml coderank-auth/pom.xml
COPY coderank-gateway/pom.xml coderank-gateway/pom.xml
COPY coderank-problem/pom.xml coderank-problem/pom.xml
COPY coderank-submission/pom.xml coderank-submission/pom.xml
COPY coderank-execution/pom.xml coderank-execution/pom.xml
COPY coderank-result-processor/pom.xml coderank-result-processor/pom.xml

# Resolve dependencies without source (cached layer)
RUN mvn dependency:go-offline -pl coderank-common,coderank-result-processor -am -q

# Copy source and build
COPY coderank-common/src coderank-common/src
COPY coderank-result-processor/src coderank-result-processor/src

RUN mvn clean package -pl coderank-common,coderank-result-processor -am \
    -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S coderank && adduser -S coderank -G coderank
USER coderank

COPY --from=build /workspace/coderank-result-processor/target/coderank-result-processor-*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]