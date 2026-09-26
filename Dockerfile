# syntax=docker/dockerfile:1.7
# One Dockerfile for every service: docker build --build-arg MODULE=appointment-service .
# No local JDK or Maven needed; the Maven cache is shared between builds via a BuildKit cache mount.

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${MODULE} -am -DskipTests package \
 && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 torqline
WORKDIR /app
COPY --from=build /app.jar app.jar
USER torqline
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
