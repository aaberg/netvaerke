# Agent Guide

## Project shape

- This is a Kotlin CLI/Amper project; use the checked-in `./kotlin` wrapper, not Gradle.
- `project.yaml` is the module manifest. Modules are `access/*` PostgreSQL-backed IFX libraries, `utilities/ifx` (Direct/NATS transports), `utilities/test-support` (Testcontainers helpers), `businesslogic/*` libraries, `subsystems/*` backend applications, and `ui/web`.
- Applications are `subsystems/membership-manager-application` (`netvaerke.application.membership.MainKt`), `subsystems/network-manager-application` (`netvaerke.application.network.MainKt`), and `ui/web` (`netvaerke.application.web.MainKt`). The membership manager exposes `MembershipManager` over NATS and binds profile/tenant access locally through `DirectTransport`; the network manager exposes `NetworkManager` over NATS and binds contact, tenant, and authorization services locally; the web app renders its Ktor/FreeMarker UI and consumes backend services over NATS.
- Every backend service is split into two modules: keep its contracts, DTOs, mappers, and business implementations in `businesslogic/<service>` as a `jvm/lib`, and put its runnable composition root in `subsystems/<service>-application` as a `jvm/app`. The application module owns `MainKt`, runtime configuration, Dockerfile, NATS/database/OpenTelemetry wiring, and application integration tests; it depends on the business library using `../../businesslogic/<service>`.
- When adding a service, register both modules in `project.yaml`, keep application-only dependencies out of the business library, and update Compose Dockerfile paths, local configuration commands, and documentation. Consumers such as `ui/web` must depend on the business library, never the executable application module.
- The web module must depend on and use manager contracts only. Do not add `access/*` dependencies or imports to `ui/web`; manager APIs expose their own serializable DTOs and map access models internally.
- Kotlin 2.4.10 modules use `allWarningsAsErrors: true`; warnings break compilation.

## Commands

- Run all tests: `./kotlin test`
- Run one module: `./kotlin test -m membership-manager` (replace the module name; see `./kotlin show modules`).
- Run a focused class: `./kotlin test -m membership-manager-application --include-classes '*ApplicationConfigTest'`.
- Run a focused test method with `--include-test=<fully-qualified-test-method>`; use `./kotlin test --help` for the exact FQN format.
- Run built-in checks: `./kotlin check` (currently the `tests` check).
- Build the membership executable JAR: `./kotlin package -m membership-manager-application -f executable-jar`.
- Run the web app locally: `./kotlin run -m web -- --config ui/web/config/local.properties`.
- Build the web executable JAR: `./kotlin package -m web -f executable-jar`.

## Runtime and tests

- Use rootless Podman for local containers: `podman compose` delegates Compose-file parsing to the installed provider. The provider supports `include`.
- The application needs PostgreSQL and NATS. Start infrastructure and migrations with `podman compose up -d db nats` followed by `podman compose run --rm liquibase`; then run with `./kotlin run -m membership-manager-application -- --config subsystems/membership-manager-application/config/local.properties`.
- Containerized web deployment is composed by the web module: `podman compose -f ui/web/docker-compose.yaml up --build web`. Its base Compose include runs Liquibase before the membership manager and starts local Hanko for browser authentication.
- On SELinux-enforcing hosts, bind mounts used by containers must be relabeled: use `:z` when a path is shared by services and `:Z` when it is private to one container. Mount configuration files read-only, for example `./hanko-config.yaml:/etc/hanko/config.yaml:ro,z`.
- Testcontainers integration tests start real PostgreSQL/NATS containers. Local reuse requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`; CI intentionally does not reuse containers.
- With rootless Podman, start `systemctl --user start podman.socket`, then set `DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"` and `TESTCONTAINERS_RYUK_DISABLED=true` before tests.
- Configuration files are loaded with `--config <path>`, then environment variables override file values. Required variables are `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `NATS_URL`; optional NATS settings default to the membership subject and a 5-second timeout.

## Database changes

- Add Liquibase changesets under `liquibase/changelog/` and include them from `liquibase/changelog-root.yaml`; the Compose `liquibase` service applies the root changelog.
