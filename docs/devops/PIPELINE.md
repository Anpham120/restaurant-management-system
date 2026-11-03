# Quy trình CI/CD (SCM + Jenkins + Docker + Nginx + Prometheus + Grafana)

Không có bước duyệt tay. Mọi thứ đi từ commit đến production đều do Jenkins chạy.

## 1. Mô hình nhánh

```
feature/xxx ──(CI pass, tự merge --no-ff)──► develop ──(CI pass, tự promote ff-only)──► main ──► DEPLOY
```

| Nhánh | Tạo từ | Pipeline chạy | Kết quả khi CI pass |
|---|---|---|---|
| `feature/*` | `develop` | Kiểm migration, test backend + frontend, Trivy mã nguồn | Tự merge vào `develop` |
| `develop` | — | Như trên + staging (smoke test đọc + E2E ghi), Trivy image, gắn tag phát hành | Tự promote lên `main` (fast-forward) |
| `main` | — | Chỉ deploy (không test lại, không build lại), kiểm qua domain thật | Deploy production, tự quay lui nếu lỗi |

Không bắt buộc Pull Request hay người duyệt: dự án nhỏ (bài tập lớn), nhóm chọn tự merge khi CI xanh. Khi dự án lớn hơn,
bật bảo vệ nhánh `develop` (bắt buộc PR + 1 người duyệt + check Jenkins xanh); Jenkins đã bật phát hiện PR sẵn.

Quy tắc phát triển:
1. `git switch develop && git switch -c feature/ten-tinh-nang`
2. Làm việc, commit, `git push -u origin feature/ten-tinh-nang`
3. Jenkins test. Nếu pass, nhánh tự merge vào `develop` rồi bị xóa trên GitHub. Nếu fail, nhánh không được merge và vẫn giữ để sửa tiếp.
   - Nếu có người push thêm commit vào nhánh trong lúc build, nhánh được giữ lại và lần build sau sẽ merge tiếp.
   - Nhánh ở máy cá nhân không bị xóa. Dọn bằng `git fetch --prune && git branch -d feature/ten-tinh-nang`.
   - Jenkins tự xóa job của nhánh đã xóa, kèm lịch sử build của nhánh đó. Commit merge trong `develop` vẫn ghi tên nhánh.
4. `develop` test lại. Nếu pass, tự đẩy lên `main` và `main` tự deploy.

Lưu ý: không commit trực tiếp lên `main` hoặc `develop`. Nếu làm vậy, lần promote sau sẽ không fast-forward được và pipeline báo đỏ.

## 2. Pipeline (`Jenkinsfile`)

| Stage | Chạy ở | Làm gì |
|---|---|---|
| Kiểm migration | `feature/*`, `develop` | `scripts/check-migrations.sh`: chặn migration không tương thích ngược (xem dưới) |
| Test backend | `feature/*`, `develop` | `mvn verify` (unit + `*IT` dùng Testcontainers + `ModularityTest`) trong container `maven:3.9-eclipse-temurin-25`. Jenkins hiện kết quả test (JUnit) và lưu báo cáo độ phủ JaCoCo (`target/site/jacoco/index.html`, mục Artifacts của build); log in dòng "Độ phủ dòng code" |
| Test frontend | `feature/*`, `develop` | `npm ci`, `typecheck`, `build` trong container `node:22-alpine` |
| Quét bảo mật mã nguồn | `feature/*`, `develop` | `scripts/security-scan.sh fs`: Trivy quét thư viện Maven, npm và secret lộ trong mã |
| Merge feature vào develop | `feature/*` | `scripts/auto-merge.sh develop` |
| Staging smoke test | `develop` | `scripts/staging-test.sh`: dựng bản tạm trên bản sao lưu production mới nhất, quét đọc 32 API, chạy E2E ghi (`scripts/e2e-flow.sh`) rồi hủy |
| Quét bảo mật image | `develop` | `scripts/security-scan.sh image`: Trivy quét image `app` và `web` vừa build ở staging. Qua thì `scripts/tag-release.sh` gắn tag `rms-release-{app,web}:<commit>` |
| Đẩy image lên GHCR | `develop` | `scripts/push-release.sh`: bản lưu image ngoài máy chủ. Thiếu credential `ghcr-token` thì bỏ qua |
| Promote develop lên main | `develop` | `scripts/auto-merge.sh main` |
| Deploy production | `main` | `scripts/deploy.sh` trong thư mục `/home/coder/rms-deploy` |

`main` không chạy lại test và quét mã nguồn: promote dùng `--ff-only`, nên commit trên `main` trùng SHA với commit `develop` vừa qua đủ test, staging và Trivy. Chạy lại chỉ cho cùng kết quả.

