pipeline {
    agent any

    environment {
        IMAGE_NAME = 'ramellian-executive-travels'
        VPS_USER   = 'root'
        VPS_HOST   = '102.68.87.231'
        JAVA_HOME  = '/usr/lib/jvm/temurin-21-jdk-amd64'
        PATH       = "${JAVA_HOME}/bin:${env.PATH}"
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Build JAR') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B clean package -DskipTests'
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
                        ssh -o StrictHostKeyChecking=accept-new $VPS_USER@$VPS_HOST "mkdir -p /opt/app"

                        scp -o StrictHostKeyChecking=accept-new \
                          docker-compose.yml $VPS_USER@$VPS_HOST:/opt/app/docker-compose.yml

                        docker save $IMAGE_NAME:latest | gzip | \
                          ssh -o StrictHostKeyChecking=accept-new $VPS_USER@$VPS_HOST \
                          "gunzip | docker load && cd /opt/app && docker compose up -d --remove-orphans"
                    '''
                }
            }
        }
    }

    post {
        success { echo 'Deployed successfully.' }
        failure { echo 'Build or deploy failed.' }
        always  { sh 'docker image prune -f || true' }
    }
}