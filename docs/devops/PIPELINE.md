# Quy trình triển khai và vận hành (GitOps, Kubernetes)

Sổ tay vận hành của hệ thống đang chạy từ 20/11/2026. Hướng dẫn cài từng bước cho người mới (kèm giải thích từng lệnh)
nằm ở tài liệu "Hướng dẫn triển khai Kubernetes — Atlas Restaurant"; file này là bản tra cứu nhanh, luôn khớp với repo.

## 1. Kiến trúc

| | Máy 1 (163.61.182.201, WireGuard 10.10.0.1) | Máy 2 (23.132.252.79, WireGuard 10.10.0.2) |
|---|---|---|
| Vai trò | Chỉ production | Công cụ |
| Chạy | nginx (edge), cụm k3s: Traefik (2 bản), app + web (2 bản, Argo Rollouts blue-green), PostgreSQL CloudNativePG (primary + replica), Sealed Secrets, Alloy | Jenkins (Docker rootless, user `ci`), cụm k3s quản trị: Argo CD + staging `rms-staging`; giám sát (Prometheus, Alertmanager, Grafana, Loki, blackbox, node-exporter); SeaweedFS (S3 cho sao lưu WAL) |
| Internet vào | 22, 80, 443 | 22, 80, 443 |

Hai máy nói chuyện qua WireGuard: Argo CD → API k3s máy 1 (6443), Prometheus → cổng số liệu nginx máy 1 (9181 app,
9182 PostgreSQL, 9200 node-exporter), Alloy máy 1 → Loki (3101), PostgreSQL máy 1 → SeaweedFS (9000). Mọi cổng đó chỉ mở
cho địa chỉ WireGuard của máy kia.

Nguyên tắc: không có bước làm tay; image build một lần, staging và production kéo đúng image đó; Jenkins không có quyền
và không giữ secret của production; muốn đổi production chỉ có một đường là sửa Git.

## 2. Luồng từ commit tới production (~6 phút)

| Nhánh / loại commit | Pipeline (`Jenkinsfile`) | Khi đạt |
|---|---|---|
| `feature/*` | kiểm migration → [test backend (Testcontainers) \| test frontend \| Trivy mã nguồn] | tự merge `--no-ff` vào `develop` |
| `develop`, commit mã | (bỏ test nếu tree trùng nhánh feature vừa test) → staging tạm trên bản sao dữ liệu thật (smoke 32 API + E2E ghi) → Trivy image → đẩy GHCR, dọn GHCR → bot `rms-ci` ghi tag vào `deploy/k8s/overlays/{staging,prod}` | commit `deploy: image <sha>` |
| `develop`, commit tag | chờ Argo CD đưa `rms-staging` lên commit đó, Healthy | promote fast-forward lên `main` |
| `main` | chờ Argo CD đưa `rms-prod` lên (blue-green + AnalysisTemplate) → kiểm domain thật → Telegram | production chạy bản mới |
| chỉ đổi tài liệu | bỏ test và build; develop chỉ chờ staging Healthy | lên `main`, production không đổi image |
| quay lui (`git revert` commit tag) | như commit tag; nhánh feature kiểm image còn trên GHCR | production về image cũ |

Stage đầu `Phân loại commit` đặt 3 biến: `CHI_DOI_TAG` (chỉ đổi dòng `newTag` về một commit có thật trong lịch sử),
`DA_TEST` (merge có tree trùng nhánh feature), `CHI_TAI_LIEU` (từ lần đổi tag gần nhất chỉ có file tài liệu thay đổi;
không miễn `docs/*.sql`, `docs/*.conf`). Lỗi ở bất kỳ bước nào: dừng, không promote, Telegram kèm link log.

Webhook: GitHub gửi cho cả Jenkins (`/github-webhook/`) và Argo CD (`/api/webhook`), có chữ ký HMAC. Dự phòng: Jenkins quét
multibranch định kỳ, Argo CD tự quét Git mỗi 60 giây (+ tối đa 60 giây ngẫu nhiên). Không thêm `pollSCM`.

## 3. Thiết lập

### 3.1 Jenkins (máy 2)
Docker rootless của user `ci`, cấu hình bằng JCasC: `deploy/jenkins/`, cài đặt và đường dẫn ở `docs/devops/JENKINS-ROOTLESS.md`.
Đăng nhập bằng GitHub (org `atlas-platform12`): `rms-admins` toàn quyền, `rms-developers` chạy/hủy build, `rms-viewers` xem.

| Credential | Nội dung | Dùng cho |
|---|---|---|
| `github-token` | token classic, scope `repo` | merge, promote, bot ghi tag |
| `github-branch-source` | username + cùng token | multibranch quét repo |
| `ghcr-token` | token classic, `write:packages` + `delete:packages` | đẩy image, dọn image cũ (`scripts/ops/ghcr-cleanup.py`, giữ 20 bản + bản đang chạy) |
| `telegram-bot-token` | token bot | báo kết quả build |
| `github-webhook-secret` | chuỗi HMAC | xác thực webhook |
| `/run/secrets/kubeconfig-ci` | token ServiceAccount `argocd/jenkins-ci` (chỉ get/list/watch Application) | `scripts/release/argocd-wait.sh` |

