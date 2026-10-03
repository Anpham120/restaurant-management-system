# P0-08, P0-09: Jenkins, the primary CI/CD (docs-core/09 section 9.6). The Docker CLI and Compose run the test
# containers, the E2E stack and the image builds through this server's Docker (socket mounted in
# deploy/ops/docker-compose.yml); ssh, jq and curl serve the Jenkinsfile. The plugins come from plugins.txt, the
# whole setup from casc.yaml (Configuration as Code).
FROM jenkins/jenkins:2.580.1-lts-jdk21

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg jq openssh-client \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*

USER jenkins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
