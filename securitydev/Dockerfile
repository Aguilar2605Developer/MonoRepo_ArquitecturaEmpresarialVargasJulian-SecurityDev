# ---------- Etapa 1: Build ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copiamos primero los archivos de Gradle para cachear dependencias
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Descarga de dependencias (se cachea si no cambian los archivos de arriba)
RUN ./gradlew dependencies --no-daemon || true

# Copiamos el resto del código fuente
COPY src ./src

# Compilamos el jar, sin correr los tests (se corren en CI aparte)
RUN ./gradlew bootJar --no-daemon -x test

# ---------- Etapa 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copiamos el jar generado en la etapa de build
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080



ENTRYPOINT ["java", "-jar", "app.jar"]