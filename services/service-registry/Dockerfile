FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY services/common-library/pom.xml /workspace/services/common-library/pom.xml
COPY services/service-registry/pom.xml /workspace/services/service-registry/pom.xml
COPY services/service-registry/src /workspace/services/service-registry/src
RUN mvn -f /workspace/pom.xml -pl services/service-registry -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/services/service-registry/target/service-registry-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
