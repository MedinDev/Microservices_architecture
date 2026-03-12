FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY services/common-library/pom.xml /workspace/services/common-library/pom.xml
COPY services/payment-service/pom.xml /workspace/services/payment-service/pom.xml
COPY services/payment-service/src /workspace/services/payment-service/src
RUN mvn -f /workspace/pom.xml -pl services/payment-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/services/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
