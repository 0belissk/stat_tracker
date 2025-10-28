# Kubernetes Deployment (Optional)

This guide sketches out how to deploy the Spring Boot `players-api` service to Amazon EKS using Helm charts.
Use it as a starting point for platform experimentation — production rollouts should follow the team's infrastructure review process.

## Prerequisites
- EKS cluster provisioned via Terraform or eksctl (`TODO: cluster name and region`)
- Container image published to Amazon ECR (`TODO: ECR repository URI`)
- Helm 3 installed locally
- AWS IAM permissions to update cluster resources

## Steps
1. **Create a values file** with environment-specific settings:
   ```yaml
   image:
     repository: TODO: players-api ECR repository
     tag: TODO: semantic version tag
   env:
     AWS_REGION: TODO: region
     REPORTS_TABLE_NAME: TODO: DynamoDB table name
     XRAY_SERVICE_NAME: players-api
   service:
     type: ClusterIP
     port: 8080
   ingress:
     enabled: true
     className: alb
     hosts:
       - host: TODO: public hostname
         paths:
           - path: /
             pathType: Prefix
   ```

2. **Install/upgrade the chart** (replace placeholders before running):
   ```bash
   helm upgrade --install players-api charts/players-api \
     --namespace vsm \
     --create-namespace \
     -f values/players-api.todo-region.yaml
   ```

3. **Validate deployment**:
   - `kubectl get pods -n vsm`
   - `kubectl get ingress -n vsm`
   - Hit `/actuator/health` via the ingress hostname.

4. **Set up monitoring**:
   - Annotate the deployment for AWS Distro for OpenTelemetry if required (TODO: add example).
   - Wire CloudWatch alarms to pod metrics or target response times.

> Document real values and automation steps once the Helm chart is production-ready.
