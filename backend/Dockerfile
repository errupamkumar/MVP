# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run --------------------------------------------------------------------
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Kolkata \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Kolkata"
RUN groupadd --system plantride && useradd --system --gid plantride plantride
WORKDIR /app
COPY --from=build /src/target/plant-ride-api.jar app.jar
USER plantride
EXPOSE 8080
# Readiness: GET /actuator/health/readiness (use it from your orchestrator's probe).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