### 3.2 Argo CD (máy 2)
Application trong `deploy/k8s/argocd/`, áp bằng `kubectl apply` trên máy 2 (bản chép ở `~/k8s/argocd/`):
- `rms-staging` (nhánh `develop` → cụm máy 2), `rms-prod` (nhánh `main` → cụm máy 1): project `rms`, chỉ 2 namespace đó.
- `may1-traefik` (`deploy/k8s/platform/may1/`): project `platform`, chỉ được HelmChartConfig trong `kube-system` máy 1,
  `prune: false`.

Secret webhook: `ssh -t coder@10.10.0.2 'bash ~/k8s/set-argocd-webhook-secret.sh'` **trong terminal riêng** (secret in ra
màn hình, không chạy qua công cụ ghi lại hội thoại), rồi dán vào webhook Argo CD trên GitHub. Chạy lại script là secret cũ
hết hiệu lực: phải sửa GitHub ngay (luật `ArgoWebhookRejected` sẽ báo nếu quên).

### 3.3 Secret của ứng dụng
SealedSecret trong `deploy/k8s/overlays/prod/sealed-*.yaml`, tạo bằng `deploy/k8s/scripts/seal-*.sh` (nhập ẩn, mã hóa bằng
chứng chỉ công khai `deploy/k8s/certs/prod.pem`). Chỉ controller trong cụm máy 1 giải mã được; khóa riêng được sao lưu
hằng tuần (mục 6).

## 4. Reverse proxy: nginx và Traefik

| | nginx (máy 1, ngoài cụm, sửa tay) | Traefik (trong k3s, GitOps) |
|---|---|---|
| Nhận từ | internet 80/443 | chỉ nginx, ClusterIP `10.43.0.80:80` |
| Việc | HTTPS (certbot), http→https, HTTP/2, HSTS, `limit_req` đăng nhập 5/phút/IP, keepalive tới Traefik, cổng số liệu WireGuard | định tuyến theo Ingress, header bảo mật (CSP, nosniff, X-Frame-Options, Referrer-Policy, Permissions-Policy), nén API, access log JSON → Loki |
| File | `docs/devops/nginx/rms.atlasplatform12.tech.conf`, `rms-login-ratelimit.conf`, `metrics-wireguard.conf` | `deploy/k8s/overlays/prod/{ingress,middlewares}.yaml`, `deploy/k8s/platform/may1/traefik-config.yaml` |

Áp file nginx (máy 1): sao lưu bản trên máy, `sudo cp docs/devops/nginx/<file> /etc/nginx/sites-available/`,
`sudo nginx -t && sudo systemctl reload nginx`. Bản trong repo phải luôn trùng bản trên máy. Không thêm định tuyến ứng
dụng vào nginx: mọi đường dẫn đi vào Traefik.

Chống dò mật khẩu 2 lớp: nginx theo IP (trên), app theo tên đăng nhập (`LoginThrottleService`: sai 5 lần trong 15 phút thì
khóa 15 phút, bộ đếm ở bảng `login_throttles`, dùng chung mọi bản app).

## 5. Giám sát và cảnh báo (máy 2)

Chạy từ bản clone nhánh `main`: `/home/coder/rms-deploy/deploy/monitoring` (`docker compose up -d`), systemd timer
`rms-monitoring-sync.timer` kéo `main` mỗi 5 phút và gửi SIGHUP cho Prometheus, Alertmanager. Máy 1 chỉ chạy node-exporter
(cùng timer, `SERVICES=node-exporter`). Grafana: `https://grafana.atlasplatform12.tech` (đăng nhập GitHub), dashboard
lưu dạng mã trong `deploy/monitoring/grafana/provisioning/`, log trong Loki 7 ngày.

23 luật trong `deploy/monitoring/prometheus/alerts.yml`, gửi Telegram (Watchdog gửi healthchecks.io làm nhịp tim):

| Nhóm | Luật |
|---|---|
| rms | `AppDown`, `HighErrorRate`, `JvmHeapHigh`, `DatabaseUnreachable` |
| site | `SiteDown`, `CertExpiringSoon` (rms + argocd, grafana, jenkins), `ProbeExporterDown` |
| backup | `BackupStale`, `OffsiteBackupStale`, `WalArchivingFailing`, `PostgresExporterDown`, `SealedKeyBackupStale` |
| gitops | `ArgoCDDown`, `ArgoAppOutOfSync`, `ArgoAppUnhealthy`, `ArgoSyncFailed`, `ArgoWebhookRejected`, `ToolDown` |
| host | `NodeExporterDown`, `DiskAlmostFull`, `HighMemory`, `HighCpu` |
| meta | `Watchdog` |

Inhibit: `AppDown` chặn `SiteDown`; `DatabaseUnreachable` chặn `SiteDown`, `HighErrorRate`. Số liệu Argo CD lấy qua Service
cố định `10.43.0.82:8082` (`deploy/k8s/platform/argocd-metrics-service.yaml`). Thử cảnh báo giả:
`curl -XPOST 127.0.0.1:9093/api/v2/alerts -H 'Content-Type: application/json' -d '[{"labels":{"alertname":"Thu","severity":"warning"},"annotations":{"summary":"Thử"}}]'`.

