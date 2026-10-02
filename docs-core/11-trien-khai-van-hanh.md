# 11. Triển khai và vận hành

Tài liệu này là các bước dựng hai máy, rồi vận hành hằng ngày:
- **Máy ứng dụng** chạy cả **production** (nhánh `main`) và **staging** (nhánh `develop`).
- **Máy công cụ** chạy giám sát và cảnh báo (mục 11.10).

Kiến trúc và pipeline ở [tài liệu 9](09-kien-truc-va-cicd.md). Các mục ứng với việc P0-01 → P0-04, P0-07 và P5-03 trong [tài liệu 10](10-ke-hoach-phat-trien.md).

Trong các lệnh dưới đây, thay `khoibep.example.vn` bằng tên miền thật, `<IP>` bằng địa chỉ máy ứng dụng.

## 11.1 Chuẩn bị

| Thứ cần có | Gợi ý |
|---|---|
| Máy ứng dụng | VPS Ubuntu 24.04 hoặc 22.04, chip **x86_64** (image chỉ build cho amd64), **2 vCPU, 4 GB RAM**, 40 GB SSD, IP tĩnh. Tối thiểu 2 GB RAM kèm 2 GB swap; 1 GB chỉ đủ khi chạy một môi trường |
| Máy công cụ | VPS Ubuntu 24.04, x86_64, **4 vCPU, 8 GB RAM**, 80 GB SSD, IP tĩnh. Chạy giám sát (mục 11.10), và còn chỗ cho Jenkins |
| Tên miền | Hai bản ghi A trỏ về `<IP>` của máy ứng dụng: `khoibep.example.vn` và `staging.khoibep.example.vn`. Một bản ghi A `monitor.khoibep.example.vn` trỏ về IP máy công cụ. Dùng Cloudflare thì để **DNS only** (mây xám), để Caddy tự lấy chứng chỉ |
| Telegram | Một bot và một nhóm chat để nhận cảnh báo (mục 11.10) |
| SePay | Tài khoản SePay đã liên kết tài khoản ngân hàng nhận tiền của quán |
| GitHub | Quyền admin trên repo để tạo environment, secret, ruleset |

Mỗi môi trường chạy 4 container: PostgreSQL, backend, web (Nginx) và sao lưu. Một Caddy dùng chung đứng trước cả hai môi trường để có HTTPS:

```text
Internet ──443──> Caddy ──> 127.0.0.1:8081  web production  ──> backend, PostgreSQL (~/khoibep-rms)
                        └─> 127.0.0.1:8080  web staging     ──> backend, PostgreSQL (~/khoibep-rms-staging)
```

## 11.2 Cài máy chủ (làm một lần)

Đăng nhập máy chủ bằng tài khoản có quyền `sudo`:

```bash
# Docker Engine và Docker Compose, bằng script chính thức của Docker
curl -fsSL https://get.docker.com | sudo sh

# Người dùng riêng cho việc deploy; GitHub Actions đăng nhập bằng tài khoản này
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy

# Tường lửa: chỉ mở SSH, HTTP, HTTPS
sudo ufw allow OpenSSH
sudo ufw allow 80,443/tcp
sudo ufw enable

# Máy 2 GB RAM chạy hai môi trường: thêm 2 GB swap cho chắc
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Docker mở cổng không qua `ufw`, nên các container web chỉ mở cổng trên `127.0.0.1` (biến `HTTP_BIND`), còn PostgreSQL không mở cổng nào ra ngoài.

**Khoá SSH cho GitHub Actions.** Trên máy của bạn, tạo một cặp khoá chỉ dùng cho việc deploy:

```bash
ssh-keygen -t ed25519 -N "" -C github-actions-deploy -f khoibep-deploy
```

Trên máy chủ, cho khoá công khai vào tài khoản `deploy` (dán nội dung tệp `khoibep-deploy.pub` vào chỗ `<khoá công khai>`):

```bash
sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
echo "<khoá công khai>" | sudo tee -a /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

