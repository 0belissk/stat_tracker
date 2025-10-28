# Runbook — CSV Ingestion → Email Delivery

## Overview
This runbook covers the CSV ingestion pipeline that starts when a coach uploads a CSV and ends when SES delivers report emails. The pipeline is implemented as a Step Functions state machine (`validate → transform → quality-check → persist → notify`) with supporting lambdas:

| Stage | Lambda / Service | Purpose |
| --- | --- | --- |
| Validate | `lambdas/csv-validate` | Parse CSV and write JSON error artifacts to `errors/` prefix. |
| Transform | (Step Functions task) | Convert rows to normalized report payloads consumed downstream. |
| Quality Check | `lambdas/stats_quality_check` | Apply configurable rules, detect duplicates, emit EventBridge telemetry. |
| Persist | `lambdas/persist-batch` | Transactionally store reports + update player metadata in DynamoDB. |
| Notify | `lambdas/notify-report-ready` | Fetch email templates from Secrets Manager, presign report text in S3, send SES email. |

Target SLO: **CSV upload → email delivery < 60 seconds (p95)**. Automated integration test `tests/python/integration/test_csv_to_email_e2e.py` records per-stage timings and prints them when run with `pytest -s`.

## Preconditions & Observability
- **CloudWatch Alarms**: `CSVValidationErrors`, `CsvQualityFailures`, `CsvPersistFailures`, `SesDeliveryIssues` should be green.
- **EventBridge**: `csv.quality` events show pass/fail status and failure reasons.
- **DynamoDB**: `reports-table` item counts should climb after ingestion.
- **SES**: Monitor bounce/complaint metrics for deliverability regressions.
- **S3**: `errors/` prefix contains per-upload validation failures; absence indicates CSV passed schema checks.

## Common Failure Modes
1. **Validation failures**
   - Symptoms: JSON file in `errors/<original-key>.errors.json` with row-level messages; Step Function fails fast.
   - Actions:
     1. Download the error file; share with coach for corrections.
     2. Confirm CSV header matches expected columns (`playerId`, `playerEmail`, categories).
     3. If the report mentions metadata columns (`playerName`, `teamId`), verify they are allowed and blank values are expected.
        Use `npm test --prefix lambdas/csv-validate -- bad-team-metadata` with [`docs/data/bad-team-metadata.csv`](../data/bad-team-metadata.csv)
        to confirm the validator fix is in place.
     4. Rerun pipeline by re-uploading corrected CSV.

2. **Quality check failures**
   - Symptoms: Step Function enters `SendToDlq`; EventBridge detail shows `status=failed` and `failures` array.
   - Actions:
     1. Query DLQ message for payload + failure summary.
     2. Check S3 quality rules (`QUALITY_RULES_BUCKET/QUALITY_RULES_KEY`) for required categories / limits.
     3. If duplicates reported, query DynamoDB `GSI1` for conflicting `reportId`.
     4. After remediation, replay DLQ message or re-run ingestion.

3. **Persistence errors**
   - Symptoms: `persist-batch` throws `PersistBatchError`; Step Function transitions to DLQ.
   - Actions:
     1. Inspect CloudWatch logs for `TransactionCanceledException` details.
     2. Verify table capacity / IAM permissions.
      3. If conditional failures due to duplicates, confirm replays are idempotent.
      4. Ensure metadata columns propagated from CSV (e.g., `teamId`) match DynamoDB item expectations.

4. **Email delivery issues**
   - Symptoms: Step Function succeeds but players report missing emails; SES metrics show delivery issues.
   - Actions:
     1. Verify the Secrets Manager payload referenced by `CONFIG_SECRET_ARN` contains sender email, subject template, and body.
     2. Confirm SES identity is verified and not suppressed (check complaints/bounces).
     3. Validate S3 object referenced in event exists and is readable.
     4. Resend email by publishing `report.created` event to EventBridge.

## Rapid Verification (LocalStack)
Use LocalStack to reproduce issues and collect SLO timings:

```bash
pip install -r tests/python/requirements.txt
pytest tests/python/integration/test_csv_to_email_e2e.py -s
```

The test prints a JSON blob with stage timings and asserts the total duration is < 60 seconds. These measurements can be pasted into incident timelines or used to compare pre/post-fix performance.

## Escalation
- **First line**: Ingestion on-call engineer
- **Escalate to**: Platform SRE if Step Functions repeatedly hits DLQ or SES quota is throttled.
- **Communications**: Update #vsm-ops Slack channel; provide CSV filename, ingestionId, failure stage, and relevant CloudWatch log links.
