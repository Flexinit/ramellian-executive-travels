pipeline {
    agent any

    environment {
        IMAGE_NAME = 'ramellian-executive-travels'
        VPS_USER   = 'root'
        VPS_HOST   = '102.68.87.231'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build JAR') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $IMAGE_NAME:latest .'
            }
        }

        stage('Deploy to VPS') {
            steps {
                sshagent(['vps-ssh-key']) {
                    sh '''
                        docker save $IMAGE_NAME:latest | \
                          ssh -o StrictHostKeyChecking=no \
                          $VPS_USER@$VPS_HOST \
                          "docker load && systemctl restart ramellian.service"
                    '''
                }
            }
        }
    }

    post {
        failure { echo 'Build or deploy failed.' }
    }
}