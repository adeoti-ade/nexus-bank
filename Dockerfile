# ---------- Stage 1: Build ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# cache dependencies first (huge CI speedup)
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

# copy full project
COPY . .
RUN mvn -B clean package -DskipTests


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# security: run as non-root
RUN useradd -ms /bin/bash spring
USER spring

# copy jar
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# container-aware JVM tuning
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]