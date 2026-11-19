# HMS backend container (used by Railway; works on any Docker host).
# Build stage compiles the jar with the project's Maven Wrapper; tests run in CI/locally, not here
# (they need the hms_test MySQL schema).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 hms && mkdir -p /data/uploads /app/logs && chown -R hms /data /app
WORKDIR /app
COPY --from=build /src/target/hms-backend.jar app.jar
USER hms
# Listen on all interfaces inside the container (the platform's proxy is the only way in);
# size the heap from the container's memory limit.
ENV SPRING_PROFILES_ACTIVE=prod \
    HMS_SERVER_ADDRESS=0.0.0.0 \
    HMS_SERVER_PORT=8080 \
    HMS_UPLOAD_DIR=/data/uploads \
    HMS_LOG_DIR=/app/logs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
