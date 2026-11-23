# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilar con el JDK completo ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Primero solo el pom y el wrapper: Docker cachea esta capa y no vuelve a descargar
# dependencias si solo cambió el código fuente.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Las pruebas ya corren en CI; aquí solo empaquetamos
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/mollet-*.jar extract --layers --launcher --destination extracted

# ---------- Etapa 2: imagen final, solo con el JRE (más liviana y con menos superficie de ataque) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# No correr como root
RUN addgroup -S mollet && adduser -S mollet -G mollet
USER mollet

# Capas de Spring Boot: dependencias (cambian poco) separadas de nuestro código (cambia seguido)
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

# MaxRAMPercentage: usa hasta el 75 % de la RAM del contenedor (los planes gratis tienen 512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-Xss512k", "org.springframework.boot.loader.launch.JarLauncher"]
