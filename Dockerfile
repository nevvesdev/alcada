FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home alcada
WORKDIR /app
COPY --from=build /workspace/target/*.jar application.jar
USER alcada
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=container
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]