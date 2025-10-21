# Players API Service

## Coach Reports Endpoint

`POST /api/coach/reports`

Submit a report for a player. Requests must include:

* `reportId` header — ISO-8601 instant string used for idempotency (e.g. `2024-03-20T18:25:43.511Z`).
* JSON body `ReportRequest` with `playerId`, `playerEmail`, and feedback categories.

Successful submissions respond with `202 Accepted` and a body containing the queued `reportId`.

## Container image workflow

1. Package the application jar:

   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Build a container image from the provided multi-stage Dockerfile:

   ```bash
   docker build -t players-api:local .
   ```

3. Authenticate to Amazon ECR (replace placeholders before running):

   ```bash
   aws ecr get-login-password --region todo: (aws region) \
     | docker login --username AWS --password-stdin todo: (aws account id).dkr.ecr.todo: (aws region).amazonaws.com
   ```

4. Tag and push the image:

   ```bash
   docker tag players-api:local todo: (full ECR repository URI with tag)
   docker push todo: (full ECR repository URI with tag)
   ```

5. Update `infra/terraform/envs/<env>/variables.tf` (or a `*.tfvars` file) with the pushed image URI so ECS deploys the new build.

## Observability defaults

- **Correlation IDs**: Every request is traced with the `X-Correlation-Id` header. The filter will reuse an inbound ID or generate a new UUID, expose it via the same response header, and add it to the logging MDC.
- **Structured logs**: `logback-spring.xml` emits JSON to stdout with MDC keys (correlation + AWS X-Ray trace ID) ready for CloudWatch Logs insights queries.
- **AWS X-Ray**: Enable tracing by setting the following environment variables (Terraform defaults these for ECS):
  - `AWS_XRAY_DAEMON_ADDRESS` (sidecar UDP endpoint)
  - `XRAY_SERVICE_NAME` (segment name)
  - `XRAY_SAMPLING_STRATEGY` (`default` unless overridden with JSON rules)
- **Health checks**: `/actuator/health` returns `200 OK` when the Spring Boot actuator reports `UP`; the Application Load Balancer probes this endpoint.
