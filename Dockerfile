FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

# Fix permission issue
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar --server.port=$PORT"]