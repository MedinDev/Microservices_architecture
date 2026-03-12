FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY services/common-library/pom.xml /workspace/services/common-library/pom.xml
COPY services/order-service/pom.xml /workspace/services/order-service/pom.xml
COPY services/order-service/src /workspace/services/order-service/src
RUN mvn -f /workspace/pom.xml -pl services/order-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/services/order-service/target/order-service-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
