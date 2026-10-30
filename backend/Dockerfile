FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S hms && adduser -S hms -G hms
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER hms
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
