# Quy trình CI/CD (SCM + Jenkins + Docker + Nginx + Prometheus + Grafana)

Không có bước duyệt tay. Mọi thứ đi từ commit đến production đều do Jenkins chạy.

## 1. Mô hình nhánh

```
feature/xxx ──(CI pass, tự merge --no-ff)──► develop ──(CI pass, tự promote ff-only)──► main ──► DEPLOY
```

| Nhánh | Tạo từ | Pipeline chạy | Kết quả khi CI pass |
|---|---|---|---|
| `feature/*` | `develop` | Test backend + frontend | Tự merge vào `develop` |
| `develop` | — | Test backend + frontend | Tự promote lên `main` (fast-forward) |
| `main` | — | Test backend + frontend, deploy | Deploy production, tự quay lui nếu lỗi |

Quy tắc phát triển:
1. `git switch develop && git switch -c feature/ten-tinh-nang`
2. Làm việc, commit, `git push -u origin feature/ten-tinh-nang`
3. Jenkins test. Nếu pass, nhánh tự merge vào `develop`. Nếu fail, nhánh không được merge.
4. `develop` test lại. Nếu pass, tự đẩy lên `main` và `main` tự deploy.

Lưu ý: không commit trực tiếp lên `main` hoặc `develop`. Nếu làm vậy, lần promote sau sẽ không fast-forward được và pipeline báo đỏ.

## 2. Pipeline (`Jenkinsfile`)

| Stage | Chạy ở | Làm gì |
|---|---|---|
| Test backend | mọi nhánh | `mvn verify` (unit + `*IT` dùng Testcontainers) trong container `maven:3.9-eclipse-temurin-21` |
| Test frontend | mọi nhánh | `npm ci`, `typecheck`, `build` trong container `node:22-alpine` |
| Merge feature vào develop | `feature/*` | `scripts/auto-merge.sh develop` |
| Promote develop lên main | `develop` | `scripts/auto-merge.sh main` |
| Deploy production | `main` | `scripts/deploy.sh` trong thư mục `/home/coder/rms-deploy` |

Deploy (`scripts/deploy.sh`):
- Lấy mã `main` mới nhất, build image `app` và `web`, chạy `docker compose up --no-deps --wait`.
- Kiểm tra `/actuator/health` và trang web. Lỗi thì tự tag lại image cũ và chạy lại.
- Không đụng container `db` và volume `restaurant-management-system_pgdata`.

## 3. Thiết lập một lần

### 3.1 Credentials trong Jenkins
**Manage Jenkins → Credentials → (global) → Add Credentials**. Cần ba credential:

| ID | Loại | Nội dung | Dùng cho |
|---|---|---|---|
| `github-token` | Secret text | Token GitHub classic, scope `repo` | Pipeline push merge và lấy mã `main` |
| `github-branch-source` | Username with password | Username GitHub + cùng token | Multibranch quét repo và checkout |
| `rms-prod-env` | Secret text | File `.env` production mã hóa base64 trên MỘT dòng: `base64 -w0 .env` | Deploy production |

Quản lý secret:
- Máy dev dùng `.env` riêng (mẫu `.env.example`, giá trị thử), không bao giờ chứa secret production.
- Secret production chỉ nằm trong credential `rms-prod-env`. Khi deploy, pipeline ghi ra `.env` trong thư mục triển khai (quyền 600), chạy compose rồi xóa ngay.
- Đổi secret: sửa credential `rms-prod-env` trên giao diện Jenkins, sau đó chạy lại build `main`. Không cần SSH vào máy chủ.
- Lý do dùng base64: ô Secret của Jenkins là một dòng, dán nhiều dòng sẽ mất xuống dòng và `.env` hỏng.
- Lưu ý: mật khẩu `DB_*` phải khớp với database đã khởi tạo. Đổi mật khẩu trong credential không tự đổi mật khẩu trong Postgres.

### 3.2 Job Jenkins
**New Item → Multibranch Pipeline**, tên `rsm`:
- Branch Sources → GitHub → repo `Anpham120/restaurant-management-system`, credential `github-branch-source`.
- Build Configuration: Jenkinsfile tại `Jenkinsfile`.
- Scan Multibranch Pipeline Triggers: định kỳ 1 phút (đã có `pollSCM` trong Jenkinsfile).

