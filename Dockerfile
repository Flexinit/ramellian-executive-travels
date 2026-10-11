# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -DskipTests -q

# Run stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/ramellian-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p uploads/vehicles uploads/documents uploads/contracts \
    && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
