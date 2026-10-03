// P0-08, P0-09: the CI/CD of the project, on Jenkins (docs-core/09 section 9.6, docs-core/11 sections 11.11, 11.12).
// Pull requests: the tests on the PR merged into its target, the two images, then the acceptance scenario on those
// very images; Jenkins reports the result on the PR, and the GitHub rulesets ask for it before merging.
// New commits of develop and main: the same, then a Trivy scan, the images pushed to GHCR, and a deploy: develop to
// staging, main to production after approval. Each test step runs in its own container through this server's Docker.
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
        // The test steps run in these images (Java 21, Node 24), as the project's tests always have.
        JDK_IMAGE = 'eclipse-temurin:21-jdk'
        NODE_IMAGE = 'node:24-bookworm-slim'
        TRIVY = 'aquasec/trivy:0.75.0'
    }
    stages {
        stage('Newest commit only') {
            when { anyOf { branch 'develop'; branch 'main' } }
            steps {
                script {
                    if (!isBranchHead()) {
                        env.SKIPPED = 'true'
                        currentBuild.result = 'NOT_BUILT'
                    }
                }
            }
        }
        // One build at a time tests and builds, so two builds never take the server's memory from the app together.
        stage('Test and build') {
            when {
                beforeOptions true
                not { environment name: 'SKIPPED', value: 'true' }
            }
            options { lock('khoibep-build') }
            stages {
                stage('ERD') {
                    steps {
                        sh 'sh deploy/ops/jenkins/in-container.sh "$NODE_IMAGE" . "node scripts/check-erd.mjs"'
                    }
                }
                // PostgreSQL comes from Testcontainers, through the server's Docker, and publishes a port there. The
                // tests reach it on the server's own loopback (host network), which ufw leaves open; through docker0
                // ufw would drop it. Maven keeps its downloads in a volume.
                stage('Backend') {
                    steps {
                        sh '''
                            sh deploy/ops/jenkins/in-container.sh "$JDK_IMAGE" backend \
                                "./mvnw -B verify && sh ../scripts/coverage-summary.sh" \
                                -v khoibep-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock \
                                --network host -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
                        '''
                    }
                }
                stage('Frontend') {
                    steps {
                        sh '''
                            sh deploy/ops/jenkins/in-container.sh "$NODE_IMAGE" frontend \
                                "npm ci --no-audit --no-fund && npm run lint && npm test && npm run build" \
                                -v khoibep-npm:/root/.npm
                        '''
                    }
                }
                stage('Monitoring configs') {
                    steps {
                        sh 'sh scripts/check-monitoring.sh'
                    }
                }
                // Labelled, so that the post step can clear them once nothing runs them.
                stage('Build images') {
                    steps {
                        sh '''
                            docker build --label khoibep.build=true -t "ghcr.io/$GHCR_OWNER/rms-backend:$GIT_COMMIT" backend
                            docker build --label khoibep.build=true -t "ghcr.io/$GHCR_OWNER/rms-frontend:$GIT_COMMIT" frontend
                        '''
                    }
                }
                // The acceptance scenario on the very images that will be deployed.
                stage('E2E') {
                    steps {
                        sh 'sh deploy/ops/jenkins/e2e.sh'
                    }
                }
                // P0-06 on this side: known CRITICAL and HIGH vulnerabilities with a fix, printed, never blocking.
                stage('Scan images') {
                    when { anyOf { branch 'develop'; branch 'main' } }
                    steps {
                        sh '''
                            for image in rms-backend rms-frontend; do
                                docker run --rm -v /var/run/docker.sock:/var/run/docker.sock "$TRIVY" image --quiet \
                                    --severity CRITICAL,HIGH --ignore-unfixed --exit-code 0 "ghcr.io/$GHCR_OWNER/$image:$GIT_COMMIT"
                            done
                        '''
                    }
                }
            }
        }
        stage('Push images') {
            when {
                anyOf { branch 'develop'; branch 'main' }
                not { environment name: 'SKIPPED', value: 'true' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh '''
                        printf '%s' "$GH_TOKEN" | docker login ghcr.io -u "$GH_USER" --password-stdin
                        for image in rms-backend rms-frontend; do
                            docker tag "ghcr.io/$GHCR_OWNER/$image:$GIT_COMMIT" "ghcr.io/$GHCR_OWNER/$image:$CHANNEL"
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
                timeout(time: 60, unit: 'MINUTES') {
                    input message: "Deploy ${env.GIT_COMMIT.take(7)} lên production?", ok: 'Deploy'
                }
            }
        }
        stage('Deploy') {
            when {
                anyOf { branch 'develop'; branch 'main' }
                not { environment name: 'SKIPPED', value: 'true' }
            }
            steps {
                script {
                    // Checked again right before deploying: a newer commit may have arrived while this one waited.
                    if (!isBranchHead()) {
                        env.SKIPPED = 'true'
                        currentBuild.result = 'NOT_BUILT'
                    } else {
                        withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                            sshagent(credentials: ['app-server']) {
                                sh 'sh deploy/ops/jenkins/deploy-remote.sh "$APP_DIR" "$GIT_COMMIT"'
                            }
                        }
                    }
                }
            }
        }
        stage('Health check') {
            when {
                anyOf { branch 'develop'; branch 'main' }
                not { environment name: 'SKIPPED', value: 'true' }
            }
            steps {
                sh 'sh deploy/ops/jenkins/health-check.sh "$SITE_URL"'
            }
        }
    }
    post {
        // One server keeps its disk: a pull request's images go right away; deployed ones once nothing has run them
        // for three days (images in use are never removed); build cache after a week.
        always {
            script {
                if (env.BRANCH_NAME != 'develop' && env.BRANCH_NAME != 'main') {
                    sh 'docker image rm "ghcr.io/$GHCR_OWNER/rms-backend:$GIT_COMMIT" "ghcr.io/$GHCR_OWNER/rms-frontend:$GIT_COMMIT" || true'
                }
            }
            sh '''
                docker image prune -af --filter label=khoibep.build=true --filter until=72h || true
                docker builder prune -f --filter until=168h || true
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
