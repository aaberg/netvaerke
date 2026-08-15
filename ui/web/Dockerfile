FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN ./kotlin package -m web -f executable-jar

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --uid 10002 web
WORKDIR /app
COPY --from=build /workspace/build/tasks/_web_executableJarJvm/web-jvm-executable.jar app.jar
USER web

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
