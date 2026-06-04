#!/bin/bash
# Jenkins server setup script for GCP (cloud-init / user-data)
# Run once on a fresh Ubuntu 22.04 VM to install Jenkins + Docker + kubectl + gcloud
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

# ─── Update system ───────────────────────────────────────────────────────────
apt-get update -y
apt-get upgrade -y

# ─── Java 21 ─────────────────────────────────────────────────────────────────
apt-get install -y curl gnupg2 software-properties-common apt-transport-https wget

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | \
    gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -sc) main" \
    > /etc/apt/sources.list.d/adoptium.list
apt-get update -y
apt-get install -y temurin-21-jdk

# ─── Jenkins LTS ─────────────────────────────────────────────────────────────
wget -qO /etc/apt/trusted.gpg.d/jenkins.asc \
    https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key
echo "deb https://pkg.jenkins.io/debian-stable binary/" \
    > /etc/apt/sources.list.d/jenkins.list
apt-get update -y
apt-get install -y jenkins

# Configure Jenkins to use Java 21
JAVA21=$(update-alternatives --list java | grep "21" | head -1)
if [ -n "$JAVA21" ]; then
    sed -i "s|^JAVA=.*|JAVA=$JAVA21|" /etc/default/jenkins 2>/dev/null || true
fi

# ─── Docker ──────────────────────────────────────────────────────────────────
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    gpg --dearmor -o /etc/apt/trusted.gpg.d/docker.gpg
echo "deb [arch=$(dpkg --print-architecture)] https://download.docker.com/linux/ubuntu \
    $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io

usermod -aG docker jenkins
usermod -aG docker ubuntu

# ─── kubectl ─────────────────────────────────────────────────────────────────
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.34/deb/Release.key | \
    gpg --dearmor -o /etc/apt/trusted.gpg.d/kubernetes.gpg
echo "deb [signed-by=/etc/apt/trusted.gpg.d/kubernetes.gpg] https://pkgs.k8s.io/core:/stable:/v1.34/deb/ /" \
    > /etc/apt/sources.list.d/kubernetes.list
apt-get update -y
apt-get install -y kubectl

# ─── gcloud SDK + gke-gcloud-auth-plugin ─────────────────────────────────────
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | \
    gpg --dearmor -o /etc/apt/trusted.gpg.d/cloud.google.gpg
echo "deb [signed-by=/etc/apt/trusted.gpg.d/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" \
    > /etc/apt/sources.list.d/google-cloud-sdk.list
apt-get update -y
apt-get install -y google-cloud-cli google-cloud-cli-gke-gcloud-auth-plugin

# ─── Trivy ───────────────────────────────────────────────────────────────────
curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin

# ─── Locust ──────────────────────────────────────────────────────────────────
apt-get install -y python3-pip
pip3 install locust --break-system-packages

# ─── GitHub CLI ──────────────────────────────────────────────────────────────
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | \
    gpg --dearmor -o /etc/apt/trusted.gpg.d/githubcli.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/trusted.gpg.d/githubcli.gpg] \
    https://cli.github.com/packages stable main" \
    > /etc/apt/sources.list.d/github-cli.list
apt-get update -y
apt-get install -y gh

# ─── Start services ──────────────────────────────────────────────────────────
systemctl enable jenkins docker
systemctl start docker
systemctl start jenkins

# ─── Open firewall ports ─────────────────────────────────────────────────────
ufw --force enable
ufw allow OpenSSH
ufw allow 8080/tcp   # Jenkins UI
ufw allow 50000/tcp  # Jenkins agent port

# ─── Print initial admin password ────────────────────────────────────────────
echo "=========================================="
echo "Jenkins setup complete."
echo "URL: http://$(curl -s -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip):8080"
echo "Initial password:"
cat /var/lib/jenkins/secrets/initialAdminPassword 2>/dev/null || \
    echo "(not yet generated — wait 1-2 min and run: sudo cat /var/lib/jenkins/secrets/initialAdminPassword)"
echo "=========================================="
