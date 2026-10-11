# Jenkins CI/CD Pipeline — GitHub to VPS with Docker

Every push to `master` on GitHub builds a Docker image, ships it to your VPS, and restarts the Ramellian Executive Travels API automatically.

---

## Prerequisites

- A VPS running Debian 12 (Bookworm) or Debian 11 (Bullseye) with at least 2GB RAM
- SSH access to your VPS
- Your repo hosted on GitHub
- A domain name pointed at your VPS IP (optional but recommended)
- Port 22 open on your VPS firewall

---

## Steps 1–6: Install Java 21 and Jenkins

**Step 1 — Connect to your VPS**

```bash
ssh user@your-vps-ip
```

**Step 2 — Install Java 21**

On **Debian 12 (Bookworm)** — Java 21 is in the default repos:

```bash
sudo apt update
sudo apt install fontconfig openjdk-21-jre -y
java -version  # should show openjdk 21
```

On **Debian 11 (Bullseye)** — Java 21 needs backports:

```bash
echo "deb https://deb.debian.org/debian bullseye-backports main" | \
  sudo tee /etc/apt/sources.list.d/backports.list

sudo apt update
sudo apt install -t bullseye-backports fontconfig openjdk-21-jre -y
java -version
```

**Step 3 — Add Jenkins repository and install**

```bash
sudo wget -O /usr/share/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key

echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/" | \
  sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update
sudo apt install jenkins -y
```

**Step 4 — Start Jenkins**

```bash
sudo systemctl start jenkins
sudo systemctl enable jenkins
sudo systemctl status jenkins   # confirm: active (running)
```

**Step 5 — Open firewall ports**

```bash
sudo ufw allow OpenSSH
sudo ufw allow 8080
sudo ufw enable
```

**Step 6 — Get the initial admin password**

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

Copy this password. Open `http://your-vps-ip:8080` in your browser, paste it in, click **Install suggested plugins**, create your admin account, then click **Save and Finish**.

---

## Steps 7–10: Docker, Plugins, and Credentials

**Step 7 — Install Docker on the VPS**

Use the official Docker CE repo for Debian (the `docker.io` package from apt is outdated):

```bash
sudo apt install ca-certificates curl gnupg -y

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io -y

sudo systemctl start docker
sudo systemctl enable docker

# Allow Jenkins to run Docker commands
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

**Step 8 — Install Jenkins plugins**

Go to **Manage Jenkins → Plugins → Available plugins**, search and install:

- `GitHub Integration Plugin`
- `SSH Agent Plugin`
- `Docker Pipeline`
- `Docker Plugin`

Restart Jenkins when prompted.

**Step 9 — Add your VPS SSH key to Jenkins**

1. Go to **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**
2. Fill in:
   - Kind: `SSH Username with private key`
   - ID: `vps-ssh-key`
   - Username: your VPS user (e.g. `debian` or whatever user you SSH in as)
   - Private key: paste the contents of `~/.ssh/id_rsa` from your local machine
3. Click **Create**

**Step 10 — Add GitHub token to Jenkins**

1. In GitHub go to **Settings → Developer settings → Personal access tokens → Generate new token**
2. Give it `repo` scope, copy the token
3. Back in Jenkins: **Add Credentials**
   - Kind: `Secret text`
   - ID: `github-token`
   - Secret: paste the token
4. Click **Create**

---

## Steps 11–13: Expose Jenkins via Nginx

**Step 11 — Install Nginx**

```bash
sudo apt install nginx -y
```

**Step 12 — Configure Nginx as a reverse proxy**

```bash
sudo nano /etc/nginx/sites-available/jenkins
```

Paste this (replace `your-vps-ip` with your actual IP or domain):

```nginx
server {
    listen 80;
    server_name your-vps-ip;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Save with `Ctrl+O`, `Enter`, `Ctrl+X`.

**Step 13 — Enable the config and open port 80**

```bash
sudo ln -s /etc/nginx/sites-available/jenkins /etc/nginx/sites-enabled/
sudo nginx -t          # must say: syntax is ok
sudo systemctl reload nginx
sudo ufw allow 80
```

Jenkins is now accessible at `http://your-vps-ip`. Optionally add HTTPS:

```bash
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d jenkins.yourdomain.com
```

---

## Steps 14–16: Dockerfile, Jenkinsfile, and Systemd Service

**Step 14 — Create a Dockerfile in your repo root**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ramellian-executive-travels.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 15 — Create a Jenkinsfile in your repo root**

```groovy
pipeline {
    agent any

    environment {
        IMAGE_NAME = 'ramellian-travels'
        VPS_USER   = 'debian'
        VPS_HOST   = 'your-vps-ip'
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
```

Commit and push both files to your repo.

**Step 16 — Create the systemd service on the VPS**

SSH into your VPS and run:

```bash
sudo nano /etc/systemd/system/ramellian.service
```

Paste:

```ini
[Unit]
Description=Ramellian Executive Travels API
After=docker.service
Requires=docker.service

[Service]
User=debian
Restart=always
ExecStartPre=-/usr/bin/docker rm -f ramellian
ExecStart=/usr/bin/docker run --name ramellian \
  --env-file /opt/app/.env \
  -p 8090:8090 \
  ramellian-travels:latest
ExecStop=/usr/bin/docker stop ramellian

[Install]
WantedBy=multi-user.target
```

Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable ramellian
```

---

## Steps 17–19: Jenkins Job, Webhook, and Test

**Step 17 — Create the Jenkins pipeline job**

1. Click **New Item**, name it `ramellian-deploy`, choose **Pipeline**, click OK
2. Under **Build Triggers**: check `GitHub hook trigger for GITScm polling`
3. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: your GitHub repo URL
   - Credentials: select `github-token`
   - Branch: `*/master`
4. Click **Save**

**Step 18 — Add a GitHub webhook**

1. Go to your GitHub repo → **Settings → Webhooks → Add webhook**
2. Fill in:
   - Payload URL: `http://your-vps-ip/github-webhook/`
   - Content type: `application/json`
   - Trigger: `Just the push event`
3. Click **Add webhook** — GitHub will ping it and show a green tick if Jenkins is reachable

**Step 19 — Test the full pipeline**

Push any change to `master`:

```bash
git add .
git commit -m "test: trigger CI pipeline"
git push origin master
```

Open Jenkins at `http://your-vps-ip` → open `ramellian-deploy` → watch the build trigger automatically → all stages should go green → your Docker container restarts on the VPS with the new build.
