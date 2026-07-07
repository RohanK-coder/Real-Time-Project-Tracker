FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package || true
EXPOSE 8080
CMD ["./mvnw", "spring-boot:run"]
