# Players API Service

## Coach Reports Endpoint

`POST /api/coach/reports`

Submit a report for a player. Requests must include:

* `reportId` header — ISO-8601 instant string used for idempotency (e.g. `2024-03-20T18:25:43.511Z`).
* JSON body `ReportRequest` with `playerId`, `playerEmail`, and feedback categories.

Successful submissions respond with `202 Accepted` and a body containing the queued `reportId`.
