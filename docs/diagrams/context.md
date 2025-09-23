# Context Diagram (Mermaid)

```mermaid
flowchart LR
  Coach((Coach c_mendez_001)) -- JWT --> Angular[Angular Portal]
  Player((Player p_alex_li_12)) -- JWT --> Angular
  Angular -- HTTPS --> API[(Spring Boot API on ALB)]
  API -- read/write --> DDB[(DynamoDB: vsm-main)]
  API -- put/get --> S3[(S3: vsm-raw-uploads / vsm-report-texts)]
  API -- events --> EB[EventBridge]
  EB -- triggers --> SFN[Step Functions]
  SFN -- invokes --> Lmb[Lambda: csv-validate / persist / notify]
  Lmb -- send --> SES[SES (no-reply@vsm.example.com)]
  API -. traces/logs .- CW[(CloudWatch/X-Ray)]
  API -. auth .- Cognito[(Cognito Hosted UI/JWT)]
```