Tệp `khoibep-deploy` (khoá bí mật) sẽ là secret `DEPLOY_SSH_KEY` ở mục 11.4. Không đưa nó vào Git, xong việc thì xoá khỏi máy của bạn.

## 11.3 Thư mục, tệp `.env` và HTTPS

Mọi lệnh từ đây chạy bằng tài khoản `deploy` (`sudo -iu deploy`). Các tệp mẫu lấy thẳng từ repo; nếu `main` chưa có thư mục `deploy/caddy` thì thay `main` bằng `develop`:

```bash
BASE=https://raw.githubusercontent.com/Anpham120/restaurant-management-system/main/deploy
mkdir -p ~/khoibep-rms ~/khoibep-rms-staging ~/caddy
curl -fsSL $BASE/.env.example -o ~/khoibep-rms/.env
curl -fsSL $BASE/.env.example -o ~/khoibep-rms-staging/.env
chmod 600 ~/khoibep-rms/.env ~/khoibep-rms-staging/.env
openssl rand -base64 48   # một giá trị cho APP_JWT_SECRET của mỗi môi trường
openssl rand -base64 24   # một giá trị cho POSTGRES_PASSWORD của mỗi môi trường
openssl rand -hex 32      # một giá trị APP_METRICS_TOKEN, dùng chung cho hai môi trường
```

Sửa hai tệp `.env` (`nano ~/khoibep-rms/.env`). Các biến khác nhau giữa hai môi trường:

| Biến | Production (`~/khoibep-rms/.env`) | Staging (`~/khoibep-rms-staging/.env`) |
|---|---|---|
| `POSTGRES_PASSWORD`, `APP_JWT_SECRET` | Giá trị ngẫu nhiên riêng | Giá trị ngẫu nhiên khác |
| `APP_PUBLIC_BASE_URL` | `https://khoibep.example.vn` | `https://staging.khoibep.example.vn` |
| `HTTP_PORT` | `8081` | `8080` |
| `APP_DEMO_ACCOUNTS_ENABLED` | `false` | `true` |
| `APP_DEMO_ACCOUNTS_PASSWORD` | Không dùng | Mật khẩu riêng; đừng để `123456` trên máy có Internet |
| `APP_INITIAL_ADMIN_PASSWORD` | Ít nhất 12 ký tự (BR-48); xoá sau lần đăng nhập đầu | Không dùng |
| `SEPAY_API_KEY` | API key của webhook production | API key của webhook staging |
| `APP_METRICS_TOKEN` | Giá trị chung ở trên; Alloy dùng nó để đọc số liệu (mục 11.10) | Cùng giá trị với production |

`APP_PUBLIC_BASE_URL` là địa chỉ in trong mã QR bàn: đổi tên miền sau này thì phải in lại QR.

**HTTPS bằng Caddy.** Một Caddy nhận cổng 80, 443 cho cả hai môi trường, tự lấy và gia hạn chứng chỉ Let's Encrypt. Bản ghi DNS phải trỏ đúng trước khi chạy:

```bash
cd ~/caddy
curl -fsSLO $BASE/caddy/Caddyfile
curl -fsSLO $BASE/caddy/docker-compose.yml
curl -fsSL $BASE/caddy/.env.example -o .env
nano .env                  # DOMAIN, STAGING_DOMAIN
docker compose up -d
docker compose logs -f     # chờ dòng "certificate obtained successfully" cho cả hai tên miền
```

Caddy chạy độc lập với pipeline: chỉ khi đổi tên miền hoặc cổng mới cần sửa `~/caddy/.env` rồi chạy lại `docker compose up -d`.

## 11.4 GitHub (P0-01, P0-02)

