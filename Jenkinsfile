// P0-08: continuous delivery on Jenkins, the primary deployer (docs-core/09 section 9.6, docs-core/11 section 11.11).
// GitHub Actions runs the tests of every push; this pipeline waits for them, builds and pushes the images, deploys
// develop to staging and main to production (after approval), and reports on the commit as "jenkins/deploy".
// When Jenkins does not answer or stays silent, the cd-gate job of GitHub Actions deploys instead.
pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
    }
    environment {
        REPO = 'Anpham120/restaurant-management-system'
        TARGET = "${env.BRANCH_NAME == 'main' ? 'production' : 'staging'}"
        APP_DIR = "${env.BRANCH_NAME == 'main' ? 'khoibep-rms' : 'khoibep-rms-staging'}"
        CHANNEL = "${env.BRANCH_NAME == 'main' ? 'latest' : 'develop'}"
        SITE_URL = "${env.BRANCH_NAME == 'main' ? env.SITE_URL_PRODUCTION : env.SITE_URL_STAGING}"
        // The CI jobs of .github/workflows/ci-cd.yml that must pass on this commit before it is deployed.
        REQUIRED_CHECKS = 'Backend - build and test|Frontend - lint, test, build|E2E - acceptance scenario|Monitoring - check configs'
        TRIVY = 'aquasec/trivy:0.75.0'
    }
    stages {
        stage('Report to GitHub') {
            steps {
                reportStatus('pending', "Jenkins đang deploy ${env.TARGET}")
            }
        }
        stage('Newest commit only') {
            steps {
                script {
                    if (!isBranchHead()) {
                        env.SKIPPED = 'true'
                        reportStatus('success', 'Bỏ qua: nhánh đã có commit mới hơn')
                        currentBuild.result = 'NOT_BUILT'
                    }
                }
            }
        }
        stage('Wait for the tests on GitHub') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    timeout(time: 40, unit: 'MINUTES') {
                        sh 'sh deploy/ops/jenkins/wait-for-checks.sh'
                    }
                }
            }
        }
        stage('Build images') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                sh '''
                    docker build -t "ghcr.io/$GHCR_OWNER/rms-backend:$GIT_COMMIT" -t "ghcr.io/$GHCR_OWNER/rms-backend:$CHANNEL" backend
                    docker build -t "ghcr.io/$GHCR_OWNER/rms-frontend:$GIT_COMMIT" -t "ghcr.io/$GHCR_OWNER/rms-frontend:$CHANNEL" frontend
                '''
            }
        }
        // P0-06 on this side: known CRITICAL and HIGH vulnerabilities with a fix, printed in the log, never blocking.
        stage('Scan images') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                sh '''
                    for image in rms-backend rms-frontend; do
                        docker run --rm -v /var/run/docker.sock:/var/run/docker.sock "$TRIVY" image --quiet \
                            --severity CRITICAL,HIGH --ignore-unfixed --exit-code 0 "ghcr.io/$GHCR_OWNER/$image:$GIT_COMMIT"
                    done
                '''
            }
        }
        stage('Push images') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh '''
                        printf '%s' "$GH_TOKEN" | docker login ghcr.io -u "$GH_USER" --password-stdin
                        for image in rms-backend rms-frontend; do
                            docker push "ghcr.io/$GHCR_OWNER/$image:$GIT_COMMIT"
                            docker push "ghcr.io/$GHCR_OWNER/$image:$CHANNEL"
                        done
                        docker logout ghcr.io
                    '''
                }
            }
        }
        stage('Approve production') {
            when {
                branch 'main'
                not { environment name: 'SKIPPED', value: 'true' }
            }
            steps {
                reportStatus('pending', 'Chờ duyệt deploy production trên Jenkins')
                timeout(time: 60, unit: 'MINUTES') {
                    input message: "Deploy ${env.GIT_COMMIT.take(7)} lên production?", ok: 'Deploy'
                }
            }
        }
        stage('Deploy') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                script {
                    // Checked again right before deploying: a newer commit may have arrived while this one waited.
                    if (!isBranchHead()) {
                        env.SKIPPED = 'true'
                        reportStatus('success', 'Bỏ qua: nhánh đã có commit mới hơn')
                        currentBuild.result = 'NOT_BUILT'
                    } else {
                        deployToAppServer()
                    }
                }
            }
        }
        stage('Health check') {
            when { not { environment name: 'SKIPPED', value: 'true' } }
            steps {
                sh '''
                    for i in $(seq 1 30); do
                        if curl -fsS "$SITE_URL" | grep -q '"status":"UP"'; then exit 0; fi
                        sleep 5
                    done
                    echo "$SITE_URL does not answer UP" >&2
                    exit 1
                '''
            }
        }
    }
    post {
        success {
            script {
                if (env.SKIPPED != 'true') {
                    reportStatus('success', "Jenkins đã deploy ${env.TARGET}")
                }
            }
        }
        failure {
            reportStatus('failure', "Jenkins deploy ${env.TARGET} lỗi")
        }
        aborted {
            reportStatus('failure', "Deploy ${env.TARGET} bị dừng trên Jenkins")
        }
    }
}

/** Copies the compose file and scripts to the application server and runs deploy.sh there (one deploy at a time). */
void deployToAppServer() {
    withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        sshagent(credentials: ['app-server']) {
            sh '''
                SSH="ssh -o StrictHostKeyChecking=accept-new deploy@$APP_SERVER_HOST"
                scp -o StrictHostKeyChecking=accept-new deploy/docker-compose.prod.yml deploy/backup.sh \
                    deploy/restore.sh deploy/deploy.sh "deploy@$APP_SERVER_HOST:$APP_DIR/"
                printf '%s' "$GH_TOKEN" | $SSH "docker login ghcr.io -u $GH_USER --password-stdin"
                $SSH "sh ~/$APP_DIR/deploy.sh $APP_DIR $GIT_COMMIT; status=\\$?; docker logout ghcr.io; exit \\$status"
            '''
        }
    }
}

/** True when this build's commit is still the newest one of its branch on GitHub. */
boolean isBranchHead() {
    String head = sh(returnStdout: true,
            script: "git ls-remote https://github.com/${env.REPO}.git refs/heads/${env.BRANCH_NAME} | cut -f1").trim()
    echo "Head of ${env.BRANCH_NAME}: ${head}, this build: ${env.GIT_COMMIT}"
    return head == env.GIT_COMMIT
}

/** The "jenkins/deploy" status that the cd-gate job of GitHub Actions reads. Never fails the build. */
void reportStatus(String state, String description) {
    withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
        withEnv(["STATUS_STATE=${state}", "STATUS_DESCRIPTION=${description}"]) {
            sh '''
                jq -n --arg state "$STATUS_STATE" --arg description "$STATUS_DESCRIPTION" --arg url "$BUILD_URL" \
                    '{state: $state, context: "jenkins/deploy", description: $description, target_url: $url}' \
                | curl -fsS -o /dev/null -X POST -H "Authorization: Bearer $GH_TOKEN" \
                    -H "Accept: application/vnd.github+json" --data @- \
                    "https://api.github.com/repos/$REPO/statuses/$GIT_COMMIT" \
                || echo "Could not report $STATUS_STATE to GitHub; GitHub Actions takes over after 5 minutes of silence"
            '''
        }
    }
}
