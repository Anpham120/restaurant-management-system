# 11. Triển khai và vận hành

Tài liệu này là các bước dựng một máy chủ chạy cả **production** (nhánh `main`) và **staging** (nhánh `develop`), rồi vận hành hằng ngày. Kiến trúc và pipeline ở [tài liệu 9](09-kien-truc-va-cicd.md). Các mục ứng với việc P0-01 → P0-04 và P5-03 trong [tài liệu 10](10-ke-hoach-phat-trien.md).

Trong các lệnh dưới đây, thay `khoibep.example.vn` bằng tên miền thật, `<IP>` bằng địa chỉ máy chủ.

## 11.1 Chuẩn bị

| Thứ cần có | Gợi ý |
|---|---|
| Máy chủ | VPS Ubuntu 24.04 hoặc 22.04, **2 GB RAM** (1 GB chỉ đủ khi chạy một môi trường), 20 GB ổ đĩa, IP tĩnh |
| Tên miền | Hai bản ghi A trỏ về `<IP>`: `khoibep.example.vn` và `staging.khoibep.example.vn`. Dùng Cloudflare thì để **DNS only** (mây xám), để Caddy tự lấy chứng chỉ |
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