1. **Ruleset** (Settings → Rules → Rulesets) cho `main` và `develop`:
   - Bắt buộc qua pull request.
   - Bắt buộc các check `Backend - build and test`, `Frontend - lint, test, build`, `E2E - acceptance scenario`, `CodeQL - Java`, `CodeQL - TypeScript` xanh.
   - Chặn force push và xoá nhánh.
2. **Environment** (Settings → Environments):
   - `staging`, không cần duyệt.
   - `production`, có **Required reviewers** (chủ repo).
   - Mỗi environment có các secret và biến sau:

| Tên | Loại | Giá trị |
|---|---|---|
| `DEPLOY_HOST` | Secret | `<IP>` |
| `DEPLOY_USER` | Secret | `deploy` |
| `DEPLOY_SSH_KEY` | Secret | Nội dung tệp `khoibep-deploy` |
| `PUBLIC_URL` | Variable | `https://staging.khoibep.example.vn` (staging), `https://khoibep.example.vn` (production) |

3. **Bật deploy:** Settings → Secrets and variables → Actions → Variables, thêm biến của repo `DEPLOY_ENABLED` = `true`.

Image build xong được đẩy lên GitHub Container Registry. Job deploy đăng nhập GHCR trên máy chủ bằng token của chính lần chạy, nên không cần tạo token riêng.

## 11.5 Lần deploy đầu và tài khoản quản trị

Pipeline chỉ deploy khi có **push** vào `develop` (staging) hoặc `main` (production), nên lần deploy đầu là lần merge kế tiếp sau khi bật `DEPLOY_ENABLED`. Mỗi lần deploy:
1. Chép `docker-compose.prod.yml`, `backup.sh`, `restore.sh` vào `~/khoibep-rms` hoặc `~/khoibep-rms-staging`.
2. Kéo image đúng commit rồi khởi động lại.
3. Gọi `PUBLIC_URL/actuator/health` tới khi trả `UP`.

Sau lần deploy production đầu:
1. Đăng nhập `https://khoibep.example.vn` bằng tài khoản `admin` và `APP_INITIAL_ADMIN_PASSWORD`, rồi đổi mật khẩu ngay (nút chìa khoá trên thanh trên cùng).
2. Xoá dòng `APP_INITIAL_ADMIN_PASSWORD` khỏi `~/khoibep-rms/.env`. Đã có nhân viên thì biến này không còn tác dụng.
3. Ở **Cài đặt**: tên quán, địa chỉ, tài khoản nhận tiền (mã ngân hàng, số tài khoản, tên chủ tài khoản) để in mã VietQR.
4. Tạo tài khoản nhân viên. Sửa thực đơn và bàn mẫu, đặt loại thuế và giá app cho món, rồi in mã QR bàn.

Staging có sẵn 5 tài khoản demo (`admin`, `quanly`, `phucvu`, `bep`, `thungan`) với `APP_DEMO_ACCOUNTS_PASSWORD`.

## 11.6 Nối SePay (P0-03)

Trên SePay, mục **Webhooks**, thêm một webhook cho mỗi môi trường:
- URL: `https://staging.khoibep.example.vn/api/webhooks/sepay` (production: `https://khoibep.example.vn/api/webhooks/sepay`).
- Gửi khi có **tiền vào**.
- Chứng thực **API Key**, giá trị bằng `SEPAY_API_KEY` trong `.env` của môi trường đó. Backend so khớp header `Authorization: Apikey <key>`.

Hai môi trường cùng nhận báo có của một tài khoản ngân hàng. Mỗi bên chỉ khớp mã thanh toán của chính nó, giao dịch lạ nằm ở "Giao dịch không khớp" và không ảnh hưởng gì.

**Thử thật:**
1. Trên staging, mở một đơn có món 2.000 đ, chọn **Chuyển khoản (VietQR)**, quét mã bằng app ngân hàng và chuyển.
2. Trong khoảng 10 giây, bill phải tự đóng và bàn trống.
3. Không tự đóng thì xem tab "Giao dịch không khớp", và log của backend (mục 11.8).

