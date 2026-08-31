FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN ./kotlin package -m network-manager-application -f executable-jar

FROM eclipse-temurin:21-jre-jammy

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.21.0

RUN useradd --system --uid 10001 network
WORKDIR /app
COPY --from=build /workspace/build/tasks/_network-manager-application_executableJarJvm/network-manager-application-jvm-executable.jar app.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN chmod 0444 /app/opentelemetry-javaagent.jar
USER network

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
