FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/r2d-backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx280m", "-Xss256k", "-XX:MaxMetaspaceSize=128m", "-XX:CompressedClassSpaceSize=32m", "-XX:ReservedCodeCacheSize=48m", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
