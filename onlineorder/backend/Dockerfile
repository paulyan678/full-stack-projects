FROM gradle:8.14.3-jdk21-alpine AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S onlineorder && adduser -S onlineorder -G onlineorder
WORKDIR /app
COPY --from=build /workspace/build/libs/onlineorder-1.0.0.jar app.jar
USER onlineorder
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
