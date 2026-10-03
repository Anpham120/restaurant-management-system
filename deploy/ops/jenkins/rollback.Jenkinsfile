// P0-09: puts the images of an earlier commit back on staging or production (docs-core/11 section 11.9). The images
// come from GHCR, where every commit Jenkins deployed has them; the compose file and backup scripts come from that
// commit, deploy.sh from this checkout. The parameters TARGET and COMMIT are set on the job (deploy/ops/jenkins/casc.yaml).
pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        timestamps()
    }
    environment {
        APP_DIR = "${params.TARGET == 'production' ? 'khoibep-rms' : 'khoibep-rms-staging'}"
        SITE_URL = "${params.TARGET == 'production' ? env.SITE_URL_PRODUCTION : env.SITE_URL_STAGING}"
    }
    stages {
        stage('Files of that commit') {
            steps {
                script {
                    if (!(params.COMMIT ==~ /[0-9a-f]{40}/)) {
                        error('Cần mã commit đủ 40 ký tự, chữ thường (lấy ở tab Commits trên GitHub)')
                    }
                }
                sh '''
                    git fetch --depth=1 origin "$COMMIT"
                    git checkout "$COMMIT" -- deploy/docker-compose.prod.yml deploy/backup.sh deploy/restore.sh
                '''
            }
        }
        stage('Approve production') {
            when { expression { params.TARGET == 'production' } }
            steps {
                timeout(time: 60, unit: 'MINUTES') {
                    input message: "Quay production về ${params.COMMIT.take(7)}?", ok: 'Quay lại'
                }
            }
        }
        stage('Roll back') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sshagent(credentials: ['app-server']) {
                        sh 'sh deploy/ops/jenkins/deploy-remote.sh "$APP_DIR" "$COMMIT"'
                    }
                }
            }
        }
        stage('Health check') {
            steps {
                sh 'sh deploy/ops/jenkins/health-check.sh "$SITE_URL"'
            }
        }
    }
}
