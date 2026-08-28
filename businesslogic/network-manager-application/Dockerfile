FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN ./kotlin package -m network-manager-application -f executable-jar

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --uid 10001 network
WORKDIR /app
COPY --from=build /workspace/build/tasks/_network-manager-application_executableJarJvm/network-manager-application-jvm-executable.jar app.jar
USER network

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
