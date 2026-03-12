FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY services/common-library/pom.xml /workspace/services/common-library/pom.xml
COPY services/notification-service/pom.xml /workspace/services/notification-service/pom.xml
COPY services/notification-service/src /workspace/services/notification-service/src
RUN mvn -f /workspace/pom.xml -pl services/notification-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/services/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
