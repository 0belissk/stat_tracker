# Component Diagram (Mermaid)

```mermaid
flowchart TB
  subgraph Frontend
    A[Angular App\nRoutes, Guard, JWT Interceptor, Forms]
  end

  subgraph Services
    B[Spring Boot API\nControllers, Services, Security, OpenAPI, Actuator]
    C[Lambda csv-validate (TS)]
    D[Lambda persist-batch (TS)]
    E[Lambda notify-report-ready (TS)]
    F[Step Functions Orchestrator]
  end

  subgraph Data
    G[(DynamoDB: Single Table)]
    H[(S3: raw uploads & .txt reports)]
  end

  A --> B
  B --> G
  B --> H
  B --> F
  F --> C
  F --> D
  F --> E
```
