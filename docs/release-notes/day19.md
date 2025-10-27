# Release Notes — Day 19 (TODO: Provide release date)

## Highlights

* Added a Spring Web Services SOAP client that calls a public EchoString endpoint and records the returned `soapStamp` in player report metadata.
* Extended player report listings and API responses to surface the stored `soapStamp` value for downstream consumers.
* Created a Jenkins “Promote to prod” stage that requires a manual approval before running Terraform against the production environment.

## Pre-deployment Checklist

* TODO: Provide change request or CAB approval reference.
* TODO: Provide confirmation that the public SOAP endpoint is reachable from the deployment environment.

## Deployment Steps

1. Run the Jenkins pipeline and monitor the `Build & Test` stage for a successful Maven test run.
2. During the `Promote to prod` stage, review the Terraform plan output and approve the manual gate when ready.
3. Jenkins will execute `terraform apply` using the provided prod variable file (`${PROD_TFVARS}` placeholder). Update the Jenkinsfile environment block with the real path before promoting.

## Post-deployment Verification

* TODO: Provide verification steps for confirming that new reports include the `soapStamp` attribute in DynamoDB and API responses.
* TODO: Provide monitoring dashboards or alarms to watch after deployment.

## Communication

* TODO: Provide links to release announcement or stakeholder communication.
