# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
COPY --from=shared_proto dsg.proto ./src/main/proto/dsg.proto
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:25.0.2_10-jre

RUN apt-get update && \
    apt-get install -y tini curl && \
    rm -rf /var/lib/apt/lists/* 

WORKDIR /app

COPY --from=builder /build/target/*.jar /app/orchestrator.jar

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/orchestrator.jar"]