# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-jammy AS build
RUN apt-get update && apt-get install --no-install-recommends --yes curl && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY kotlin project.yaml ./
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin,sharing=locked \
    ./kotlin --version
COPY . .
RUN --mount=type=cache,target=/root/.cache/JetBrains/Kotlin,sharing=locked \
    ./kotlin package -m membership-manager-application -f executable-jar

FROM eclipse-temurin:25-jre-jammy

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.31.1

RUN useradd --system --uid 10001 membership
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN chmod 0444 /app/opentelemetry-javaagent.jar
COPY --from=build /workspace/build/tasks/_membership-manager-application_executableJarJvm/membership-manager-application-jvm-executable.jar app.jar
USER membership

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