## 11.7 Sao lưu và khôi phục (P0-04)

Dịch vụ `backup` của mỗi môi trường chạy `pg_dump` mỗi đêm lúc `BACKUP_HOUR` giờ Việt Nam, giữ `BACKUP_KEEP` bản trong thư mục `backups/`:

```bash
cd ~/khoibep-rms
ls -lh backups/                                                        # các bản đã có
docker compose -f docker-compose.prod.yml exec backup sh /backup.sh now # sao lưu ngay
sh restore.sh --check backups/rms-2026-10-05-0300.dump                 # thử khôi phục vào CSDL tạm
sh restore.sh backups/rms-2026-10-05-0300.dump                         # khôi phục thật (hỏi xác nhận)
```

- Nên chạy `--check` một lần sau khi dựng xong, và sau mỗi lần nâng cấp lớn.
- Chép bản sao lưu ra khỏi máy chủ định kỳ, vì mất máy chủ là mất luôn bản sao lưu trên đó. Từ máy của bạn: `scp deploy@<IP>:khoibep-rms/backups/*.dump ./`.

## 11.8 Theo dõi

| Việc | Cách xem |
|---|---|
| Dashboard, cảnh báo | Grafana ở `https://monitor.khoibep.example.vn`; sự cố tự báo về Telegram (mục 11.10) |
| Ứng dụng còn chạy | `https://khoibep.example.vn/actuator/health` trả `{"status":"UP"}` |
| Log backend (JSON, ECS) | `docker compose -f docker-compose.prod.yml logs -f backend`, lọc bằng `jq`, ví dụ `... logs --no-log-prefix backend \| jq -r '."log.level" + " " + .message'` |
| Container đang chạy | `docker compose -f docker-compose.prod.yml ps` |
| Dung lượng đĩa | `df -h /` và `docker system df`; job deploy tự xoá image cũ không dùng |
| Webhook SePay hỏng liên tiếp | Trang **Thu ngân** hiện cảnh báo đỏ; khi đó xác nhận tay và kiểm tra cấu hình webhook |

## 11.9 Cập nhật và quay lại bản cũ

- **Cập nhật** là merge vào `develop` (staging tự cập nhật), rồi PR `develop` → `main` (production, sau khi người duyệt bấm duyệt ở tab Actions).
- **Quay lại bản trước:** ở tab Actions, mở lần chạy thành công gần nhất của nhánh đó, bấm **Re-run all jobs**. Job deploy kéo lại image đúng commit cũ.
- **Lưu ý về CSDL:** migration chỉ đi tới, và ứng dụng kiểm tra lược đồ lúc khởi động. Nếu bản mới đã chạy một migration làm đổi lược đồ, bản cũ có thể không khởi động được. Khi đó:
  - Sửa lỗi bằng một bản mới, hoặc
  - Khôi phục bản sao lưu ngay trước lúc cập nhật (mục 11.7) rồi mới quay lại.

## 11.10 Giám sát và cảnh báo (P0-07)

Thiết kế và danh sách cảnh báo ở tài liệu 09 mục 9.8.

**Bot Telegram.**
1. Trong Telegram, nhắn `@BotFather` lệnh `/newbot` rồi đặt tên. BotFather trả một token dạng `123456789:AAH…`: đó là `TELEGRAM_BOT_TOKEN`.
2. Tạo một nhóm, ví dụ "Khói Bếp cảnh báo", thêm bot vào nhóm, rồi gửi một tin bất kỳ trong nhóm.
3. Mở `https://api.telegram.org/bot<token>/getUpdates` trên trình duyệt, tìm `"chat":{"id":-100…`. Số đó, kể cả dấu trừ, là `TELEGRAM_CHAT_ID`.

**Máy công cụ.** Cài Docker, tạo tài khoản `deploy` và bật tường lửa như mục 11.2; không cần swap và khoá SSH cho GitHub Actions. Rồi bằng tài khoản `deploy`:

