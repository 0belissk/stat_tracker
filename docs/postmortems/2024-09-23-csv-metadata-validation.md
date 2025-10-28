# Postmortem — CSV Metadata Rejection

- **Date**: 2024-09-23
- **Incident Commander**: TODO: Assign IC
- **Contributors**: Ingestion, Data Engineering
- **Status**: Closed

## Summary
A coach upload that included a new `teamId` column failed validation and never progressed through the ingestion pipeline.
The CSV validator treated the metadata column as if it were a feedback category and rejected every row because the cells were blank.

## Impact
- Duration: 37 minutes from detection to mitigation.
- Scope: 1 upload (18 player reports) blocked; no downstream emails were sent.
- Customer impact: Coach received an error artifact and could not distribute reports until re-upload.

## Detection
- Automated: CloudWatch alarm `CSVValidationErrors` fired at 14:12 CDT.
- Manual: Coach escalated in #vsm-ops Slack with the error report JSON containing `"Category feedback cannot be blank"` messages.

## Timeline
| Time (CDT) | Event |
| --- | --- |
| 14:12 | Alarm triggered after validation Lambda wrote error artifact for `coach-77/2024-09-23.csv`. |
| 14:16 | On-call engineer inspected error report and noticed `teamId` column mentioned in messages. |
| 14:22 | Reproduced locally using [`docs/data/bad-team-metadata.csv`](../data/bad-team-metadata.csv) via `npm test --prefix lambdas/csv-validate`. |
| 14:31 | Root cause identified: validator optional column allow-list missing `teamId`. |
| 14:39 | Patch deployed to staging; CSV re-run successfully. |
| 14:49 | Production Lambda updated; alarm cleared. |

## Root Cause
The CSV validator enforces that every non-required column must contain player feedback.
The allow-list of optional metadata columns contained `playerName` but not `teamId`.
When coaches exported the CSV from a roster tool that auto-populated a `teamId` column with blank values, the validator interpreted it as a category column and rejected the row because the cells were empty.

## Resolution
- Added `teamId` to the optional metadata allow-list in `lambdas/csv-validate` so it is ignored by the feedback rule.
- Extended unit tests to prove metadata columns no longer trigger error reports.
- Updated documentation (system overview, runbook) to call out supported metadata columns.

## Corrective Actions
1. ✅ Add regression test covering metadata columns (`npm test --prefix lambdas/csv-validate`).
2. ✅ Document supported metadata columns in system overview and runbooks.
3. 🔜 TODO: Expand validator configuration to allow metadata columns to be managed via environment variable.
4. 🔜 TODO: Add contract test ensuring `stats_quality_check` and `persist-batch` handle optional metadata consistently.

## Lessons Learned
- Schema allow-lists must evolve with upstream CSV exports; capture them in code + docs to avoid drift.
- Integration tests using realistic roster exports should be part of release readiness.
- Rapid reproduction with a sanitized sample CSV kept MTTR under 40 minutes — keep `docs/data/` up to date with anonymized fixtures.
