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

        stage('Detect Changes') {
            steps {
                script {
                    def changedFiles = sh(
                        script: 'git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only $(git rev-list --max-parents=0 HEAD) HEAD',
                        returnStdout: true
                    ).trim()

                    echo "Changed files:\n${changedFiles}"

                    def fileList = changedFiles.split('\n') as List
                    env.CONFIG_ONLY = (fileList.every { it == 'docker-compose.yml' }) ? 'true' : 'false'

                    echo env.CONFIG_ONLY == 'true'
                        ? 'Only docker-compose.yml changed — skipping build, restart only.'
                        : 'Source files changed — full build required.'
                }
            }
        }

        stage('Build JAR') {
            when { environment name: 'CONFIG_ONLY', value: 'false' }
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            when { environment name: 'CONFIG_ONLY', value: 'false' }
            steps {
                sh 'docker build -t $IMAGE_NAME:latest .'
            }
        }

        stage('Deploy to VPS') {
            steps {
                sshagent(['vps-ssh-key']) {
                    script {
                        if (env.CONFIG_ONLY == 'true') {
                            sh '''
                                ssh -o StrictHostKeyChecking=accept-new $VPS_USER@$VPS_HOST "mkdir -p /opt/app"

                                scp -o StrictHostKeyChecking=accept-new \
                                  docker-compose.yml $VPS_USER@$VPS_HOST:/opt/app/docker-compose.yml

                                ssh -o StrictHostKeyChecking=accept-new $VPS_USER@$VPS_HOST \
                                  "cd /opt/app && docker compose up -d --remove-orphans"
                            '''
                        } else {
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
        }
    }

    post {
        success { echo 'Deployed successfully.' }
        failure { echo 'Build or deploy failed.' }
        always  { sh 'docker image prune -f || true' }
    }
}
