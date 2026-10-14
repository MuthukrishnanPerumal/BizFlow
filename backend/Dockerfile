# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/bizflow-server-*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system bizflow \
    && useradd --system --gid bizflow --home-dir /app --shell /usr/sbin/nologin bizflow

COPY --from=build /workspace/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
USER bizflow

# Render and similar hosts inject PORT. Fall back to SERVER_PORT or 8080.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-${SERVER_PORT:-8080}}"]