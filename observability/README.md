# OpenTelemetry

The local Compose stack starts Grafana at `http://localhost:3000` and provisions Tempo as its trace datasource.

Applications export traces to the OpenTelemetry Collector at `otel-collector:4317` when they run in Compose. Applications run directly on the host can use `http://localhost:4317`.

`otel-collector.grafana-cloud.yaml` is the production Collector configuration. Deploy it with the Collector beside the application services and set these deployment secrets:

- `GRAFANA_CLOUD_INSTANCE_ID`
- `GRAFANA_CLOUD_API_TOKEN`
- `GRAFANA_CLOUD_OTLP_ENDPOINT`

The endpoint is the Grafana Cloud OTLP gateway URL, including its `/otlp` path. The Collector removes PII-bearing attributes before tail sampling and export. Production services should use `OTEL_TRACES_SAMPLER=always_on`; the Collector retains error traces, traces taking at least two seconds, and ten percent of other traces.
