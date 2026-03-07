# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
COPY --from=shared_proto signer.proto ./src/main/proto/signer.proto
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:25.0.2_10-jre

RUN apt-get update && \
    apt-get install -y tini && \
    rm -rf /var/lib/apt/lists/* 

WORKDIR /app

COPY --from=builder /build/target/*.jar /app/orchestrator.jar

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]

CMD ["java", "-jar", "/app/orchestrator.jar"]