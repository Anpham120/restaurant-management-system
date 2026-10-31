// CI/CD tự động, không có bước duyệt tay.
//   feature/*  : test → quét bảo mật → tự merge vào develop
//   develop    : test → quét bảo mật → staging tạm thời + smoke test → quét image → tự promote lên main (fast-forward)
//   main       : test → quét bảo mật → deploy production (tự quay lui nếu lỗi)
// Maven và Node chạy trong container tạm, dùng chung volume với Jenkins để đường dẫn workspace khớp.
// Kích hoạt: webhook GitHub. Dự phòng là quét multibranch mỗi phút (cấu hình job). Không dùng pollSCM:
// poll trùng lúc với webhook sẽ xếp thêm một build thứ hai cho cùng commit.

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        JENKINS_CONTAINER = 'jenkins'
        // Thư mục triển khai trên máy chủ (clone nhánh main, có .env thật). Xem docs/devops/PIPELINE.md
        DEPLOY_DIR = '/home/coder/rms-deploy'
        // Thư mục staging tạm thời (clone nhánh develop, tự tạo lần đầu). Không chứa secret thật.
        STAGING_DIR = '/home/coder/rms-staging'
        // Bản sao lưu database (scripts/backup-db.sh), deploy tạo một bản trước khi build
        BACKUP_DIR = '/home/coder/rms-backups'
        // Chat nhận thông báo pipeline (cùng bot với Alertmanager). Chat id không phải bí mật, token nằm trong credential.
        TELEGRAM_CHAT_ID = '6822487436'
    }

    stages {
        stage('Test backend') {
            steps {
                sh '''
                    # Volume cache Maven mới tạo thuộc root, đổi quyền để user Jenkins ghi được
                    docker run --rm -v rms-m2-cache:/m2 alpine:3.20 chown "$(id -u):$(id -g)" /m2
                    docker run --rm --network host \
                      --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE" \
                      --user "$(id -u):$(id -g)" --group-add "$(stat -c %g /var/run/docker.sock)" \
                      -e HOME=/tmp -v rms-m2-cache:/tmp/.m2 \
                      maven:3.9-eclipse-temurin-21 \
                      mvn -B -Dmaven.repo.local=/tmp/.m2/repository verify
                '''
            }
        }

        stage('Test frontend') {
            steps {
                sh '''
                    docker run --rm --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE/frontend" \
                      --user "$(id -u):$(id -g)" -e HOME=/tmp \
                      node:22-alpine \
                      sh -c "npm ci --no-audit --no-fund && npm run typecheck && npm run build"
                '''
            }
        }

        stage('Quét bảo mật mã nguồn') {
            steps {
                // Trivy: thư viện Maven/npm có lỗ hổng HIGH/CRITICAL đã có bản vá, hoặc secret lộ trong mã
                sh 'scripts/security-scan.sh fs'
            }
        }

        stage('Merge feature vào develop') {
            when { branch pattern: 'feature/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/auto-merge.sh develop'
                }
            }
        }

        stage('Staging smoke test') {
            when { branch 'develop' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    // Dựng bản develop ở môi trường tách biệt, thử các chức năng chính rồi hủy. Lỗi thì không promote.
                    sh '''
                        docker run --rm --network host \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          -v "$STAGING_DIR:$STAGING_DIR" -w "$STAGING_DIR" \
                          -e GH_TOKEN \
                          docker:27-cli \
                          sh -c "apk add -q --no-cache docker-cli-compose git curl bash && git config --global --add safe.directory \\"\\$PWD\\" && ([ -d .git ] || git init -q) && git fetch -q \\"https://x-access-token:\\$GH_TOKEN@github.com/Anpham120/restaurant-management-system.git\\" develop && git reset -q --hard FETCH_HEAD && scripts/staging-test.sh; rc=\\$?; chown -R 1000:1000 .; exit \\$rc"
                    '''
                }
            }
        }

        stage('Quét bảo mật image') {
            when { branch 'develop' }
            steps {
                // Image vừa build ở staging. main fast-forward từ develop nên production build từ cùng commit này.
                sh 'scripts/security-scan.sh image rms-staging-app rms-staging-web'
            }
        }

        stage('Promote develop lên main') {
            when { branch 'develop' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/auto-merge.sh main'
                }
            }
        }

        stage('Deploy production') {
            when { branch 'main' }
            steps {
                // rms-prod-env: nội dung .env production mã hóa base64 một dòng (Secret text). Chỉ ghi ra đĩa trong lúc deploy.
                withCredentials([
                    string(credentialsId: 'github-token', variable: 'GH_TOKEN'),
                    string(credentialsId: 'rms-prod-env', variable: 'ENV_B64')
                ]) {
                    // Chạy trong container docker CLI, mount thư mục triển khai đúng đường dẫn trên host.
                    // Sau khi deploy, xóa .env và trả quyền thư mục về user coder (uid 1000 — kiểm tra bằng: id -u coder).
                    sh '''
                        docker run --rm --network host \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          -v "$DEPLOY_DIR:$DEPLOY_DIR" -w "$DEPLOY_DIR" -v "$BACKUP_DIR:$BACKUP_DIR" \
                          -e GH_TOKEN -e ENV_B64 -e BACKUP_DIR \
                          docker:27-cli \
                          sh -c "apk add -q --no-cache docker-cli-compose git curl bash && git config --global --add safe.directory \\"\\$PWD\\" && git fetch -q \\"https://x-access-token:\\$GH_TOKEN@github.com/Anpham120/restaurant-management-system.git\\" main && git reset -q --hard FETCH_HEAD && (umask 077; printf '%s' \\"\\$ENV_B64\\" | base64 -d > .env) && scripts/deploy.sh; rc=\\$?; rm -f .env; chown -R 1000:1000 . \\$BACKUP_DIR; exit \\$rc"
                    '''
                }
            }
        }
    }

    post {
        // Telegram: báo build lỗi ở mọi nhánh, chỉ báo thành công khi main deploy xong (tránh 3 tin cho mỗi lần push)
        failure {
            script { notifyTelegram("❌ Build ${env.BRANCH_NAME} #${env.BUILD_NUMBER} THẤT BẠI") }
        }
        success {
            script {
                if (env.BRANCH_NAME == 'main') {
                    notifyTelegram("✅ Đã deploy lên production")
                }
            }
        }
        // Dọn sau mỗi lần chạy: image mồ côi và cache build quá 48 giờ. Không đụng container, volume, image đang dùng.
        always {
            sh 'docker image prune -f || true; docker builder prune -f --filter until=48h || true'
        }
    }
}

// Gửi tin Telegram kèm commit và link build. Thiếu credential telegram-bot-token hoặc mạng lỗi thì chỉ ghi log,
// không làm hỏng build. Token chỉ dùng trong sh nháy đơn để không lộ qua Groovy.
def notifyTelegram(String title) {
    try {
        def commit = sh(script: 'git log -1 --format="%h %s"', returnStdout: true).trim()
        withCredentials([string(credentialsId: 'telegram-bot-token', variable: 'TG_TOKEN')]) {
            withEnv(["TG_TEXT=${title}\n${commit}\n${env.BUILD_URL}console"]) {
                sh '''curl -fsS -o /dev/null --max-time 10 "https://api.telegram.org/bot$TG_TOKEN/sendMessage" \
                      --data-urlencode chat_id="$TELEGRAM_CHAT_ID" --data-urlencode text="$TG_TEXT"'''
            }
        }
    } catch (err) {
        echo "Không gửi được Telegram: ${err.message}"
    }
}
