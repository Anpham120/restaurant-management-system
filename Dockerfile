# Build: Maven wrapper trong image JDK; chạy: image JRE, user thường.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/restaurant-core-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+KhongTonTai", "-jar", "/app/app.jar"]
