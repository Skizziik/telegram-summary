# --- Стадия сборки ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src ./src
RUN mvn -q -e -B -DskipTests clean package

# --- Стадия запуска ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/telegram-summary.jar app.jar

# На Render это Cron Job: контейнер стартует, делает один прогон и завершается.
CMD ["java", "-jar", "app.jar", "now"]
