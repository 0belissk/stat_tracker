# System Overview — Volleyball Stats Messenger

## Context

Volleyball Stats Messenger turns raw CSV uploads from coaches into personalized plain-text emails for each player.
The platform is split into four domains:

1. **Experience** — Angular portal + Spring Boot APIs for coaches and players.
2. **Ingestion** — S3-triggered pipeline that validates, normalizes, quality-checks, and persists CSV rows.
3. **Delivery** — SES notifications with presigned S3 report links plus audit trail events.
4. **Observability & Ops** — CloudWatch metrics/alarms, X-Ray traces, EventBridge telemetry, and runbooks.

Refer to the diagrams in [`docs/diagrams/`](diagrams) for the zoomed-out context, component responsibilities, and the coach upload sequence.

## Core Components

| Component | Language | Responsibility | Key Dependencies |
| --- | --- | --- | --- |
| `frontend` | Angular | Authenticated portal for coaches/players to upload CSVs, trigger manual sends, and browse history. | Cognito, REST APIs |
| `services/players-api` | Spring Boot | REST endpoints for ingesting manual reports, fetching player history, and emitting audit events. | DynamoDB, S3, EventBridge, CloudWatch |
| `lambdas/csv-validate` | TypeScript | Validates CSV structure/content, writes error artifacts to S3, and short-circuits invalid uploads. | S3, Step Functions |
| `lambdas/stats_quality_check` | Python | Applies configurable business rules (duplicate detection, category coverage). | DynamoDB, EventBridge |
| `lambdas/persist-batch` | TypeScript | Transactionally stores reports, updates per-player aggregates, and emits metrics. | DynamoDB, CloudWatch |
| `lambdas/notify-report-ready` | TypeScript | Generates SES emails with presigned S3 links, populates templates from Secrets Manager. | S3, SES, Secrets Manager |

## Data Flow

1. **Upload** — Coach posts CSV to presigned URL (`vsm-raw-uploads`).
2. **Validation** — `csv-validate` parses rows, enforces required columns, and ensures at least one category column with feedback. Metadata columns (`playerName`, `teamId`) are ignored by validation but passed downstream.
3. **Normalization** — Step Functions map state produces `PersistBatchEvent` documents per row.
4. **Quality** — `stats_quality_check` enforces configurable rules and emits `csv.quality` events for observability.
5. **Persistence** — `persist-batch` writes each report to the DynamoDB single-table schema and updates player aggregates.
6. **Notification** — `notify-report-ready` presigns the text report in S3, sends SES email, and records audit metadata.

## Deployments & Environments

- **Infrastructure**: Terraform modules in `infra/terraform` provision AWS accounts (S3, DynamoDB, Step Functions, SES, Cognito, etc.).
- **Services**: Spring Boot services are containerized (Dockerfile per service) and deployed via ECS or EKS (see [`docs/k8s/README.md`](docs/k8s/README.md) — TODO: capture Helm chart usage once finalized).
- **Lambdas**: Packaged via npm scripts + AWS SAM/CDK pipelines (TODO: document exact build commands when CI/CD is finalized).
- **Environments**: `dev`, `staging`, `prod` with feature branch sandboxes as needed. Secrets live in per-environment AWS Secrets Manager paths.

## Observability

- **Metrics**: Custom namespace `TODO: Replace with namespace for Lambda metrics` (see lambda handlers). Track validation failures, quality rejects, DynamoDB latency, SES success rate.
- **Logs**: Structured JSON via Winston (TypeScript Lambdas) and Logback (Spring Boot). Correlation IDs propagate via headers and Step Functions context.
- **Traces**: AWS X-Ray spans originate from API + Step Functions tasks. Persist lambda annotates `correlationId` and `ingestionId` for cross-service linkage.
- **Dashboards**: TODO: Create CloudWatch dashboards summarizing SLO burn, queue depth, and SES deliverability.

## Integrations

- **Authentication**: Amazon Cognito for web portal + API tokens.
- **Email**: Amazon SES with domain verification and bounce handling.
- **Storage**: DynamoDB (reports, audit), S3 (raw uploads, text reports, error artifacts).
- **Messaging**: SQS for async processing, EventBridge for analytics events.

## Change Management

- Architectural decisions are recorded in [`adr/`](../adr).
- Release notes follow SemVer and are stored per version in [`docs/release-notes/`](release-notes).
- Incident learnings live in [`docs/postmortems/`](postmortems) with action items tracked during sprint planning.
