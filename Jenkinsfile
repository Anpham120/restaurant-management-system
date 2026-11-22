// CI/CD tự động kiểu GitOps, không có bước duyệt tay. Jenkins chạy ở máy 2; production chạy Kubernetes ở máy 1, do Argo CD
// triển khai theo Git. Jenkins không deploy trực tiếp: chỉ ghi tag image vào Git rồi chờ Argo CD báo kết quả.
//   feature/*          : kiểm migration → [test backend | test frontend | quét bảo mật mã nguồn] → tự merge vào develop
//   develop (commit M) : như trên → staging tạm thời (smoke test đọc + E2E ghi) → quét image, gắn tag → đẩy GHCR
//                        → bot commit B "deploy: image M" sửa newTag trong deploy/k8s/overlays/{staging,prod}
//   develop (commit B) : không test lại (B chỉ đổi tag) → chờ Argo CD đưa rms-staging lên B, Healthy → promote B lên main
//   main (commit B)    : chờ Argo CD đưa rms-prod lên B (Argo Rollouts blue-green, AnalysisTemplate kiểm trước khi chuyển
//                        traffic) → kiểm domain thật → báo Telegram
//   commit chỉ đổi tài liệu (docs/*.md, *.docx, *.pptx, ảnh…; xem stage Phân loại commit): không test, không build image.
//                        feature: merge thẳng vào develop. develop: chờ rms-staging Healthy rồi promote lên main.
//                        main: Argo CD không có gì để triển khai, chỉ xác nhận production vẫn Healthy.
//   quay lui           : git revert commit B trên nhánh feature → commit chỉ đổi tag về image cũ, được xử lý như B: không
//                        test, không build (nhánh feature chỉ kiểm image còn trên GHCR), Argo CD đưa staging rồi production về
//                        image cũ bằng blue-green. Commit code sau đó mới build bản mới.
//   [a | b]            : chạy song song (các bước độc lập, không bỏ bước nào)
// main không test lại: --ff-only nên commit trên main trùng SHA với commit develop vừa qua đủ test, staging và Trivy.
// Maven và Node chạy trong container tạm, dùng chung volume với Jenkins để đường dẫn workspace khớp.
// Jenkins và mọi container của pipeline chạy trên Docker rootless của user ci (máy 2): root trong container chỉ là user
// thường ci trên máy, không chiếm được máy dù lấy được docker.sock. DOCKER_SOCK_HOST (đặt trong
// deploy/jenkins/docker-compose.override.yml) là đường dẫn socket đó trên máy, dùng khi mount socket cho container con.
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
        // kubeconfig của ServiceAccount argocd/jenkins-ci (chỉ đọc Application): file /opt/jenkins/secrets/kubeconfig-ci
        // trên máy 2, mount vào /run/secrets. Dùng để chờ Argo CD triển khai xong (scripts/release/argocd-wait.sh).
        KUBECONFIG = '/run/secrets/kubeconfig-ci'
        // Repo GitHub (dùng cho fetch/push bằng token trong các bước chạy ngoài workspace)
        REPO = 'github.com/Anpham120/restaurant-management-system.git'
        // Thư mục staging tạm thời trên máy 2 (clone nhánh develop, tự tạo lần đầu), thuộc user ci. Không chứa secret thật.
        STAGING_DIR = '/home/ci/rms-staging'
        // Bản sao lưu database trên máy 2: máy 1 sao lưu hằng ngày rồi chép bản mới nhất sang (scripts/backup/backup-db.sh).
        // Staging tạm thời nạp bản này để thử migration và API trên dữ liệu thật.
        BACKUP_DIR = '/home/coder/rms-backups'
        // Địa chỉ công khai (qua nginx + HTTPS) để kiểm production sau deploy
        PUBLIC_URL = 'https://rms.atlasplatform12.tech'
        // Chat nhận thông báo pipeline (cùng bot với Alertmanager). Chat id không phải bí mật, token nằm trong credential.
        TELEGRAM_CHAT_ID = '6822487436'
    }

    stages {
        stage('Phân loại commit') {
            steps {
                script {
                    // Commit chỉ đổi dòng newTag trong 2 overlay, mọi tag mới là commit có trong lịch sử repo: commit "deploy: image ..."
                    // của bot (scripts/release/bump-image.sh), hoặc người revert commit đó để quay lui về image cũ. Không test, không build:
                    // image đó đã qua đủ kiểm tra lúc phát hành; nhánh feature kiểm image còn trên GHCR (stage Kiểm image phát hành).
                    env.CHI_DOI_TAG = sh(returnStdout: true, script: '''
                        ovl='deploy/k8s/overlays/(staging|prod)/kustomization.yaml'
                        doi=$(git diff --name-only HEAD^ HEAD)
                        tags=$(git diff HEAD^ HEAD -- deploy/k8s/overlays | sed -nE 's/^\\+ *newTag: ([0-9a-f]{40}).*/\\1/p' | sort -u)
                        co_that=true
                        for t in $tags; do git cat-file -e "$t^{commit}" 2>/dev/null || co_that=false; done
                        if [ -n "$doi" ] && [ -n "$tags" ] && [ "$co_that" = true ] \\
                           && [ -z "$(printf '%s\\n' "$doi" | grep -vxE "$ovl")" ] \\
                           && [ -z "$(git diff HEAD^ HEAD -- deploy/k8s/overlays | grep -E '^[-+]' | grep -vE '^(---|\\+\\+\\+) ' | grep -v 'newTag: ')" ]
                        then echo true; else echo false; fi
                    ''').trim()
                    // Commit merge feature -> develop (của rms-ci) có nội dung (git tree) giống hệt đầu nhánh feature: nhánh đó vừa
                    // qua đủ kiểm migration + test + quét mã thì mới được merge, test lại cùng nội dung là thừa (~2 phút).
                    // develop có thay đổi khác xen vào (tree khác) thì vẫn test đủ.
                    env.DA_TEST = sh(returnStdout: true, script: '''
                        if [ "$BRANCH_NAME" = develop ] && [ "$(git log -1 --format=%an)" = rms-ci ] \\
                           && git rev-parse -q --verify HEAD^2 >/dev/null \\
                           && [ "$(git rev-parse 'HEAD^{tree}')" = "$(git rev-parse 'HEAD^2^{tree}')" ]
                        then echo true; else echo false; fi
                    ''').trim()
                    // Chỉ đổi tài liệu kể từ lần quyết định deploy gần nhất (commit cuối sửa overlay staging: bot ghi tag, hoặc người
                    // revert để quay lui). So với commit đó chứ không chỉ commit cuối, để code đã vào develop mà chưa build
                    // (bump-image bỏ qua vì develop có commit mới hơn) không bị bỏ sót; sau khi quay lui, commit tài liệu không
                    // build lại code mới nhất đè lên bản đã quay lui. Được miễn: file trong docs/ có đuôi tài liệu, file .md ngoài src/. Không miễn docs/*.sql
                    // (schema gốc mà Kustomize và Docker Compose nạp), docs/*.conf, script. Overlay chỉ được khác ở dòng newTag
                    // (commit của bot). Không xác định được commit gốc thì chạy đủ.
                    env.CHI_TAI_LIEU = env.CHI_DOI_TAG == 'true' ? 'false' : sh(returnStdout: true, script: '''
                        goc=$(git log -1 --format=%H -- deploy/k8s/overlays/staging/kustomization.yaml)
                        mien='docs/.*\\.(md|docx|pptx|pdf|png|jpe?g|svg|drawio|dbml|txt)|.*\\.md|deploy/k8s/overlays/(staging|prod)/kustomization.yaml'
                        if [ -n "$goc" ] && git cat-file -e "$goc^{commit}" 2>/dev/null \\
                           && doi=$(git diff --name-only "$goc" HEAD) \\
                           && printf '%s\\n' "$doi" | grep -qE '^docs/|\\.md$' \\
                           && [ -z "$(printf '%s\\n' "$doi" | grep -vxE "$mien")" ] \\
                           && [ -z "$(printf '%s\\n' "$doi" | grep -E '^src/')" ] \\
                           && [ -z "$(git diff "$goc" HEAD -- deploy/k8s/overlays | grep -E '^[-+]' | grep -vE '^(---|\\+\\+\\+) ' | grep -v 'newTag: ')" ]
                        then echo true; else echo false; fi
                    ''').trim()
                    echo(env.CHI_DOI_TAG == 'true' ? 'Commit đổi tag image: bỏ qua test, chỉ chờ Argo CD'
                         : env.CHI_TAI_LIEU == 'true' ? 'Chỉ đổi tài liệu: bỏ qua test và build image'
                         : env.DA_TEST == 'true' ? 'Nội dung giống hệt nhánh feature đã qua test: bỏ qua test lặp, vẫn chạy staging'
                         : 'Commit mã nguồn: chạy đủ kiểm tra')
                }
            }
        }

        stage('Kiểm migration') {
            when { allOf { not { branch 'main' }; environment name: 'CHI_DOI_TAG', value: 'false'; environment name: 'DA_TEST', value: 'false'; environment name: 'CHI_TAI_LIEU', value: 'false' } }
            steps {
                // Migration phải tương thích ngược (expand/contract) để quay lui image không cần khôi phục database
                sh 'scripts/ci/check-migrations.sh'
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Kiểm tra song song') {
            when { allOf { not { branch 'main' }; environment name: 'CHI_DOI_TAG', value: 'false'; environment name: 'DA_TEST', value: 'false'; environment name: 'CHI_TAI_LIEU', value: 'false' } }
            // Ba việc độc lập chạy cùng lúc: tổng thời gian = việc lâu nhất (test backend) thay vì cộng dồn. Không bỏ bước nào;
            // một nhánh lỗi thì cả stage lỗi như trước, các nhánh còn lại vẫn chạy hết để log đủ thông tin. Kiểm migration vẫn chạy
            // trước (vài giây, lỗi thì dừng sớm không tốn thời gian test). RAM: Maven tối đa 3 GB + Node 2 GB, máy còn trống ~4 GB.
            parallel {
                stage('Test backend') {
                    steps {
                        sh '''
                            # Volume cache Maven mới tạo thuộc root, đổi quyền để user Jenkins ghi được
                            docker run --rm -v rms-m2-cache:/m2 alpine:3.20 chown "$(id -u):$(id -g)" /m2
                            # Giới hạn tài nguyên: máy 2 (4 CPU, 8 GB) chạy chung k3s (Argo CD, staging) và giám sát. Tối đa 2 CPU,
                            # 2,5 GB; cpu-shares 512: khi tranh CPU, các dịch vụ đang chạy được ưu tiên gấp đôi.
                            docker run --rm --network host --cpus=2 --cpu-shares=512 --memory=2560m \
                              --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE" \
                              --user "$(id -u):$(id -g)" --group-add "$(stat -c %g /var/run/docker.sock)" \
                              -e HOME=/tmp -v rms-m2-cache:/tmp/.m2 \
                              -e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$DOCKER_SOCK_HOST" \
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
                            docker run --rm --cpus=2 --cpu-shares=512 --memory=1536m --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE/frontend" \
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
                        sh 'scripts/ci/security-scan.sh fs'
                    }
                    post { failure { script { ghiBuocLoi() } } }
                }
            }
        }

        stage('Kiểm image phát hành') {
            when { allOf { branch pattern: 'feature/.*', comparator: 'REGEXP'; environment name: 'CHI_DOI_TAG', value: 'true' } }
            steps {
                // Commit đổi tag (thường là revert để quay lui): image app, web của tag mới phải còn trên GHCR, nếu không
                // Argo CD sẽ kéo image thất bại. Kiểm ngay ở đây thay vì đợi staging báo lỗi sau vài phút.
                withCredentials([string(credentialsId: 'ghcr-token', variable: 'GHCR_TOKEN')]) {
                    sh 'scripts/release/check-release-images.sh'
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Merge feature vào develop') {
            when { branch pattern: 'feature/.*', comparator: 'REGEXP' }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/release/auto-merge.sh develop'
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Staging smoke test') {
            when { allOf { branch 'develop'; environment name: 'CHI_DOI_TAG', value: 'false'; environment name: 'CHI_TAI_LIEU', value: 'false' } }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    // Dựng bản develop ở môi trường tách biệt trên bản sao lưu production mới nhất (chỉ đọc), thử các
                    // chức năng chính và quét đọc mọi module rồi hủy. Lỗi thì không promote.
                    // Container docker:27-cli (có sẵn docker compose, git) mount thư mục staging đúng đường dẫn trên host; lệnh
                    // trong '...' do shell của container chạy. trap: luôn trả quyền thư mục về root của container = user ci
                    // trên máy, kể cả khi lỗi.
                    sh '''
                        docker run --rm --network host \
                          -v "$DOCKER_SOCK_HOST:/var/run/docker.sock" \
                          -v "$STAGING_DIR:$STAGING_DIR" -w "$STAGING_DIR" -v "$BACKUP_DIR:$BACKUP_DIR:ro" \
                          -e GH_TOKEN -e BACKUP_DIR -e GIT_COMMIT -e REPO \
                          docker:27-cli sh -euc '
                            trap "chown -R 0:0 ." EXIT
                            apk add -q --no-cache bash curl jq
                            git config --global --add safe.directory "$PWD"
                            [ -d .git ] || git init -q
                            git fetch -q "https://x-access-token:$GH_TOKEN@$REPO" develop
                            git reset -q --hard "$GIT_COMMIT"
                            scripts/ci/staging-test.sh
                          '
                    '''
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Quét bảo mật image') {
            when { allOf { branch 'develop'; environment name: 'CHI_DOI_TAG', value: 'false'; environment name: 'CHI_TAI_LIEU', value: 'false' } }
            steps {
                // Image vừa build ở staging. Qua quét thì gắn tag phát hành theo commit: production chạy đúng image này.
                sh 'scripts/ci/security-scan.sh image rms-staging-app rms-staging-web'
                sh 'scripts/release/tag-release.sh "$GIT_COMMIT"'
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Phát hành') {
            when { allOf { branch 'develop'; environment name: 'CHI_DOI_TAG', value: 'false'; environment name: 'CHI_TAI_LIEU', value: 'false' } }
            steps {
                // Kubernetes kéo image từ GHCR nên bước này bắt buộc: thiếu credential ghcr-token hoặc registry lỗi thì dừng.
                withCredentials([string(credentialsId: 'ghcr-token', variable: 'GHCR_TOKEN')]) {
                    sh 'scripts/release/push-release.sh "$GIT_COMMIT"'
                    // Dọn image cũ trên GHCR: giữ 20 bản mới nhất (đủ để quay lui bằng git revert) và các tag đang chạy ở
                    // staging/production. Token đi qua biến môi trường (-e GHCR_TOKEN), không nằm trên dòng lệnh.
                    // Lỗi dọn không làm hỏng phát hành.
                    sh '''
                        PROTECT="$GIT_COMMIT $(sed -nE 's/^ *newTag: ([0-9a-f]{7,40}).*/\\1/p' deploy/k8s/overlays/*/kustomization.yaml | sort -u | tr '\\n' ' ')"
                        docker run --rm -i -e GHCR_TOKEN -e KEEP=20 -e PROTECT="$PROTECT" python:3.12-alpine \
                          python - < scripts/ops/ghcr-cleanup.py || echo "[ghcr-cleanup] lỗi, bỏ qua"
                    '''
                }
                // Ghi tag vào Git (commit "deploy: image ..."). Commit đó chạy lại pipeline ở nhánh này, nhưng chỉ chờ Argo CD.
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/release/bump-image.sh "$GIT_COMMIT"'
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Chờ Argo CD: staging') {
            when { allOf { branch 'develop'; anyOf { environment name: 'CHI_DOI_TAG', value: 'true'; environment name: 'CHI_TAI_LIEU', value: 'true' } } }
            steps {
                // rms-staging (máy 2) theo nhánh develop: blue-green + AnalysisTemplate, lỗi thì không promote.
                // Commit chỉ đổi tài liệu: Argo CD không có gì mới để triển khai, bước này xác nhận staging vẫn Healthy.
                sh 'scripts/release/argocd-wait.sh rms-staging "$GIT_COMMIT" 900'
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Promote develop lên main') {
            when { allOf { branch 'develop'; anyOf { environment name: 'CHI_DOI_TAG', value: 'true'; environment name: 'CHI_TAI_LIEU', value: 'true' } } }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh 'scripts/release/auto-merge.sh main'
                }
            }
            post { failure { script { ghiBuocLoi() } } }
        }

        stage('Chờ Argo CD: production') {
            when { branch 'main' }
            steps {
                // rms-prod (máy 1) theo nhánh main. Argo Rollouts dựng bản mới cạnh bản cũ, AnalysisTemplate gọi thử qua
                // Service preview, đạt mới chuyển traffic; không đạt thì giữ bản cũ và báo Degraded.
                sh 'scripts/release/argocd-wait.sh rms-prod "$GIT_COMMIT" 1200'
                // Kiểm như người dùng: domain thật qua nginx + HTTPS
                sh '''
                    for i in $(seq 1 12); do
                      if curl -fsS --max-time 10 "$PUBLIC_URL/actuator/health" | grep -q '"UP"'; then
                        echo "[prod] $PUBLIC_URL health UP"; exit 0
                      fi
                      sleep 5
                    done
                    echo "[prod] $PUBLIC_URL không trả UP"; exit 1
                '''
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
            # Commit "deploy: image ..." của bot: nội dung thay đổi nằm ở commit cha (commit merge của nhánh tính năng)
            R=HEAD
            case "$(git log -1 --format=%s)" in "deploy: "*) R=HEAD^ ;; esac
            if git rev-parse -q --verify "$R^2" >/dev/null; then
              nhanh=$(git log -1 --format=%s "$R" | sed -nE 's/^Merge (.+) vào .*/\\1/p')
              thaydoi=$(git log -1 --format=%s "$R^2" | sed -E 's/^ +//')
              tacgia=$(git log -1 --format=%an "$R^2")
              them=$(( $(git rev-list --count "$R^1..$R^2") - 1 ))
              if [ "$them" -gt 0 ]; then thaydoi="$thaydoi (+$them commit khác)"; fi
            else
              nhanh=$BRANCH_NAME
              thaydoi=$(git log -1 --format=%s "$R" | sed -E 's/^ +//')
              tacgia=$(git log -1 --format=%an "$R")
            fi
            image=$(sed -nE 's/^ *newTag: ([0-9a-f]{7}).*/\\1/p' deploy/k8s/overlays/prod/kustomization.yaml | head -1)
            echo "Thay đổi: $thaydoi"
            echo "Nhánh: ${nhanh:-$BRANCH_NAME} · $tacgia"
            echo "Commit: $(git rev-parse --short HEAD)"
            echo "Đang phục vụ: Kubernetes rms-prod, image ${image:-không rõ}"
        ''').trim()
        long giay = (System.currentTimeMillis() - currentBuild.startTimeInMillis).intdiv(1000)
        def thoiGian = giay >= 60 ? "${giay.intdiv(60)} phút ${giay % 60} giây" : "${giay} giây"
        def build = "${env.BRANCH_NAME} #${env.BUILD_NUMBER} · ${thoiGian}"
        def text
        if (ok) {
            def tieuDe = env.CHI_TAI_LIEU == 'true' ? '✅ TÀI LIỆU ĐÃ LÊN MAIN (production không đổi image)' : '✅ ĐÃ LÊN PRODUCTION'
            text = "${tieuDe}\n${info}\nBuild: ${build}\nWeb: ${env.PUBLIC_URL}"
        } else {
            def hauQua = env.BRANCH_NAME == 'main' ? 'Argo Rollouts giữ bản cũ nếu bản mới không qua kiểm tra. Xem Argo CD và log.'
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
