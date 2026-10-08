# Build stage: Maven Wrapper downloads Maven, so no Maven install is needed.
# Java 21, the version the tests run on (pom.xml, ci-cd.yml, Jenkinsfile): a new major moves all of them together.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Runtime stage: JRE only, non-root user. The label lets deploy.sh and the Jenkinsfile clear the images of old commits.
FROM eclipse-temurin:21-jre
LABEL khoibep.build=true
WORKDIR /app
RUN useradd --system --uid 1001 rms
COPY --from=build /app/target/*.jar app.jar
USER rms
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
