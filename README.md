# netvaerke

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
