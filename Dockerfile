# Compila con Maven y corre solo el jar sobre un JRE liviano.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /data/uploads && chown -R app /data
COPY --from=build /app/target/carbonbox-java-1.0.0.jar app.jar
USER app
ENV CARBONBOX_STORAGE_DIR=/data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
