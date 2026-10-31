// CI/CD tự động, không có bước duyệt tay.
//   feature/*  : test → tự merge vào develop
//   develop    : test → staging tạm thời + smoke test → tự promote lên main (fast-forward)
//   main       : test → deploy production (tự quay lui nếu lỗi)
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
                          -v "$DEPLOY_DIR:$DEPLOY_DIR" -w "$DEPLOY_DIR" \
                          -e GH_TOKEN -e ENV_B64 \
                          docker:27-cli \
                          sh -c "apk add -q --no-cache docker-cli-compose git curl bash && git config --global --add safe.directory \\"\\$PWD\\" && git fetch -q \\"https://x-access-token:\\$GH_TOKEN@github.com/Anpham120/restaurant-management-system.git\\" main && git reset -q --hard FETCH_HEAD && (umask 077; printf '%s' \\"\\$ENV_B64\\" | base64 -d > .env) && scripts/deploy.sh; rc=\\$?; rm -f .env; chown -R 1000:1000 .; exit \\$rc"
                    '''
                }
            }
        }
    }

    // Dọn sau mỗi lần chạy: image mồ côi và cache build quá 48 giờ. Không đụng container, volume, image đang dùng.
    post {
        always {
            sh 'docker image prune -f || true; docker builder prune -f --filter until=48h || true'
        }
    }
}
