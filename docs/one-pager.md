# One-Pager — Volleyball Stats Messenger (Coach → Player)

**Product**: Volleyball Stats Messenger  
**Team**: `t_uic_mens_2025` — UIC Men's Volleyball 2025  
**Date**: 2025-09-23

## Problem
Coaches need a fast way to send players simple, auditable stat reports via email. Existing tools are visual-first and slow to distribute. We optimize for **speed, reliability, and auditability** with **plain-text** reports in a secure, role-based system.

## Users & Roles
- **Coach (COACH)** — creates/ingests stats, sends reports, views team history. Example: `c_mendez_001` — Coach Luis Mendez
- **Player (PLAYER)** — views personal report history and individual report text. Example: `p_alex_li_12` — Alex Li
- **System** — validates CSV, generates `.txt`, stores in S3, emails signed link via SES.

## Top Use Cases
1. Coach manually enters stats for a player and clicks **Send**.
2. Coach uploads a CSV; system validates/normalizes rows and emails each player their report.
3. Player opens email link to their `.txt` report and can browse prior reports in the portal.
4. Coach audits who received/viewed which report and when.

## Acceptance Criteria (Given/When/Then)
**Manual Send**
- *Given* `c_mendez_001` is authenticated with COACH role,
- *When* they submit a valid stats form for `p_alex_li_12`,
- *Then* the system saves a REPORT item, writes a `.txt` to S3 (`vsm-report-texts`), sends an SES email to `alex.li@example.edu` with a signed link, and writes an AUDIT record for **SENT**.

**CSV Upload**
- *Given* a CSV is uploaded to `vsm-raw-uploads` via a presigned URL,
- *When* the pipeline validates and processes it,
- *Then* invalid rows are rejected with a clear error file; valid rows produce REPORTs, `.txt` files, SES emails, and **AUDIT** events. Failures go to DLQ with retries/backoff.

**Player Access**
- *Given* `p_alex_li_12` is authenticated with PLAYER role,
- *When* they view **My Reports**,
- *Then* they see only their own reports (reverse chronological) and can open the secure `.txt` link. All views are audited.

**Auditability**
- *Given* a sent or viewed report,
- *When* a coach queries audit for that report,
- *Then* the system returns who sent/viewed and timestamps with correlation IDs.

## Non-Functionals (SLO Targets)
- **Reads**: `GET /players/{id}/reports` p95 < **200 ms**.
- **Ingest**: 5k-row CSV end-to-end < **60 s**.
- **Availability**: **99.9%** monthly.
- **Security**: Cognito JWT (RBAC), KMS-encrypted S3/Dynamo, WAF in front of API edges.
- **Observability**: Correlated logs/metrics/traces; alarms on 5xx & SLO burn rate.

## Out of Scope (v1)
Rich charts/visualization, mobile apps, offline mode, multi-sport support, bulk team admin UI beyond basics.

## Risks & Mitigations
- **Email deliverability (SES)** → Domain verification, warm-up, bounce/complaint handling, link expiration.  
- **CSV quality** → Strong schema validation (Ajv/Zod), clear error report, Step Functions retries, SQS DLQ.  
- **Access** → JWT RBAC & policy checks; WAF, least-privilege IAM; KMS on S3/Dynamo; CORS lockdown.
