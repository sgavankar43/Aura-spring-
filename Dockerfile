# Multi-stage Dockerfile for Spring Boot 3 (Java 21)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Maven Wrapper and POM from server directory
COPY server/.mvn/ .mvn/
COPY server/mvnw server/pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

# Copy server source code and package application
COPY server/src/ ./src/
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy compiled JAR from build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

