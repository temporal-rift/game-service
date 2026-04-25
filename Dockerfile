FROM maven:3.9.13-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q -Dspotless.skip=true -Dcheckstyle.skip=true -Denforcer.skip=true
COPY src ./src
RUN mvn package -DskipTests -q -Dspotless.skip=true -Dcheckstyle.skip=true

FROM eclipse-temurin:25
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
