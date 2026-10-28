FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system cloudvault && useradd --system --gid cloudvault --home-dir /home/cloudvault --create-home cloudvault
WORKDIR /app
COPY --from=build --chown=cloudvault:cloudvault /workspace/target/cloudvault-api.jar /app/cloudvault-api.jar
ENV HOME=/home/cloudvault
USER cloudvault:cloudvault
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/cloudvault-api.jar"]
