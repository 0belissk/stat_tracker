# Demo Script — Volleyball Stats Messenger

Use this 10–12 minute flow to showcase VSM end-to-end for stakeholders. Update TODO placeholders with environment-specific links before presenting.

## 1. Set the Stage (1 min)
- Introduce the coach persona (`TODO: coach name`) and player persona (`TODO: player name`).
- State the goal: upload practice stats and deliver personalized feedback in under a minute.

## 2. Upload CSV (3 min)
1. Open the Angular portal (`TODO: portal URL`) and log in as the coach.
2. Highlight the CSV template download link and explain required vs. optional columns (`playerId`, `playerEmail`, `playerName`, `teamId`, categories).
3. Drag-and-drop the prepared CSV (use [`docs/data/bad-team-metadata.csv`](docs/data/bad-team-metadata.csv) updated with real values).
4. Show the upload progress indicator and the success toast when validation passes.

## 3. Monitor Pipeline (2 min)
- Flip to CloudWatch (or LocalStack dashboard) showing the Step Functions execution (`TODO: link to execution`).
- Call out each stage: validation, quality check, persist, notify.
- Mention that validation now allows metadata columns — reference the recent postmortem fix.

## 4. Player Experience (3 min)
1. Open the player inbox (`TODO: email screenshot or test mailbox`).
2. Show the SES-delivered email with the presigned text report link.
3. Click through to the report hosted in S3 (`TODO: sample presigned URL`) and highlight the concise feedback.
4. In the portal, switch to the player view to show historical reports and audit entries.

## 5. Operations & Observability (2 min)
- Display the CSV pipeline dashboard (`TODO: CloudWatch dashboard URL`).
- Walk through the `docs/runbooks/csv-pipeline.md` and point to the postmortem for the metadata fix.
- Mention alerting strategy: validation errors, quality failures, SES deliverability.

## 6. Close with Roadmap (1 min)
- Upcoming enhancements: configurable metadata allow-list, automated Helm deployment to EKS, dashboards.
- Reinforce the release tag `v1.0.0` and invite feedback via #vsm-release channel.
