FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/server/build/install/server /app
ENV PORT=8080
ENV DATABASE_URL=jdbc:sqlite:/data/ankiduel.db
VOLUME ["/data"]
EXPOSE 8080
CMD ["/app/bin/server"]
