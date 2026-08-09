FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN ./kotlin package -m membership-manager -f executable-jar

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --uid 10001 membership
WORKDIR /app
COPY --from=build /workspace/build/tasks/_membership-manager_executableJarJvm/membership-manager-jvm-executable.jar app.jar
USER membership

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
