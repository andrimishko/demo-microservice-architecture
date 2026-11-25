# Reusable build for any service module: docker build -f docker/service.Dockerfile --build-arg MODULE=node-registry .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests -pl ${MODULE} -am package && cp ${MODULE}/target/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
