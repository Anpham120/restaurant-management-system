// CI/CD tự động, không có bước duyệt tay.
//   feature/*  : kiểm migration → [test backend | test frontend | quét bảo mật mã nguồn] → tự merge vào develop
//   develop    : như trên → staging tạm thời (smoke test đọc + E2E ghi) → quét image, gắn tag phát hành
//                → [đẩy GHCR (nếu có token) | tự promote lên main (fast-forward)]
//   main       : deploy production đúng image develop đã kiểm (blue-green khi nginx có 2 slot), kiểm qua domain thật
//   [a | b]    : chạy song song (các bước độc lập, không bỏ bước nào)
// main không test lại: --ff-only nên commit trên main trùng SHA với commit develop vừa qua đủ test, staging và Trivy.
// Maven và Node chạy trong container tạm, dùng chung volume với Jenkins để đường dẫn workspace khớp.
// Kích hoạt: webhook GitHub. Dự phòng là quét multibranch mỗi phút (cấu hình job). Không dùng pollSCM:
// poll trùng lúc với webhook sẽ xếp thêm một build thứ hai cho cùng commit.

pipeline {
    agent any

    options {
        // Lưu trạng thái pipeline ít hơn xuống đĩa: chạy nhanh hơn; đổi lại build đang chạy không tự tiếp tục nếu Jenkins
        // khởi động lại giữa chừng (chạy lại build là đủ, mọi bước đều lặp lại an toàn)
        durabilityHint('PERFORMANCE_OPTIMIZED')
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        JENKINS_CONTAINER = 'jenkins'
        // Repo GitHub (dùng cho fetch/push bằng token trong các bước chạy ngoài workspace)
        REPO = 'github.com/Anpham120/restaurant-management-system.git'
        // Thư mục triển khai trên máy chủ (clone nhánh main, có .env thật). Xem docs/devops/PIPELINE.md
        DEPLOY_DIR = '/home/coder/rms-deploy'
        // Thư mục staging tạm thời (clone nhánh develop, tự tạo lần đầu). Không chứa secret thật.
        STAGING_DIR = '/home/coder/rms-staging'
        // Bản sao lưu database (scripts/backup-db.sh), deploy tạo một bản trước khi build
        BACKUP_DIR = '/home/coder/rms-backups'
        // Địa chỉ công khai (qua nginx + HTTPS) để kiểm production sau deploy
        PUBLIC_URL = 'https://rms.atlasplatform12.tech'
        // Chat nhận thông báo pipeline (cùng bot với Alertmanager). Chat id không phải bí mật, token nằm trong credential.
        TELEGRAM_CHAT_ID = '6822487436'
    }

    stages {
        stage('Kiểm migration') {
            when { not { branch 'main' } }
            steps {
                // Migration phải tương thích ngược (expand/contract) để quay lui image không cần khôi phục database
                sh 'scripts/check-migrations.sh'
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Kiểm tra song song') {
            when { not { branch 'main' } }
            // Ba việc độc lập chạy cùng lúc: tổng thời gian = việc lâu nhất (test backend) thay vì cộng dồn. Không bỏ bước nào;
            // một nhánh lỗi thì cả stage lỗi như trước, các nhánh còn lại vẫn chạy hết để log đủ thông tin. Kiểm migration vẫn chạy
            // trước (vài giây, lỗi thì dừng sớm không tốn thời gian test). RAM: Maven tối đa 3 GB + Node 2 GB, máy còn trống ~4 GB.
            parallel {
                stage('Test backend') {
                    steps {
                        sh '''
                            # Volume cache Maven mới tạo thuộc root, đổi quyền để user Jenkins ghi được
                            docker run --rm -v rms-m2-cache:/m2 alpine:3.20 chown "$(id -u):$(id -g)" /m2
                            # Giới hạn tài nguyên: CI chạy chung máy với production (4 CPU, 7 GB). Tối đa 2 CPU, 3 GB;
                            # cpu-shares 512 (production mặc định 1024): khi tranh CPU, production được ưu tiên gấp đôi.
                            docker run --rm --network host --cpus=2 --cpu-shares=512 --memory=3g \
                              --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE" \
                              --user "$(id -u):$(id -g)" --group-add "$(stat -c %g /var/run/docker.sock)" \
                              -e HOME=/tmp -v rms-m2-cache:/tmp/.m2 \
                              maven:3.9-eclipse-temurin-25 \
                              mvn -B -ntp -Dmaven.repo.local=/tmp/.m2/repository verify
                        '''
                    }
                    post {
                        failure { script { ghiBuocLoi() } }
                        // Kết quả test (biểu đồ pass/fail qua các build) và báo cáo độ phủ JaCoCo (artifact HTML)
                        always {
                            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                            sh '''
                                # awk chỉ in số (mawk không xử lý tốt chuỗi UTF-8); chữ tiếng Việt in bằng echo
                                if [ -f target/site/jacoco/jacoco.csv ]; then
                                  COV=$(awk -F, 'NR > 1 { m += $8; c += $9 } END { if (m + c) printf "%.1f%% (%d/%d", 100 * c / (m + c), c, m + c }' target/site/jacoco/jacoco.csv)
                                  echo "Độ phủ dòng code: $COV dòng)"
                                fi
                            '''
                        }
                    }
                }

                stage('Test frontend') {
                    steps {
                        sh '''
                            docker run --rm --cpus=2 --cpu-shares=512 --memory=2g --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE/frontend" \
                              --user "$(id -u):$(id -g)" -e HOME=/tmp \
                              node:22-alpine \
                              sh -c "npm ci --no-audit --no-fund && npm run typecheck && npm run build"
                        '''
                    }
                    post { failure { script { ghiBuocLoi() } } }
                }

                stage('Quét bảo mật mã nguồn') {
                    steps {
                        // Trivy: thư viện Maven/npm có lỗ hổng HIGH/CRITICAL đã có bản vá, hoặc secret lộ trong mã
                        sh 'scripts/security-scan.sh fs'
                    }
                    post { failure { script { ghiBuocLoi() } } }
                }
            }
        }

        stage('Merge feature vào develop') {
            when { branch pattern: 'feature/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/auto-merge.sh develop'
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Staging smoke test') {
            when { branch 'develop' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    // Dựng bản develop ở môi trường tách biệt trên bản sao lưu production mới nhất (chỉ đọc), thử các
                    // chức năng chính và quét đọc mọi module rồi hủy. Lỗi thì không promote.
                    // Container docker:27-cli (có sẵn docker compose, git) mount thư mục staging đúng đường dẫn trên host; lệnh
                    // trong '...' do shell của container chạy. trap: luôn trả quyền thư mục về user coder, kể cả khi lỗi.
                    sh '''
                        docker run --rm --network host \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          -v "$STAGING_DIR:$STAGING_DIR" -w "$STAGING_DIR" -v "$BACKUP_DIR:$BACKUP_DIR:ro" \
                          -e GH_TOKEN -e BACKUP_DIR -e GIT_COMMIT -e REPO \
                          docker:27-cli sh -euc '
                            trap "chown -R 1000:1000 ." EXIT
                            apk add -q --no-cache bash curl jq
                            git config --global --add safe.directory "$PWD"
                            [ -d .git ] || git init -q
                            git fetch -q "https://x-access-token:$GH_TOKEN@$REPO" develop
                            git reset -q --hard "$GIT_COMMIT"
                            scripts/staging-test.sh
                          '
                    '''
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Quét bảo mật image') {
            when { branch 'develop' }
            steps {
                // Image vừa build ở staging. Qua quét thì gắn tag phát hành theo commit: production chạy đúng image này.
                sh 'scripts/security-scan.sh image rms-staging-app rms-staging-web'
                sh 'scripts/tag-release.sh "$GIT_COMMIT"'
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Phát hành') {
            when { branch 'develop' }
            // Chỉ chạy khi image đã qua Trivy và được gắn tag (stage trước). Đẩy GHCR là bản lưu, không chặn và không liên quan
            // đến promote, nên chạy song song: main bắt đầu deploy sớm hơn ~10 giây.
            parallel {
                stage('Đẩy image lên GHCR') {
                    steps {
                        // Bản lưu image ngoài máy chủ. Không chặn pipeline: thiếu credential ghcr-token hoặc registry lỗi thì chỉ ghi log,
                        // deploy vẫn dùng image phát hành trên máy.
                        script {
                            try {
                                withCredentials([string(credentialsId: 'ghcr-token', variable: 'GHCR_TOKEN')]) {
                                    sh 'scripts/push-release.sh "$GIT_COMMIT"'
                                }
                            } catch (err) {
                                echo "Bỏ qua đẩy GHCR: ${err.message}"
                            }
                        }
                    }
                }

                stage('Promote develop lên main') {
                    steps {
                        withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                            sh 'scripts/auto-merge.sh main'
                        }
                    }
                    post { failure { script { ghiBuocLoi() } } }
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
                    // Như staging: container docker:27-cli, mount thư mục triển khai đúng đường dẫn trên host, checkout đúng
                    // commit của build. trap: luôn xóa .env (kể cả khi deploy lỗi) và trả quyền về user coder (uid 1000).
                    sh '''
                        docker run --rm --network host \
                          -v /var/run/docker.sock:/var/run/docker.sock \
                          -v "$DEPLOY_DIR:$DEPLOY_DIR" -w "$DEPLOY_DIR" -v "$BACKUP_DIR:$BACKUP_DIR" -v /etc/nginx:/etc/nginx:ro \
                          -e GH_TOKEN -e ENV_B64 -e BACKUP_DIR -e GIT_COMMIT -e PUBLIC_URL -e REPO \
                          docker:27-cli sh -euc '
                            trap "rm -f .env; chown -R 1000:1000 . $BACKUP_DIR" EXIT
                            apk add -q --no-cache bash curl
                            git config --global --add safe.directory "$PWD"
                            git fetch -q "https://x-access-token:$GH_TOKEN@$REPO" main
                            git reset -q --hard "$GIT_COMMIT"
                            (umask 077; printf "%s" "$ENV_B64" | base64 -d > .env)
                            scripts/deploy.sh
                          '
                    '''
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }
    }

    post {
        // Telegram: báo build lỗi ở mọi nhánh, chỉ báo thành công khi main deploy xong (tránh 3 tin cho mỗi lần push)
        failure {
            script { notifyTelegram(false) }
        }
        success {
            script {
                if (env.BRANCH_NAME == 'main') {
                    notifyTelegram(true)
                }
            }
        }
        // Dọn sau mỗi lần chạy: image mồ côi và cache build quá 48 giờ. Không đụng container, volume (cache Maven, Trivy),
        // image đang dùng. Không giới hạn tổng cache: phần lớn cache là lớp của image phát hành đang dùng (không dọn được),
        // phần còn lại là cache thư viện Maven của staging; dọn đi chỉ làm build chậm hơn.
        always {
            sh 'docker image prune -f || true; docker builder prune -f --filter until=48h || true'
        }
    }
}

// Ghi tên stage lỗi (gọi trong post failure của từng stage) để tin Telegram nói rõ hỏng ở bước nào.
// Nhiều stage song song cùng lỗi thì ghi đủ.
def ghiBuocLoi() {
    env.BUOC_LOI = env.BUOC_LOI ? "${env.BUOC_LOI}, ${env.STAGE_NAME}" : env.STAGE_NAME
}

// Gửi tin Telegram dễ đọc: kết quả, thay đổi gì (của nhánh tính năng nào, ai viết), commit, slot đang phục vụ, link log.
// Commit trên develop/main là commit merge "[auto-merge]", nên lấy nội dung từ commit cuối của nhánh tính năng (HEAD^2).
// Thiếu credential telegram-bot-token hoặc mạng lỗi thì chỉ ghi log, không làm hỏng build. Token chỉ dùng trong sh nháy
// đơn để không lộ qua Groovy.
def notifyTelegram(boolean ok) {
    try {
        def info = sh(returnStdout: true, script: '''
            if git rev-parse -q --verify HEAD^2 >/dev/null; then
              nhanh=$(git log -1 --format=%s | sed -nE 's/^Merge (.+) vào .*/\\1/p')
              thaydoi=$(git log -1 --format=%s HEAD^2 | sed -E 's/^ +//')
              tacgia=$(git log -1 --format=%an HEAD^2)
              them=$(( $(git rev-list --count HEAD^1..HEAD^2) - 1 ))
              if [ "$them" -gt 0 ]; then thaydoi="$thaydoi (+$them commit khác)"; fi
            else
              nhanh=$BRANCH_NAME
              thaydoi=$(git log -1 --format=%s | sed -E 's/^ +//')
              tacgia=$(git log -1 --format=%an)
            fi
            slot=$(docker ps --format '{{.Names}}' \
              | sed -nE 's/^restaurant-management-system-app-1$/A (8081)/p; s/^restaurant-management-system-app-b-1$/B (8086)/p' \
              | sort | paste -sd '+' | sed 's/+/ và /')
            echo "Thay đổi: $thaydoi"
            echo "Nhánh: ${nhanh:-$BRANCH_NAME} · $tacgia"
            echo "Commit: $(git rev-parse --short HEAD)"
            echo "Đang phục vụ: slot ${slot:-không rõ}"
        ''').trim()
        long giay = (System.currentTimeMillis() - currentBuild.startTimeInMillis).intdiv(1000)
        def thoiGian = giay >= 60 ? "${giay.intdiv(60)} phút ${giay % 60} giây" : "${giay} giây"
        def build = "${env.BRANCH_NAME} #${env.BUILD_NUMBER} · ${thoiGian}"
        def text
        if (ok) {
            text = "✅ ĐÃ LÊN PRODUCTION\n${info}\nBuild: ${build}\nWeb: ${env.PUBLIC_URL}"
        } else {
            def hauQua = env.BRANCH_NAME == 'main' ? 'Deploy lỗi: deploy.sh giữ hoặc bật lại slot cũ, xem log để chắc chắn.'
                       : env.BRANCH_NAME == 'develop' ? 'Không promote lên main. Production không đổi.'
                       : env.BRANCH_NAME.startsWith('feature/') ? 'Chưa merge vào develop. Production không đổi.'
                       : 'Production không đổi.'
            text = "❌ PIPELINE DỪNG · ${env.BRANCH_NAME} #${env.BUILD_NUMBER}\nBước lỗi: ${env.BUOC_LOI ?: 'không rõ, xem log'}\n${info}\nBuild: ${build}\n${hauQua}"
        }
        withCredentials([string(credentialsId: 'telegram-bot-token', variable: 'TG_TOKEN')]) {
            withEnv(["TG_TEXT=${text}\nLog: ${env.BUILD_URL}console"]) {
                sh '''curl -fsS -o /dev/null --max-time 10 "https://api.telegram.org/bot$TG_TOKEN/sendMessage" \
                      --data-urlencode chat_id="$TELEGRAM_CHAT_ID" --data-urlencode text="$TG_TEXT"'''
            }
        }
    } catch (err) {
        echo "Không gửi được Telegram: ${err.message}"
    }
}
