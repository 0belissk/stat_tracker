pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        timestamps()
    }
    environment {
        PROD_TFVARS = 'TODO: Provide path to production terraform.tfvars file'
    }
    stages {
        stage('Build & Test') {
            steps {
                sh './mvnw -pl services/players-api test'
            }
        }
        stage('Promote to prod') {
            steps {
                script {
                    timeout(time: 2, unit: 'HOURS') {
                        input message: 'Ready to promote infrastructure to production?', ok: 'Promote'
                    }
                }
                dir('infra/terraform/envs/prod') {
                    sh '''
                        terraform init -input=false
                        terraform workspace select prod || terraform workspace new prod
                        terraform plan -input=false -var-file="${PROD_TFVARS}"
                        terraform apply -input=false -auto-approve -var-file="${PROD_TFVARS}"
                    '''
                }
            }
        }
    }
    post {
        always {
            echo 'Pipeline finished'
        }
    }
}
