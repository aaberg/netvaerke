# netvaerke

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
