# ---------- Сборка ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Отдельный слой на зависимости: кэш переживает правки в src/
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Рантайм ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Боевой профиль: без Swagger и без подробностей ошибок
ENV SPRING_PROFILES_ACTIVE=prod

USER app
EXPOSE 8080

# Контейнер считается здоровым, если Actuator отвечает статусом UP (HTTP 200)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
