# Script

Chia theo việc. Jenkinsfile, cron và systemd trên máy chủ gọi đúng đường dẫn `scripts/<nhóm>/<tên>` (bản clone
`/home/coder/rms-deploy` theo nhánh `main`); đổi tên hay chuyển chỗ phải sửa cả nơi gọi (docs/devops/PIPELINE.md §8).
Mô tả đủ ở đầu mỗi file.

| Thư mục | Script | Chạy bởi |
| --- | --- | --- |
| `ci/` | `check-migrations.sh` (luật migration tương thích ngược), `security-scan.sh` (Trivy), `staging-test.sh` (staging tạm + smoke + E2E), `e2e-flow.sh` (vòng nghiệp vụ qua REST) | Jenkins |
| `release/` | `auto-merge.sh` (feature → develop → main), `tag-release.sh`, `push-release.sh` (đẩy image lên GHCR), `bump-image.sh` (ghi tag vào overlay), `check-release-images.sh`, `argocd-wait.sh` (chờ Argo CD triển khai xong) | Jenkins |
| `backup/` | `backup-db.sh` (pg_dump), `offsite-backup.sh` (mã hóa, gửi ra ngoài), `backup-sealed-key.sh` (khóa Sealed Secrets), `backup-metric.sh` (ghi số liệu cho Prometheus), `restore-db.sh` (khôi phục, XÓA dữ liệu hiện tại) | cron máy 1, người vận hành |
| `ops/` | `argocd-webhook-metric.sh` (đếm webhook bị từ chối), `ghcr-cleanup.py` (giữ 20 image gần nhất và tag đang chạy), `set-github-oauth.sh` (OAuth Grafana/Jenkins, nhập secret ẩn) | cron máy 2, Jenkins, người vận hành |
| `demo/` | `kiem-tra.sh` (kiểm tra trước buổi demo, máy 1), `doi-chu.sh` (thay đổi nhìn thấy được đi tới production, `--tra-lai` để đưa về cũ), `gay-loi.sh` (commit sai cố ý để pipeline chặn), `don-dep.sh` (xóa nhánh demo), `k8s.sh` (tự phục hồi pod, GitOps tự sửa lệch, NetworkPolicy, SealedSecret, PostgreSQL; máy 1), `test-canh-bao.sh` (promtool test cả 23 luật cảnh báo), `canh-bao-that.sh` (cảnh báo thật NodeExporterDown hoặc cảnh báo thử về Telegram; máy 2) | người trình bày |

Script cho cụm Kubernetes (seal secret, webhook Argo CD) nằm ở `deploy/k8s/scripts/`.