Build một lần, deploy đúng image đã kiểm:
- Image `app`, `web` chỉ build một lần ở staging của `develop`. Qua smoke test và Trivy image thì được gắn tag `rms-release-app:<commit>`, `rms-release-web:<commit>`.
- `scripts/tag-release.sh` giữ tag của 5 commit `develop` gần nhất và của commit mới hơn (đang chờ `main` deploy). Tag của commit cũ hơn bị bỏ.
- Chạy lại build `develop` trên commit đã có tag thì tag giữ nguyên image cũ. Build Docker không tái lập (mã image khác dù cùng mã nguồn), gắn lại tag sẽ làm production và GHCR giữ hai image khác nhau cho cùng một commit.
- Staging và deploy đều checkout đúng `GIT_COMMIT` của build, không lấy đầu nhánh mới nhất, để image và mã nguồn luôn cùng một commit.

E2E ghi dữ liệu (`scripts/e2e-flow.sh`), chạy trên bản sao dữ liệu thật ở staging:
- Kho: tạo nguyên liệu → nhập 10 phần → xuất → bếp nhận. Thực đơn: món + công thức (1 phần/suất) → thêm vào thực đơn hôm nay, hạn mức 3.
- Phục vụ: mở bàn → gọi 2 suất → bếp nhận (giữ 2 phần) → nấu → xong → bưng (tồn bếp còn 8) → chốt hóa đơn 100 000 → thu tiền mặt → hóa đơn PAID, bàn trống.
- Ca lỗi: số lượng âm/0 (400), gửi trùng `requestKey` không tạo món trùng, vượt hạn mức bị từ chối, thu tiền thiếu (422), bấm thu tiền 2 lần cùng lúc chỉ 1 lần thành công và chỉ 1 khoản thu.
- Thực đơn hôm nay trong dữ liệu production đã đóng (sau giờ đóng, trước 04:00) thì phần E2E in `BỎ QUA` và không chặn pipeline.

Deploy (`scripts/deploy.sh`):
- Kiểm tra có image `rms-release-*:<commit>`. Không có (commit thẳng lên `main`, hoặc image đã bị dọn) thì dừng, chưa đụng gì. Cách xử lý: chạy lại build `develop` của commit đó rồi chạy lại build `main`.
- Sao lưu database (`scripts/backup-db.sh pre-deploy`). Sao lưu lỗi thì dừng, chưa đụng gì.
- Không build lại: gắn tag image phát hành cho slot cần dựng rồi `docker compose up --no-deps --no-build --wait`.
- Kiểm `/actuator/health` và trang web trong máy, rồi kiểm qua domain thật (`PUBLIC_URL`, qua nginx + HTTPS): trang web, health UP, `/api/tables` trả 401.
- **Blue-green** (khi nginx máy chủ đã cân bằng 2 slot, mục 3.7; `deploy.sh` nhận ra bằng cách tìm `127.0.0.1:8086` trong file site nginx, Jenkinsfile mount `/etc/nginx` chỉ đọc):
  - Slot A = `app`/`web` (cổng 8081/8083), slot B = `app-b`/`web-b` (8086/8087, profile `slot-b` trong `compose.yaml`). Bình thường chỉ một slot chạy.
  - Dựng slot đang dừng bằng image mới → healthy → dừng slot cũ (Spring tắt êm) → kiểm qua domain. nginx tự chuyển request sang slot còn sống, không gián đoạn.
  - Slot mới không healthy: dừng slot mới, slot cũ vẫn phục vụ. Kiểm qua domain lỗi: bật lại slot cũ, dừng slot mới.
  - Đã thử trên bản sao (project riêng, nginx riêng): 2 lần chuyển A→B→A dưới tải 10 request/giây, 903 request, 0 lỗi; slot mới hỏng thì slot cũ phục vụ tiếp, 0 lỗi.
  - Quay về bản trước bằng tay: container slot cũ được giữ (đã dừng). `docker compose start <slot cũ>`, chờ healthy, rồi `docker compose stop <slot mới>` (trong `/home/coder/rms-deploy`, `COMPOSE_PROJECT_NAME=restaurant-management-system COMPOSE_PROFILES=slot-b`).
