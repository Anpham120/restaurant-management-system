# Script

Giữ nguyên tên và vị trí: Jenkinsfile và cron trên máy chủ gọi đúng đường dẫn `scripts/<tên>`. Mô tả đủ ở đầu mỗi file.

| Nhóm | Script | Chạy bởi |
| --- | --- | --- |
| Kiểm tra trong pipeline | `check-migrations.sh` (luật migration tương thích ngược), `security-scan.sh` (Trivy), `staging-test.sh` (staging tạm + smoke + E2E), `e2e-flow.sh` (vòng nghiệp vụ qua REST) | Jenkins |
| Phát hành, GitOps | `auto-merge.sh` (feature → develop → main), `tag-release.sh`, `push-release.sh` (đẩy image lên GHCR), `bump-image.sh` (ghi tag vào overlay), `check-release-images.sh`, `argocd-wait.sh` (chờ Argo CD triển khai xong) | Jenkins |
| Sao lưu, khôi phục | `backup-db.sh` (pg_dump), `offsite-backup.sh` (mã hóa, gửi ra ngoài), `backup-sealed-key.sh` (khóa Sealed Secrets), `restore-db.sh` (khôi phục, XÓA dữ liệu hiện tại) | cron máy chủ, người vận hành |
| Giám sát | `backup-metric.sh`, `argocd-webhook-metric.sh` (ghi số liệu cho node-exporter) | cron máy 2 |
| Dọn dẹp | `ghcr-cleanup.py` (giữ 20 bản image gần nhất và tag đang chạy) | cron máy 2 |
| Cấu hình một lần | `set-github-oauth.sh` (OAuth cho Grafana/Jenkins, nhập secret ẩn) | người vận hành, terminal riêng |

Script cho cụm Kubernetes (seal secret, webhook Argo CD) nằm ở `deploy/k8s/scripts/`.
