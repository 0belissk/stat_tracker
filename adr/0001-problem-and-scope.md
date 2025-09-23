# ADR-0001 Problem and Scope for Volleyball Stats Messenger

**Status**: Accepted  
**Date**: 2025-09-23

## Context
Coaches need a fast, auditable way to deliver plain-text stat reports to players via email. MVP must prioritize speed, reliability, and security over visualization. Players require a secure history of reports.

## Decision
- Deliver **plain-text** report flow (manual entry + CSV ingestion) with **email delivery** and **secure signed links**.
- Use **DynamoDB single-table** for low-latency timelines; store report text in **S3**.
- Protect endpoints with **Cognito JWT** and **WAF**; encrypt at rest with **KMS**.
- Capture **AUDIT** items for both send and view events.
- Define **SLOs**: reads p95 < 200 ms; CSV→email < 60 s; 99.9% availability.

## Alternatives Considered
- **Aurora Postgres first**: easier ad-hoc analytics, joins; **cons**: higher read latency for timelines, more ops overhead.
- **Charts & PDFs v1**: richer UX; **cons**: slows time-to-value, heavier pipeline.
- **Email attachments** instead of links: simpler delivery; **cons**: loss of centralized revocation/audit, larger email footprint.

## Consequences
**Pros**: Simple UX, fast reads, straightforward email delivery, strong auditability, easy to scale writes.  
**Cons**: Requires careful Dynamo modeling; no charts in v1; CSV quality control is mandatory.  
**Follow-ups**: Week-2 implement manual send API & SES; Week-3 CSV pipeline; consider Aur