- **Tại chỗ** (nginx chưa cấu hình 2 slot): thay container slot A, gián đoạn ~15–25 giây; lỗi thì tự tag lại image `:previous` và chạy lại.
- Không đụng container `db` và volume `restaurant-management-system_pgdata`.
- Profile Spring luôn là `prod`: `deploy.sh` export `SPRING_PROFILES_ACTIVE=prod`, ghi đè giá trị trong `.env`. Profile `prod` cấm Flyway tự baseline; database production đã có lịch sử Flyway nên không ảnh hưởng.
- Staging cũng chạy `prod` để kiểm tra đúng cấu hình production. Riêng staging ghép thêm `compose.staging.yaml` để cho phép baseline trên database mới tạo. Production không dùng file này.
- `staging-test.sh` ghi đè rồi xóa `.env` ở thư mục đang chạy. Script tự dừng nếu thấy `.env` không phải do nó tạo, nên đừng chạy nó trong thư mục dự án hay thư mục deploy.
- `app`, `web`, `db` đặt `restart: unless-stopped`: tự chạy lại khi container lỗi hoặc máy chủ khởi động lại.
- Telegram: build lỗi ở bất kỳ nhánh nào và mỗi lần `main` deploy xong đều có tin nhắn kèm commit và link log.

Migration tương thích ngược (`scripts/check-migrations.sh`):
- Deploy lỗi chỉ quay lui image, không quay lui database. Vì vậy bản app đang chạy phải còn chạy được trên schema sau migration.
- Script chặn `DROP TABLE`, `DROP COLUMN`, `RENAME`, `ALTER COLUMN … TYPE`, `SET NOT NULL`, `ADD COLUMN … NOT NULL` không có `DEFAULT`.
- Cách làm đúng là tách 2 lần phát hành: **expand** (thêm cột/bảng mới, cho phép NULL hoặc có DEFAULT) rồi **contract** (lần phát hành sau, khi không còn code dùng phần cũ, mới xóa/đổi). File contract ghi dòng `-- contract: <lý do>` để script cho qua.

Quét bảo mật (Trivy):
- Pipeline dừng khi có lỗ hổng mức HIGH hoặc CRITICAL **đã có bản vá**, hoặc khi phát hiện secret trong mã. Lỗ hổng chưa có bản vá chỉ được bỏ qua vì chưa có cách sửa.
- Quét image ở `develop` là đủ cho production: production chạy đúng image đã quét (tag `rms-release-*`), không build lại.
- Trivy được ghim theo digest của bản 0.69.3. Các bản 0.69.4 đến 0.69.6 từng bị cài mã độc (CVE-2026-33634), nên không đổi sang tag khác mà không kiểm tra digest.
- CSDL lỗ hổng nằm trong volume `rms-trivy-cache` và tự cập nhật. Vì vậy một commit không đổi gì vẫn có thể bị chặn khi có CVE mới công bố.
- Khi image bị chặn: nâng image nền trong `Dockerfile` hoặc `frontend/Dockerfile`. Nếu bản vá đã có trong kho Alpine nhưng cache build Docker còn giữ lớp `apk upgrade` cũ, chạy `docker builder prune` trên máy chủ rồi chạy lại.
- Chạy tay trên máy chủ: `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.69.3@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c image --severity HIGH,CRITICAL <image>`.

## 3. Thiết lập một lần

### 3.1 Credentials trong Jenkins
**Manage Jenkins → Credentials → (global) → Add Credentials**. Cần các credential sau:

| ID | Loại | Nội dung | Dùng cho |
|---|---|---|---|
| `github-token` | Secret text | Token GitHub classic, scope `repo` | Pipeline push merge và lấy mã `main` |
| `github-branch-source` | Username with password | Username GitHub + cùng token | Multibranch quét repo và checkout |
| `rms-prod-env` | Secret text | File `.env` production mã hóa base64 trên MỘT dòng: `base64 -w0 .env` | Deploy production |
| `telegram-bot-token` | Secret text | Bot token Telegram (cùng bot với mục 3.5) | Báo kết quả pipeline. Thiếu thì pipeline vẫn chạy, chỉ không có tin nhắn |
| `ghcr-token` | Secret text | Token GitHub classic, scope `write:packages` | Đẩy image phát hành lên `ghcr.io/anpham120/rms-{app,web}:<commit>` (`scripts/push-release.sh`). Thiếu thì bỏ qua, không chặn pipeline |

