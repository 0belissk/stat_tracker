# DynamoDB Single-Table Draft

**Table**: `vsm-main`  
**Primary Key**: `PK` (partition), `SK` (sort)  
**GSIs**: `GSI1` (reportId lookup), `GSI2` (team timeline — optional)

## Access Patterns
1. **List a player’s reports** (reverse chronological)
2. **Get a specific report** by `reportId`
3. **List a team’s reports** (coach/team view)
4. **Audit trail** per report (who sent/viewed/when)

## Item Shapes
### PLAYER
PK = PLAYER#p_alex_li_12
SK = PROFILE#p_alex_li_12
email = "alex.li@example.edu"
name = "Alex Li"
teamId = "t_uic_mens_2025"

### REPORT
PK = PLAYER#<playerId>
SK = REPORT#<yyyyMMddHHmmss>#<reportId>
reportId, playerId, teamId, coachId, categories, s3Key, createdAt
GSI1PK = REPORT#<reportId>
GSI1SK = REPORT#<reportId>
GSI2PK = TEAM#<teamId> (optional)
GSI2SK = CREATED#<yyyyMMddHHmmss>#<reportId>

### AUDIT
PK = REPORT#<reportId>
SK = AUDIT#<timestamp>Z#<eventType> # SENT | VIEWED
actorId, actorRole, ip, userAgent, correlationId?

## Example Items
```json
{
  "PK": "PLAYER#p_alex_li_12",
  "SK": "PROFILE#p_alex_li_12",
  "email": "alex.li@example.edu",
  "name": "Alex Li",
  "teamId": "t_uic_mens_2025"
}
{
  "PK": "PLAYER#p_alex_li_12",
  "SK": "REPORT#20250922T221530#r_0001",
  "reportId": "r_0001",
  "playerId": "p_alex_li_12",
  "teamId": "t_uic_mens_2025",
  "coachId": "c_mendez_001",
  "categories": {"Aces": 3, "Digs": 17, "Blocks": 2},
  "s3Key": "reports/p_alex_li_12/2025/09/22/r_0001.txt",
  "createdAt": "2025-09-22T22:15:30Z",
  "GSI1PK": "REPORT#r_0001",
  "GSI1SK": "REPORT#r_0001",
  "GSI2PK": "TEAM#t_uic_mens_2025",
  "GSI2SK": "CREATED#20250922T221530#r_0001"
}
{
  "PK": "REPORT#r_0001",
  "SK": "AUDIT#20250922T221532Z#SENT",
  "actorId": "c_mendez_001",
  "actorRole": "COACH",
  "ip": "203.0.113.10",
  "userAgent": "Mozilla/5.0"
}
{
  "PK": "REPORT#r_0001",
  "SK": "AUDIT#20250922T223001Z#VIEWED",
  "actorId": "p_alex_li_12",
  "actorRole": "PLAYER",
  "ip": "198.51.100.23",
  "userAgent": "Mobile Safari/17.0"
}
```
Typical Queries (pseudo)
Player history: Query PK = PLAYER#p_alex_li_12 AND begins_with(SK, "REPORT#") ORDER BY desc
Get report by id: Query GSI1 where GSI1PK = REPORT#r_0001
Team history: Query GSI2 where GSI2PK = TEAM#t_uic_mens_2025 ORDER BY desc
Audit for a report: Query PK = REPORT#r_0001 AND begins_with(SK, "AUDIT#")
Idempotency
Client supplies reportId; perform conditional put: attribute_not_exists(PK) AND attribute_not_exists(SK).
