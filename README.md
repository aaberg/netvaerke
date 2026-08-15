# netvaerke

## Web application

The `web` module is the server-rendered netværke application. It uses Ktor and FreeMarker for HTML pages, HTMX for future partial page updates, Hanko for authentication, and NATS to call the membership manager.

For a complete local stack, including PostgreSQL, NATS, local Hanko, the membership manager, and the web application:

```sh
podman compose -f ui/web/docker-compose.yaml up --build web
```

Open [http://localhost:8080](http://localhost:8080). The local Hanko API is available at `http://localhost:8000`.

To run the web application directly on the host, first start the local infrastructure, apply migrations, and start the membership manager:

```sh
podman compose -f ui/web/docker-compose.yaml up -d db nats postgres_hanko
podman compose -f ui/web/docker-compose.yaml run --rm liquibase
podman compose -f ui/web/docker-compose.yaml run --rm hanko-migrate
podman compose -f ui/web/docker-compose.yaml up -d hanko
./kotlin run -m membership-manager -- \
  --config businesslogic/membership-manager/config/local.properties
```

In a second terminal, run the web application with its tracked local configuration:

```sh
./kotlin run -m web -- --config ui/web/config/local.properties
```

The configuration file supplies the local NATS and Hanko URLs. Environment variables override it; in production set `HANKO_API_URL` to the browser-facing Hanko Cloud or custom-domain URL, and `HANKO_VALIDATION_API_URL` to the URL the web server should use to validate sessions.

## Membership manager application

The membership manager runs as a JVM application. It exposes `MembershipManager` through NATS and calls the local profile and tenant access components through IFX `DirectTransport` bindings.

Start PostgreSQL, Liquibase, NATS, and the application with:

```sh
docker compose up --build membership-manager
```

The default NATS operations are:

- `netvaerke.membership-manager.v1.registerProfileWithPersonalTenant`
- `netvaerke.membership-manager.v1.getProfile`

The runtime configuration is supplied through `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `NATS_URL`. `MEMBERSHIP_NATS_SUBJECT`, `MEMBERSHIP_NATS_QUEUE_GROUP`, and `MEMBERSHIP_NATS_TIMEOUT_SECONDS` are optional.

To run the application on the host against local PostgreSQL and NATS, start the infrastructure and apply the migrations:

```sh
docker compose up -d db nats
docker compose run --rm liquibase
```

Then run the application with the tracked local configuration:

```sh
./kotlin run -m membership-manager -- \
  --config businesslogic/membership-manager/config/local.properties
```

Environment variables override values from the configuration file. Omitting `--config` retains the environment-only behavior used by the container.

To build the executable JAR directly:

```sh
./kotlin package -m membership-manager -f executable-jar
```

## NATS integration tests

`NatsTransportTest` runs against a real NATS broker through Testcontainers. Docker is detected automatically.

To retain the broker between local test runs (including separate module test runs), opt in on the developer machine by adding this to `~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

Reusable containers are intentionally not enabled in CI. Each CI run starts a disposable broker.

On Linux with rootless Podman, start its socket and configure Testcontainers before running tests:

```sh
systemctl --user start podman.socket
export DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```