Webhook GitHub để chạy ngay khi push (không chờ polling):
1. Tạo chuỗi bí mật: `openssl rand -hex 32`.
2. Jenkins: credential Secret text ID `github-webhook-secret` chứa chuỗi đó. Ở **Manage Jenkins → System → GitHub → Advanced → Shared secrets** chọn credential này rồi Save.
3. GitHub: **Settings → Webhooks → Add webhook**. Payload URL `https://jenkins.atlasplatform12.tech/github-webhook/`, Content type `application/json`, Secret là chuỗi ở bước 1, chỉ chọn sự kiện **push**.
4. Kiểm tra: **Recent Deliveries** → lần `ping` phải có mã 200. Jenkins bản này từ chối webhook không có chữ ký (400), nên bắt buộc phải đặt Secret ở cả hai nơi và hai chuỗi phải giống nhau.

`pollSCM` trong `Jenkinsfile` giữ ở mức 15 phút làm phương án dự phòng khi webhook lỗi.

### 3.3 Thư mục triển khai
```bash
git clone --branch main https://github.com/Anpham120/restaurant-management-system.git /home/coder/rms-deploy
```
Không cần tạo `.env` tay: pipeline tự ghi từ credential `rms-prod-env` mỗi lần deploy. Kiểm tra uid của user `coder` bằng `id -u coder`. Nếu khác 1000 thì sửa `chown -R 1000:1000` trong `Jenkinsfile`.

### 3.4 Giám sát
```bash
cd /home/coder/projects/RSM/restaurant-management-system/monitoring
echo "GRAFANA_ADMIN_PASSWORD=<mật khẩu mạnh>" > .env
docker compose up -d
```
Truy cập Grafana bằng SSH tunnel: `ssh -L 3000:127.0.0.1:3000 <user>@<ip>`, rồi mở `http://127.0.0.1:3000`.
Import dashboard có sẵn từ grafana.com: **4701** (JVM Micrometer) và **1860** (Node Exporter Full). Datasource Prometheus đã được cấu hình sẵn.

Prometheus chỉ thấy app sau khi bản có `micrometer-registry-prometheus` được deploy. Trước đó target `rms-app` sẽ báo DOWN.

### 3.5 Cảnh báo (Alertmanager + Telegram)
Prometheus đánh giá các luật trong `monitoring/prometheus/alerts.yml` (app chết, lỗi 5xx cao, heap JVM, ổ đĩa dưới 15%, RAM và CPU trên 90%). Khi vi phạm, Alertmanager gửi tin nhắn Telegram và gửi thêm tin "đã khôi phục" khi hết lỗi.

Thiết lập bot một lần:
1. Trong Telegram, chat với `@BotFather`, gửi `/newbot`, đặt tên. Nhận **bot token**.
2. Mở chat với bot vừa tạo và gửi một tin bất kỳ.
3. Lấy chat id: `curl -s "https://api.telegram.org/bot<TOKEN>/getUpdates"`, tìm `"chat":{"id":<số>`.
4. Ghi token vào file không commit: `printf '%s' '<TOKEN>' > monitoring/secrets/telegram_bot_token && chmod 600 monitoring/secrets/telegram_bot_token`.
5. Đặt `chat_id` trong `monitoring/alertmanager/alertmanager.yml` (chat id không phải bí mật). Bot không nhắn trước được: phải bấm Start với bot ít nhất một lần.
6. `cd monitoring && docker compose up -d`. Alertmanager chạy bằng uid 1000 (user `coder`) để file token giữ quyền `600`; nếu user khác thì sửa `user:` trong `monitoring/compose.yaml`.

Thử bắn một cảnh báo giả: `curl -XPOST 127.0.0.1:9093/api/v2/alerts -H 'Content-Type: application/json' -d '[{"labels":{"alertname":"Thu","severity":"warning"},"annotations":{"summary":"Thử gửi cảnh báo"}}]'`

## 4. Quay lui và giới hạn

- Quay lui tự động chỉ áp dụng cho image `app` và `web`. Flyway là migration tiến một chiều: nếu bản mới đã migrate schema thì quay lui image không hoàn tác được DB. Vì vậy thay đổi schema phải tương thích ngược.
- Không có cổng duyệt tay nghĩa là lỗi logic không bị test bắt được sẽ lên production sau khi pass CI. Đây là đánh đổi của yêu cầu tự động hóa hoàn toàn.
- `/actuator/prometheus` được permitAll để Prometheus scrape. Nginx máy chủ chỉ proxy `/api/` và `/actuator/health`, nên đường dẫn này không lộ ra internet. App chỉ lắng nghe `127.0.0.1:8081`.

## 5. Kiểm tra nhanh

```bash
# Cú pháp script
bash -n scripts/auto-merge.sh && bash -n scripts/deploy.sh
# Cấu hình monitoring
cd monitoring && GRAFANA_ADMIN_PASSWORD=x docker compose config >/dev/null && echo OK
# Trạng thái deploy
curl -s http://127.0.0.1:8081/actuator/health
```
