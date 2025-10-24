# ADR-0002 CSV Ingestion Pipeline Design

**Status**: Accepted  
**Date**: 2025-09-23

## Context
Coaches upload season stat CSVs that must be validated, normalized, stored, and delivered as emails quickly and reliably. We need an ingestion architecture that balances correctness (catch bad rows), timeliness (CSV → email < 60 s), and observability (clear success/failure signals). The solution must integrate existing lambdas (`csv-validate`, `stats_quality_check`, `persist-batch`, `notify-report-ready`) and support LocalStack-based tests for iteration.

## Decision
- Orchestrate ingestion with AWS Step Functions (`validate → transform → quality-check → persist → notify`) to isolate failures and add retries/SLO measurements.
- Use serverless components per stage:
  - **S3 event** triggers validation Lambda that writes structured error reports alongside uploads.
  - **Transform** step normalizes rows into report payloads used by downstream lambdas.
  - **Quality check** Lambda enforces dynamic rules (S3 JSON), dedupes via DynamoDB, and emits EventBridge telemetry.
  - **Persist batch** Lambda writes player reports to DynamoDB in transactional chunks and updates player metadata.
  - **Notify report ready** Lambda fetches templated config from SSM, generates signed S3 links, and sends SES emails.
- Expose service endpoint overrides via environment variables (`*_ENDPOINT_URL`, `AWS_ENDPOINT_URL`) so the full pipeline can run against LocalStack for CI and SLO sampling.
- Record per-stage timings during E2E tests to confirm the <60 s ingest SLO and surface regressions.

## Alternatives Considered
- **Monolithic ECS worker** to handle CSV ingestion end-to-end. *Rejected*: higher operational burden, slower deployment iterations, harder to retry partial failures.
- **Direct S3 to Lambda fan-out** without Step Functions. *Rejected*: complicated error handling and coordination between multiple lambdas, no central SLO tracking.
- **Fully managed ETL (Glue/DataBrew)**. *Rejected*: heavier cost, slower cold starts, less control over validation and custom audit data.

## Consequences
**Pros**
- Fine-grained retries and DLQ catch-all reduce blast radius.
- Each stage is locally testable; LocalStack support keeps feedback loops fast.
- Structured error artifacts support coach feedback and future UI surfacing.
- Timings captured in automated tests document SLO adherence.

**Cons**
- More moving pieces (Step Functions + multiple lambdas) increase IAM surface and coordination.
- Transform step must stay consistent with downstream schema contracts; drift risks runtime failures.
- SES warm-up and configuration must be maintained to keep deliverability high.

## Follow-ups
- Automate nightly SLO sampling (store timings from integration runs in CloudWatch metrics).
- Build dashboards/alerts for `csv.validated`, persistence errors, and SES bounces/complaints.
- Expand runbooks with guided triage for partial pipeline failures once production metrics arrive.