Quản lý secret:
- Máy dev dùng `.env` riêng (mẫu `.env.example`, giá trị thử), không bao giờ chứa secret production.
- `.env` dev đặt `COMPOSE_PROJECT_NAME=rms-dev`. Nếu thiếu, compose lấy tên thư mục `restaurant-management-system`, trùng project production: lệnh `docker compose` trong thư mục dev sẽ tác động vào container và volume production. Trên máy chủ, dev dùng cổng riêng (db 5435, app 8071, web 8073).
- Secret production chỉ nằm trong credential `rms-prod-env`. Khi deploy, pipeline ghi ra `.env` trong thư mục triển khai (quyền 600), chạy compose rồi xóa ngay.
- Đổi secret: sửa credential `rms-prod-env` trên giao diện Jenkins, sau đó chạy lại build `main`. Không cần SSH vào máy chủ.
- Lý do dùng base64: ô Secret của Jenkins là một dòng, dán nhiều dòng sẽ mất xuống dòng và `.env` hỏng.
- Lưu ý: mật khẩu `DB_*` phải khớp với database đã khởi tạo. Đổi mật khẩu trong credential không tự đổi mật khẩu trong Postgres.

### 3.2 Job Jenkins
**New Item → Multibranch Pipeline**, tên `rsm`:
- Branch Sources → GitHub → repo `Anpham120/restaurant-management-system`, credential `github-branch-source`.
- Build Configuration: Jenkinsfile tại `Jenkinsfile`.
- Scan Multibranch Pipeline Triggers: định kỳ 1 phút. Đây là phương án dự phòng khi webhook lỗi.

Webhook GitHub để chạy ngay khi push (không chờ polling):
1. Tạo chuỗi bí mật: `openssl rand -hex 32`.
2. Jenkins: credential Secret text ID `github-webhook-secret` chứa chuỗi đó. Ở **Manage Jenkins → System → GitHub → Advanced → Shared secrets** chọn credential này rồi Save.
3. GitHub: **Settings → Webhooks → Add webhook**. Payload URL `https://jenkins.atlasplatform12.tech/github-webhook/`, Content type `application/json`, Secret là chuỗi ở bước 1, chỉ chọn sự kiện **push**.
4. Kiểm tra: **Recent Deliveries** → lần `ping` phải có mã 200. Jenkins bản này từ chối webhook không có chữ ký (400), nên bắt buộc phải đặt Secret ở cả hai nơi và hai chuỗi phải giống nhau.

Không thêm `pollSCM` vào `Jenkinsfile`. Quét multibranch đã làm dự phòng. Nếu thêm, lần poll trùng lúc với webhook sẽ xếp một build thứ hai cho cùng commit, và nhánh `main` sẽ deploy hai lần.

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

### 3.6 Sao lưu database
`scripts/backup-db.sh` chạy `pg_dump` (định dạng custom, đã nén) vào `/home/coder/rms-backups`, quyền 600. Có hai loại, mỗi loại giữ 7 bản mới nhất:
- `pre-deploy-*.dump`: deploy tự tạo trước mỗi lần deploy.
- `daily-*.dump`: cron của user `coder`, 02:30 giờ Việt Nam (19:30 UTC). Cài một lần:
  ```bash
  (crontab -l 2>/dev/null; echo '30 19 * * * cd /home/coder/rms-deploy && scripts/backup-db.sh daily >> /home/coder/rms-backups/backup.log 2>&1') | crontab -
  ```

Khôi phục (xóa toàn bộ dữ liệu hiện tại, chỉ làm khi thật sự cần, ví dụ quay lại trước một migration hỏng):
```bash
cd /home/coder/rms-deploy && scripts/restore-db.sh /home/coder/rms-backups/pre-deploy-<thời điểm>.dump
```
Script hỏi xác nhận (gõ `KHOI PHUC`), tự sao lưu dữ liệu hiện tại thành `pre-restore-*`, dừng app, xóa schema `public` rồi nạp
bản sao lưu trong **một transaction** (lỗi thì giữ nguyên dữ liệu cũ), sau đó chạy lại app. Sau khi khôi phục về trước một
migration, deploy lại bản code cũ tương ứng.

Bản sao lưu luôn được thử khôi phục: mỗi lần build `develop`, staging nạp bản sao lưu mới nhất theo đúng cách trên, chạy
migration Flyway của bản mới trên dữ liệu thật và quét đọc mọi module (`scripts/staging-test.sh`). Dữ liệu này chỉ nằm trong
database staging ở `127.0.0.1` và bị xóa khi staging chạy xong.

Bản sao ngoài máy chủ (`scripts/offsite-backup.sh`): mỗi ngày mã hóa AES-256 bản `daily-*` mới nhất rồi gửi qua bot
Telegram đang dùng cho cảnh báo. Telegram chỉ giữ file đã mã hóa. Cài một lần:
```bash
( umask 077; openssl rand -base64 48 > /home/coder/rms-backups/.offsite.key )
# Lưu nội dung khóa vào trình quản lý mật khẩu ngay: máy chủ mất thì chỉ còn bản này để giải mã
(crontab -l; echo '35 19 * * * /home/coder/rms-deploy/scripts/offsite-backup.sh >> /home/coder/rms-backups/backup.log 2>&1') | crontab -
```
Chạy 5 phút sau bản `daily`. Giải mã một file tải từ Telegram:
`openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:<khóa> -in <file>.dump.enc -out <file>.dump`,
rồi khôi phục bằng `scripts/restore-db.sh`.

