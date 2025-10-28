![CI](https://github.com/0belissk/stat_tracker/actions/workflows/ci.yml/badge.svg)

# Volleyball Stats Messenger (VSM)

VSM gives volleyball coaches a fast, reliable way to deliver plain-text stat summaries to every player after a practice or match.
This monorepo houses the Spring Boot APIs, Angular portal, ingestion lambdas, and infrastructure-as-code that power the CSV → email pipeline.

> **Current release:** `v1.0.0` (see [`docs/release-notes/v1.0.0.md`](docs/release-notes/v1.0.0.md))

---

## Quickstart

### Prerequisites
- Node.js 18+
- npm 9+
- Java 17 (uses the bundled `mvnw` wrapper)
- Python 3.11+ for integration tooling
- Docker (optional) if you plan to run LocalStack or container builds

### 1. Install dependencies
```bash
# Frontend
npm install --prefix frontend

# Lambda packages
npm install --prefix lambdas/csv-validate
npm install --prefix lambdas/persist-batch
npm install --prefix lambdas/notify-report-ready

# Python Lambda
pip install -r lambdas/stats_quality_check/requirements.txt

# Spring Boot services
./mvnw -pl services/players-api -am dependency:go-offline

# Integration tooling
pip install -r tests/python/requirements.txt
```

### 2. Run unit tests and linters
```bash
npm test --prefix lambdas/csv-validate
npm test --prefix lambdas/persist-batch
npm test --prefix lambdas/notify-report-ready
./mvnw verify -pl services/players-api
```

### 3. Smoke test the CSV → email flow (LocalStack)
```bash
pytest tests/python/integration/test_csv_to_email_e2e.py -s
```
The integration test prints per-stage timings so you can confirm the end-to-end SLO (< 60s p95) is met locally.

### 4. Run the players API locally (optional)
```bash
./mvnw spring-boot:run -pl services/players-api
```
The API boots on `http://localhost:8080` with OpenAPI docs at `/swagger-ui.html`.

### 5. Run the Angular portal (optional)
```bash
npm run start --prefix frontend
```
The dev server listens on `http://localhost:4200`.

---

## System Overview

| Area | Description |
| --- | --- |
| CSV ingestion | S3 upload → `csv-validate` → Step Functions normalization → `stats_quality_check` → `persist-batch` → `notify-report-ready`. |
| Persistence | DynamoDB single-table design keyed by player, with team and report GSIs (`docs/data/dynamodb-single-table-draft.md`). |
| Delivery | SES emails per player with presigned S3 links to text reports. |
| Frontend | Angular portal for coaches/players to review history and trigger manual sends. |
| API | Spring Boot `players-api` exposes report submission and audit endpoints. |

See [`docs/system-overview.md`](docs/system-overview.md) for diagrams, request flows, and data contracts.

---

## Documentation Map

- [`docs/system-overview.md`](docs/system-overview.md) — architecture, deployment units, and dependencies
- [`docs/runbooks/csv-pipeline.md`](docs/runbooks/csv-pipeline.md) — on-call steps for ingestion issues
- [`docs/postmortems/2024-09-23-csv-metadata-validation.md`](docs/postmortems/2024-09-23-csv-metadata-validation.md) — latest incident write-up
- [`docs/release-notes/v1.0.0.md`](docs/release-notes/v1.0.0.md) — changelog for this release
- [`docs/demo-script.md`](docs/demo-script.md) — storytelling flow for stakeholder demos
- [`adr/`](adr) — architectural decision records (CSV ingestion, validation guardrails, etc.)

---

## Development Workflow

1. Branch from `main` (`feat/*`, `fix/*`, or `chore/*`).
2. Follow the quality gates in [`CONTRIBUTING.md`](CONTRIBUTING.md): format, lint, tests, coverage ≥ 80%.
3. Commit with clear messages and open a PR describing the **why**.
4. Tag releases with SemVer (`git tag -a vX.Y.Z`). `v1.0.0` is the baseline.

---

## Operational Checklist

- **Metrics**: CloudWatch namespace `TODO: Replace with namespace for Lambda metrics` (see `lambdas/persist-batch/src/handler.ts`).
- **Tracing**: AWS X-Ray enabled on API + Lambdas (`XRAY_SERVICE_NAME` set per environment).
- **Alerting**: Alarms on validation, quality, persist, and email delivery failures (documented in the CSV runbook).
- **Runbooks**: Stored in `docs/runbooks/` and linked from pager rotations.

---

## Licensing

This project is released under the MIT license. See [`LICENSE`](LICENSE) for details.
