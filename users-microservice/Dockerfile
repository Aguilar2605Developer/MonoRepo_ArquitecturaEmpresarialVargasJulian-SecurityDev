# ---------- Etapa 1: Build ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

# ---------- Etapa 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8686

ENTRYPOINT ["java", "-jar", "app.jar"]