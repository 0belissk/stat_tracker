# 0003 — Allow Metadata Columns in CSV Validator

- **Status**: Accepted
- **Date**: 2024-09-23

## Context
Coaches increasingly export stats from roster tools that append metadata columns such as `teamId` or `playerName`.
Our Step Functions pipeline expects these fields and downstream Lambdas (`persist-batch`, `stats_quality_check`) already handle them as optional metadata.
However, the CSV validation Lambda enforced that *every* non-required column contain player feedback text.
When an export included a blank `teamId` column the validator rejected the upload before the metadata could reach downstream services.

## Decision
Expand the validator's allow-list of optional columns to include known metadata keys (`playerName`, `teamId`).
These columns bypass the "category feedback required" rule but are still normalized and forwarded in the event payload for downstream consumers.
Unit tests cover the regression to ensure metadata columns no longer create error artifacts.

## Consequences
- ✅ Coaches can include roster metadata without breaking ingestion.
- ✅ Postmortem action item addressed by keeping validator allow-list in sync with downstream expectations.
- ⚠️ Additional metadata fields will require a code change until we make the allow-list configurable (tracked as TODO in the postmortem).
