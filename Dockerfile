# --- STAGE 1: Build the application ---
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Copy only the Maven build files first so the dependency layer is cached
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw

# Cached unless pom.xml changes
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests

# --- STAGE 2: Run the application ---
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

CMD ["sh", "-c", "java -jar app.jar --server.port=$PORT"]
