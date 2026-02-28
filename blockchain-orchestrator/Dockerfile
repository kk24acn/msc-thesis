FROM eclipse-temurin:25.0.2_10-jre

RUN apt-get update && \
    apt-get install -y tini && \
    rm -rf /var/lib/apt/lists/* 

WORKDIR /app

COPY target/*.jar orchestrator.jar

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]

CMD ["java", "-jar", "/app/orchestrator.jar"]