### 3.7 nginx máy chủ: blue-green và chống dò mật khẩu
File mẫu: `docs/devops/nginx/rms.atlasplatform12.tech.conf` (site) và `docs/devops/nginx/rms-login-ratelimit.conf` (zone, khối http).
- Upstream `rms_app` (8081, 8086) và `rms_web` (8083, 8087); `proxy_next_upstream error` chuyển request sang slot còn sống.
- `proxy_set_header Host $host` đặt ở mức `server`: thiếu thì Host gửi đi là `rms_app` (có dấu `_`) và Tomcat trả 400.
- Cài một lần bằng quyền root (sao lưu file cũ, `nginx -t`, reload, kiểm qua domain, lỗi thì tự khôi phục):
  ```bash
  cd /home/coder/projects/RSM/restaurant-management-system
  sudo cp -a /etc/nginx/sites-available/rms.atlasplatform12.tech.conf /etc/nginx/sites-available/rms.atlasplatform12.tech.conf.bak
  sudo install -m 644 docs/devops/nginx/rms-login-ratelimit.conf /etc/nginx/conf.d/
  sudo install -m 644 docs/devops/nginx/rms.atlasplatform12.tech.conf /etc/nginx/sites-available/
  sudo nginx -t && sudo systemctl reload nginx
  ```
  `nginx -t` lỗi thì chép lại file `.bak` và xóa file trong `conf.d`.

Chống dò mật khẩu
Hai lớp, không đổi schema database:
- App (`LoginThrottle`): một tên đăng nhập sai 5 lần trong 15 phút thì bị khóa tạm 15 phút (trả `429 TOO_MANY_ATTEMPTS`, kể cả
  khi lần sau nhập đúng). Áp dụng cả cho tên không tồn tại nên không lộ tài khoản nào có thật. Khởi động lại app thì xóa bộ đếm.
- Nginx: mỗi IP tối đa 5 lần gọi `/api/auth/login` mỗi phút (dồn thêm 5), vượt thì 429. Nằm trong file site ở trên.

## 4. Quay lui và giới hạn

- Tài nguyên: CI chạy chung máy với production (4 CPU, 7 GB). Container Maven tối đa 2 CPU / 3 GB, Node 2 CPU / 2 GB, staging app 1,5 CPU / 1,5 GB, staging db 1 CPU / 1 GB; tất cả `cpu-shares 512` (production 1024) nên khi tranh CPU production được ưu tiên. Bước build image trong staging (Docker build) chưa giới hạn được.
- Máy chủ duy nhất: Jenkins, staging và production chung một máy, Jenkins giữ `docker.sock` (tương đương root). Hạn chế đã biết của đồ án; thực tế nên tách máy CI.
- Quay lui tự động chỉ áp dụng cho image `app` và `web`. Flyway là migration tiến một chiều: nếu bản mới đã migrate schema thì quay lui image không hoàn tác được DB. Vì vậy thay đổi schema phải tương thích ngược. Nếu cần quay DB về trước khi deploy, khôi phục bản `pre-deploy-*.dump` mới nhất (mục 3.6).
- Không có cổng duyệt tay nghĩa là lỗi logic không bị test bắt được sẽ lên production sau khi pass CI. Đây là đánh đổi của yêu cầu tự động hóa hoàn toàn.
- `/actuator/prometheus` được permitAll để Prometheus scrape. Nginx máy chủ chỉ proxy `/api/` và `/actuator/health`, nên đường dẫn này không lộ ra internet. App chỉ lắng nghe `127.0.0.1:8081`.

## 5. Kiểm tra nhanh

```bash
# Cú pháp script
bash -n scripts/auto-merge.sh && bash -n scripts/deploy.sh && bash -n scripts/tag-release.sh && bash -n scripts/e2e-flow.sh && bash -n scripts/push-release.sh
# Slot đang chạy (blue-green)
docker ps --filter name=restaurant-management-system-app --format '{{.Names}} {{.Status}}'
scripts/check-migrations.sh
# Image phát hành đang có
docker images 'rms-release-*'
# Cấu hình monitoring
cd monitoring && GRAFANA_ADMIN_PASSWORD=x docker compose config >/dev/null && echo OK
# Trạng thái deploy
curl -s http://127.0.0.1:8081/actuator/health
```
