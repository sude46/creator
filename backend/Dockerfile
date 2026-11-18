# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY maven-settings.xml pom.xml ./
COPY src ./src
RUN mvn -B -s maven-settings.xml -Dmaven.repo.local=/tmp/m2 -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /work \
    && chown -R app:app /app /work
COPY --from=build /src/target/creator-platform-*.jar /app/app.jar
WORKDIR /app
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