```bash
mkdir -p ~/monitoring && cd ~/monitoring
curl -fsSL https://github.com/Anpham120/restaurant-management-system/archive/refs/heads/main.tar.gz \
  | tar -xz --strip-components=3 restaurant-management-system-main/deploy/ops
cp .env.example .env && chmod 600 .env
openssl rand -base64 24    # mật khẩu để agent đẩy dữ liệu (INGEST_PASSWORD); ghi lại cho máy ứng dụng
docker run --rm caddy:2-alpine caddy hash-password --plaintext '<mật khẩu vừa tạo>'
nano .env
docker compose up -d
docker compose ps          # 7 container đều đang chạy
```

Biến trong `~/monitoring/.env`:

| Biến | Giá trị |
|---|---|
| `MONITOR_DOMAIN` | `monitor.khoibep.example.vn` |
| `SITE_URL_PRODUCTION`, `SITE_URL_STAGING` | `https://khoibep.example.vn/actuator/health`, `https://staging.khoibep.example.vn/actuator/health` |
| `GRAFANA_ADMIN_PASSWORD` | Mật khẩu đăng nhập Grafana, tài khoản `admin` |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | Lấy ở bước bot Telegram |
| `INGEST_USER` | `agent` |
| `INGEST_PASSWORD_HASH` | Kết quả của `caddy hash-password`, để trong dấu nháy đơn vì nó có ký tự `$` |

Mở `https://monitor.khoibep.example.vn`, đăng nhập `admin`. Dashboard **Khói Bếp** có các phần: tổng quan, máy chủ, container, ứng dụng, nghiệp vụ và log lỗi.

**Máy ứng dụng.** Bằng tài khoản `deploy`:

```bash
mkdir -p ~/agent && cd ~/agent
curl -fsSL https://github.com/Anpham120/restaurant-management-system/archive/refs/heads/main.tar.gz \
  | tar -xz --strip-components=3 restaurant-management-system-main/deploy/agent
cp .env.example .env && chmod 600 .env
nano .env                  # MONITOR_DOMAIN, INGEST_PASSWORD (mật khẩu gốc, không phải hash), APP_METRICS_TOKEN
docker compose up -d
docker compose logs -f     # không có dòng "level=error"
```

`APP_METRICS_TOKEN` phải giống giá trị trong `~/khoibep-rms/.env` và `~/khoibep-rms-staging/.env`. Lần đầu thêm biến đó vào hai tệp này, chạy `docker compose -f docker-compose.prod.yml up -d` trong mỗi thư mục để backend đọc giá trị mới.

Tệp `backup.prom` (thời điểm sao lưu thành công gần nhất) có từ lần sao lưu đầu sau khi deploy bản có P0-07. Chưa có tệp này thì khoảng 10 phút sau khi bật agent, Telegram nhận `BackupMissing` cho production. Muốn có ngay thì chạy `docker compose -f docker-compose.prod.yml exec backup sh /backup.sh now` trong mỗi thư mục.

**Thử báo động** trên staging:

```bash
cd ~/khoibep-rms-staging
docker compose -f docker-compose.prod.yml stop backend    # khoảng 3 phút sau Telegram nhận BackendDown, SiteDown
docker compose -f docker-compose.prod.yml start backend   # vài phút sau nhận tin đã ổn
```

**Khi máy công cụ chết** thì không còn ai báo. Nếu muốn chắc, đăng ký một dịch vụ kiểm tra uptime miễn phí (ví dụ UptimeRobot) gọi `https://monitor.khoibep.example.vn/api/health` mỗi 5 phút và báo qua email.

**Cập nhật cấu hình giám sát** sau khi `deploy/ops` hoặc `deploy/agent` trong repo đổi: tải lại thư mục như trên (tệp `.env` được giữ nguyên), rồi chạy `docker compose up -d && docker compose restart` để các container đọc lại tệp cấu hình.
