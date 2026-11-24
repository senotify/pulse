# syntax=docker/dockerfile:1
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle gradle dependencies --no-daemon -q > /dev/null
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon -x test \
    && cp build/libs/pulse-api-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pulse && adduser -S pulse -G pulse
USER pulse
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
# Sized for a small shared VPS; override with JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
