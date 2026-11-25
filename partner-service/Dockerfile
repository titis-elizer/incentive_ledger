FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY partner-service partner-service
COPY incentive-service/pom.xml incentive-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -pl partner-service -am -DskipTests package

FROM eclipse-temurin:21.0.9_10-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/partner-service/target/partner-service-1.0.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=4s --start-period=60s --retries=6 CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]