# Agent Guide

## Project shape

- This is a Kotlin CLI/Amper project; use the checked-in `./kotlin` wrapper, not Gradle.
- `project.yaml` is the module manifest. Modules are `access/*` PostgreSQL-backed IFX libraries, `utilities/ifx` (Direct/NATS transports), `utilities/test-support` (Testcontainers helpers), and `businesslogic/*`.
- `businesslogic/membership-manager` is the only application (`netvaerke.application.membership.MainKt`); it exposes `MembershipManager` over NATS and binds profile/tenant access locally through `DirectTransport`.
- Kotlin 2.4.10 modules use `allWarningsAsErrors: true`; warnings break compilation.

## Commands

- Run all tests: `./kotlin test`
- Run one module: `./kotlin test -m membership-manager` (replace the module name; see `./kotlin show modules`).
- Run a focused class: `./kotlin test -m membership-manager --include-classes '*ApplicationConfigTest'`.
- Run a focused test method with `--include-test=<fully-qualified-test-method>`; use `./kotlin test --help` for the exact FQN format.
- Run built-in checks: `./kotlin check` (currently the `tests` check).
- Build the membership executable JAR: `./kotlin package -m membership-manager -f executable-jar`.

## Runtime and tests

- The application needs PostgreSQL and NATS. Start infrastructure and migrations with `docker compose up -d db nats` followed by `docker compose run --rm liquibase`; then run with `./kotlin run -m membership-manager -- --config businesslogic/membership-manager/config/local.properties`.
- Container deployment is `docker compose up --build membership-manager`; Compose runs Liquibase before the application.
- Testcontainers integration tests start real PostgreSQL/NATS containers. Local reuse requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`; CI intentionally does not reuse containers.
- With rootless Podman, start `systemctl --user start podman.socket`, then set `DOCKER_HOST="unix://${XDG_RUNTIME_DIR}/podman/podman.sock"` and `TESTCONTAINERS_RYUK_DISABLED=true` before tests.
- Configuration files are loaded with `--config <path>`, then environment variables override file values. Required variables are `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `NATS_URL`; optional NATS settings default to the membership subject and a 5-second timeout.

## Database changes

- Add Liquibase changesets under `liquibase/changelog/` and include them from `liquibase/changelog-root.yaml`; the Compose `liquibase` service applies the root changelog.
