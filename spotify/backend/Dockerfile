FROM gradle:8.10.2-jdk17-alpine AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle --no-daemon test installDist

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spotify && adduser -S spotify -G spotify
WORKDIR /app
COPY --from=build /workspace/build/install/spotify-backend ./
RUN chown -R spotify:spotify /app
USER spotify
ENV PORT=8080
ENV PUBLIC_BASE_URL=http://10.0.2.2:8080
EXPOSE 8080
CMD ["bin/spotify-backend"]
