# --- 1. Lépés: ÉPÍTÉS (Build Stage) ---
# Mavenes képet használunk a fordításhoz
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Először csak a pom.xml-t másoljuk (cache miatt gyorsabb lesz később)
COPY pom.xml .
# Letöltjük a függőségeket
RUN mvn dependency:go-offline

# Most belemásoljuk a teljes forráskódot
COPY src ./src

# Lefordítjuk a projektet (tesztek kihagyásával a gyorsaság miatt)
RUN mvn clean package -DskipTests

# --- 2. Lépés: FUTTATÁS (Run Stage) ---
# Egy könnyű Java környezetet használunk a futtatáshoz
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp

# Csak az elkészült JAR fájlt másoljuk át az 1. lépésből (build stage)
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]