## 6. Sao lưu và khôi phục

| Lớp | Khi nào | Ở đâu |
|---|---|---|
| Replica PostgreSQL | liên tục | pod `rms-db` thứ 2 (máy 1) |
| WAL (barman-cloud) | liên tục, chậm nhất 60 giây (`archive_timeout`) | SeaweedFS máy 2, bucket `rms-pg-backups`, 14 ngày |
| Bản nền | 02:00 giờ VN (ScheduledBackup `rms-db-daily`) | SeaweedFS máy 2 |
| `pg_dump` | 02:30 (`scripts/backup/backup-db.sh daily`) | máy 1 `/home/coder/rms-backups` (7 bản), chép máy 2 cho staging |
| Offsite | 02:35 (`scripts/backup/offsite-backup.sh`) | `*.dump.enc` (AES-256) qua bot Telegram |
| Khóa Sealed Secrets | Chủ nhật 02:40 (`scripts/backup/backup-sealed-key.sh`) | máy 1 (8 bản), máy 2, Telegram; mã hóa AES-256 |

Khóa mã hóa offsite: `/home/coder/rms-backups/.offsite.key` (máy 1, quyền 600), có bản trong trình quản lý mật khẩu.

Khôi phục:
- **Từ `pg_dump`:** `scripts/backup/restore-db.sh <file.dump>` (máy 1). Sao lưu bản hiện tại (`pre-restore-*`), thay toàn bộ dữ liệu
  trong một transaction trên pod primary, khởi động lại app theo blue-green. Đã chạy thử trên `rms-staging`
  (`NAMESPACE=rms-staging`, 21/11/2026).
- **Về thời điểm bất kỳ trong 14 ngày (PITR):** tạo Cluster CloudNativePG mới với `bootstrap.recovery` từ ObjectStore
  `rms-backup-store` và `recoveryTarget.targetTime`, kiểm dữ liệu rồi trỏ app sang (các bước ở hướng dẫn K8s, mục 8.3;
  đã thử 20/11/2026).
- **Giải mã bản offsite:** `openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:<khóa> -in <file>.enc -out <file>`.
- **Khóa Sealed Secrets lên cụm mới:** giải mã như trên rồi `| kubectl apply -f -`, sau đó
  `kubectl -n kube-system rollout restart deploy/sealed-secrets-controller`.

## 7. Quay lui

- **Tự động:** bản mới không qua AnalysisTemplate (Job gọi `/actuator/health/readiness` qua Service preview) thì Argo
  Rollouts hủy, Service chính vẫn trỏ bản cũ.
- **Theo GitOps:** tạo nhánh `feature/*`, `git revert <commit "deploy: image …">`, push. Không test, không build; Argo CD
  đưa staging rồi production về image cũ bằng blue-green. Commit tài liệu sau đó không build lại bản lỗi.
- **Khẩn cấp:** tắt tự đồng bộ `rms-prod` trên Argo CD, `kubectl argo rollouts undo rms-app -n rms-prod`, xong thì bật lại.
- **Database:** quay lui image không hoàn tác migration, nên migration bắt buộc tương thích ngược (`scripts/ci/check-migrations.sh`).

## 8. Việc định kỳ trên máy

| Máy | Lịch | Việc |
|---|---|---|
| 1 | cron 19:30 UTC | `scripts/backup/backup-db.sh daily` (`COPY_TO=coder@10.10.0.2:/home/coder/rms-backups`) |
| 1 | cron 19:35 UTC | `scripts/backup/offsite-backup.sh` |
| 1 | cron 19:40 UTC thứ Bảy | `scripts/backup/backup-sealed-key.sh` (`COPY_TO` máy 2) |
| 1, 2 | systemd `rms-monitoring-sync.timer`, 5 phút | kéo `main`, nạp lại cấu hình giám sát (`deploy/monitoring/sync/`) |
| 2 | cron mỗi 5 phút | `scripts/ops/argocd-webhook-metric.sh` (đếm webhook bị từ chối) |
| 1, 2 | `certbot.timer` | gia hạn chứng chỉ Let's Encrypt |

Script chạy từ bản clone `main` (`/home/coder/rms-deploy`), nên đổi script chỉ cần merge.

## 9. Kiểm tra nhanh

```bash
# máy 1
curl -s https://rms.atlasplatform12.tech/actuator/health
kubectl -n rms-prod get pods
kubectl argo rollouts get rollout rms-app -n rms-prod
kubectl -n rms-prod get cluster.postgresql.cnpg.io rms-db      # primary, replica, đẩy WAL
kubectl -n kube-system get pods -l app.kubernetes.io/name=traefik
# máy 2
KUBECONFIG=~/.kube/config kubectl -n argocd get applications
curl -s 127.0.0.1:9090/api/v1/alerts | head -c 300           # cảnh báo đang bật
```
