# Khói Bếp — Hệ thống quản lý nhà hàng (bản core)

Đồ án môn học: quản lý một quán lẩu nướng và cơm nhà ở Hà Nội, phục vụ tại bàn.
- **Gọi món:** phục vụ gọi món trên điện thoại, hoặc khách tự gọi bằng **QR trên bàn**, nhân viên xác nhận.
- **Bếp:** màn hình bếp cập nhật **theo thời gian thực**, khách thấy trạng thái từng món trên điện thoại.
- **Thanh toán:** tiền mặt, hoặc **chuyển khoản VietQR tự xác nhận** qua webhook SePay.
- **Quản lý:** kho nguyên liệu, báo cáo doanh thu, quản lý tài khoản nhân viên.
- **Nhân sự:** hồ sơ và mức lương, xếp ca, chấm công vào/ra ca, nghỉ phép, bảng lương tháng. Mỗi nhân viên có trang "Của tôi" để chấm công, xem lịch, xin nghỉ và xem phiếu lương.

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security + JWT, Data JPA, WebSocket STOMP), Flyway |
| Frontend | React 19, TypeScript, Vite, Ant Design, TanStack Query |
| CSDL | PostgreSQL 17 (thiết kế trước, **database-first**) |
| DevOps | Docker, Docker Compose, GitHub Actions, GHCR |

Tài liệu phân tích thiết kế: [docs-core/](docs-core/README.md). Thư mục [docs/](docs/README.md) là bản phân tích mở rộng, chỉ để tham khảo.

## Cấu trúc

```text
backend/        Spring Boot: API, WebSocket, Flyway migration, test
frontend/       React: màn hình nhân viên và trang khách /q/<mã-bàn>
deploy/         Compose cho máy chủ và mẫu .env
docs-core/      Tài liệu BA và thiết kế (bản nộp)
.github/        Pipeline CI/CD
```

## Chạy nhanh bằng Docker

Cần Docker Desktop.

```bash
docker compose up --build
```

- Ứng dụng: http://localhost:8080
- Swagger UI: http://localhost:8081/swagger-ui.html

Tài khoản demo (chỉ có khi `APP_DEMO_ACCOUNTS_ENABLED=true`, mật khẩu đều là `123456`):

| Tài khoản | Vai trò |
|---|---|
| `admin` | Quản trị |
| `quanly` | Quản lý |
| `phucvu` | Phục vụ |
| `bep` | Bếp |
| `thungan` | Thu ngân |

## Chạy để phát triển

```bash
docker compose up -d db
cd backend && APP_DEMO_ACCOUNTS_ENABLED=true ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Mở http://localhost:5173. Vite chuyển `/api` và `/ws` sang backend ở cổng 8080.

## Thử luồng khách quét QR

1. Đăng nhập `quanly` → **Bàn và QR** → **Xem QR** của một bàn → bấm đường link (hoặc quét bằng điện thoại).
2. Muốn dùng điện thoại thật, máy tính và điện thoại phải cùng Wi-Fi. Khi chạy thì đặt `APP_PUBLIC_BASE_URL=http://<IP-máy-tính>:8080`, để mã QR in ra có địa chỉ mà điện thoại mở được.
3. Khách gửi món → phục vụ (`phucvu`) thấy "chờ xác nhận" → xác nhận → bếp (`bep`) làm → trang khách tự cập nhật.

## Giả lập tiền về (webhook SePay)

Khi chưa nối SePay thật, tự gửi webhook bằng lệnh dưới. Thay `KBXXXXXXXX` bằng mã thanh toán hiện trên màn hình, và `245000` bằng số tiền. Mỗi lần gửi phải dùng một `id` mới.

```bash
curl -X POST http://localhost:8080/api/webhooks/sepay \
  -H "Authorization: Apikey dev-sepay-key" -H "Content-Type: application/json" \
  -d '{"id": 1001, "gateway": "Vietcombank", "content": "KBXXXXXXXX", "transferType": "in", "transferAmount": 245000}'
```

PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/webhooks/sepay `
  -Headers @{ Authorization = "Apikey dev-sepay-key" } -ContentType "application/json" `
  -Body '{"id": 1001, "gateway": "Vietcombank", "content": "KBXXXXXXXX", "transferType": "in", "transferAmount": 245000}'
```

Nối SePay thật:
1. Trên my.sepay.vn, tạo webhook trỏ tới `https://<tên-miền>/api/webhooks/sepay`, sự kiện "Có tiền vào", xác thực **API Key**.
2. Đặt cùng khoá đó vào biến `SEPAY_API_KEY`.
3. Nhập tài khoản nhận tiền ở màn hình **Cài đặt**.
4. Ở bước **Cảnh báo** khi tạo webhook, bật thông báo khi webhook lỗi. Máy chủ chỉ tự báo được khi SePay gọi tới mà bị lỗi; còn khi SePay không gọi tới được (sai tên miền, chứng chỉ hết hạn) thì chỉ SePay biết.

## Kiểm thử

```bash
node scripts/check-erd.mjs
cd backend && ./mvnw verify
cd frontend && npm run lint && npm test && npm run build
```

- `scripts/check-erd.mjs` so ERD trong [docs-core/07-erd.md](docs-core/07-erd.md) với các migration Flyway (database-first): bảng, cột, kiểu dữ liệu, khoá chính, khoá ngoại và `UNIQUE`. CI chạy lệnh này trước khi build backend.
- Test backend chạy với PostgreSQL thật qua Testcontainers, nên cần Docker đang chạy.
- `./mvnw verify` đo độ phủ bằng JaCoCo và báo lỗi khi test chạy tới dưới 70% số dòng. Báo cáo nằm ở `backend/target/site/jacoco/index.html`.
- Test frontend gồm cả test component (Testing Library), chạy trong trình duyệt giả lập jsdom.

Test E2E ([e2e/](e2e/tests/acceptance.spec.ts)) chạy kịch bản nghiệm thu trên trình duyệt thật, với cả ứng dụng dựng bằng Docker Compose:
- Khách gọi món qua QR, phục vụ xác nhận, bếp làm.
- Khách chuyển khoản, hệ thống tự xác nhận, bàn trống.
- Mỗi vai trò chỉ làm được việc của mình.

CI chạy test này ở mỗi PR. Chạy ở máy:

```bash
docker compose -p rms-e2e up -d --build
cd e2e && npm ci && npx playwright install chromium && npx playwright test
docker compose -p rms-e2e down -v
```

Tên project `rms-e2e` tách dữ liệu test khỏi dữ liệu của `docker compose up` thường. `down -v` xoá luôn dữ liệu test.

Kiểm thử tải ([perf/load-test.js](perf/load-test.js), NFR-02): 30 người dùng cùng lúc trong 2 phút. Đạt khi 95% request trả lời dưới 500 ms và dưới 1% request lỗi. Trên GitHub, chạy tay ở **Actions → Load test → Run workflow**. Chạy ở máy (k6 chạy trong Docker, không cần cài):

```bash
docker compose -p rms-perf up -d --build
# Nạp khoảng 6 tháng bán hàng, để đo trên lượng dữ liệu giống thật
docker compose -p rms-perf exec -T db psql -U rms -d rms -v ON_ERROR_STOP=1 < perf/seed-history.sql
docker run --rm -i -v "$PWD/perf:/perf" grafana/k6:2.3.0 run -e BASE_URL=http://host.docker.internal:8080 /perf/load-test.js
docker compose -p rms-perf down -v
```

Trên Windows, vài request có thể hiện `max` khoảng 55 giây. Đó là do đồng hồ máy ảo của Docker Desktop nhảy, không phải server chậm; p95 và số lỗi không bị ảnh hưởng.

## Nhánh và quy trình làm việc

```text
feature/<tên> ──PR──▶ develop ──PR──▶ main
                      (staging)       (production, cần duyệt)
```

| Nhánh | Dùng để | Khi push |
|---|---|---|
| `feature/<tên>` | Mỗi tính năng hoặc sửa lỗi một nhánh, tách từ `develop` | Mở PR vào `develop` thì CI chạy test |
| `develop` | Gộp tính năng, cả nhóm thử trên staging | Test → image tag `develop` → tự deploy **staging** |
| `main` | Bản chạy thật, chỉ nhận PR từ `develop` | Test → image tag `latest` → deploy **production** sau khi được duyệt |

Làm một tính năng:

```bash
git checkout develop
git pull
git checkout -b feature/ten-tinh-nang
git push -u origin feature/ten-tinh-nang
```

Sau đó mở pull request vào `develop` trên GitHub.

Cài đặt trên GitHub (chỉ làm một lần):
1. **Settings → Rules → Rulesets**, áp cho `main` và `develop` (đã bật):
   - Bắt buộc đi qua pull request, không push thẳng, không force push.
   - Bắt buộc các check xanh: *Backend - build and test*, *Frontend - lint, test, build*, *E2E - acceptance scenario*, *CodeQL - Java*, *CodeQL - TypeScript*.
   - Không bật *Require branches to be up to date*, để các PR xếp chồng lên nhau không phải cập nhật lại liên tục.
   - **Settings → General → Pull Requests:** đã bật *Allow auto-merge* (PR tự merge khi CI xanh nếu bấm *Enable auto-merge*) và *Automatically delete head branches*.
2. **Settings → Environments:**
   - Tạo `staging`, không cần duyệt.
   - Tạo `production`, bật *Required reviewers*.
   - Mỗi environment có secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` và variable `PUBLIC_URL`.
3. **Settings → Variables:** đặt `DEPLOY_ENABLED=true` khi đã có máy chủ. Chưa đặt thì pipeline chỉ test và build image, không deploy.
4. **Settings → Code security:** bật *Dependabot alerts* và *Dependabot security updates*, để GitHub báo và tự mở PR vá khi thư viện có lỗ hổng.

## CI/CD

File [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml):

1. Pull request vào `develop` hoặc `main` thì chạy test backend (PostgreSQL thật qua Testcontainers), frontend (lint, test, build), và kiểm tra cấu hình giám sát kèm test quy tắc cảnh báo.
2. Push vào `develop` hoặc `main`: [Jenkins](Jenkinsfile) trên máy công cụ deploy chính. Job `cd-gate` của GitHub Actions chờ trạng thái `jenkins/deploy` trên commit, và chỉ tự build, deploy khi Jenkins chưa cấu hình, không trả lời, hoặc im lặng 5 phút ([tài liệu 09 mục 9.6](docs-core/09-kien-truc-va-cicd.md)). Ai build thì cũng đẩy 2 image lên GHCR, tag là mã commit, kèm `develop` hoặc `latest`.
3. Deploy, bằng cùng script [deploy/deploy.sh](deploy/deploy.sh) cho cả hai bên:
   - `develop` lên máy chủ staging, thư mục `~/khoibep-rms-staging`.
   - `main` lên production, thư mục `~/khoibep-rms`.
   - Mỗi thư mục có một file `.env` làm từ [deploy/.env.example](deploy/.env.example). Staging dùng `HTTP_PORT=8080` nếu chạy chung máy với production.
4. Sau khi deploy, pipeline gọi `/actuator/health` để kiểm tra.
5. Quay lại bản cũ: tab **Actions → Rollback → Run workflow**, chọn môi trường và commit.
6. Chạy lại test bằng tay: tab **Actions → CI/CD → Run workflow**.
7. Quét lỗ hổng, kết quả ở tab **Security**:
   - [`codeql.yml`](.github/workflows/codeql.yml) phân tích mã Java và TypeScript ở mỗi PR và mỗi tuần.
   - Sau khi build image, Trivy quét 2 image.
   - [`dependabot.yml`](.github/dependabot.yml) mở PR cập nhật thư viện mỗi tuần vào `develop`.

## Sao lưu và khôi phục

Trên máy chủ, dịch vụ `backup` sao lưu CSDL mỗi đêm vào thư mục `backups/` cạnh file compose (mặc định 3 giờ sáng, giữ 7 bản). Pipeline chép sẵn [deploy/backup.sh](deploy/backup.sh) và [deploy/restore.sh](deploy/restore.sh) lên máy chủ. Chạy các lệnh sau trong `~/khoibep-rms` (hoặc `~/khoibep-rms-staging`):

```bash
# Sao lưu ngay, ví dụ trước khi deploy bản lớn
docker compose -f docker-compose.prod.yml exec backup sh /backup.sh now
ls -lh backups/

# Thử khôi phục vào CSDL tạm: in số dòng từng bảng, không đụng dữ liệu thật
sh restore.sh --check backups/rms-2026-10-05-0300.dump

# Khôi phục thật: thay CSDL đang chạy bằng bản sao lưu, có hỏi xác nhận
sh restore.sh backups/rms-2026-10-05-0300.dump
```

Nên chép thư mục `backups/` ra ngoài máy chủ định kỳ (ổ khác, Google Drive...): mất máy chủ là mất luôn bản sao lưu nằm trên đó.

## Log, số liệu và cảnh báo

Trên máy chủ, backend ghi log dạng JSON, mỗi dòng một đối tượng theo chuẩn ECS. Số liệu của Spring Boot Actuator chỉ ADMIN xem được, và Nginx không mở chúng ra ngoài, nên phải hỏi từ trong mạng Docker. Chạy trong `~/khoibep-rms` (hoặc `~/khoibep-rms-staging`):

```bash
# Chỉ xem các dòng lỗi
docker compose -f docker-compose.prod.yml logs backend | grep '"level":"ERROR"'

# Lấy token của một tài khoản ADMIN (cần jq)
read -r -p "Tài khoản: " U; read -rs -p "Mật khẩu: " P; echo
TOKEN=$(jq -n --arg u "$U" --arg p "$P" '{username: $u, password: $p}' \
  | curl -s -H "Content-Type: application/json" -d @- https://<tên-miền>/api/auth/login | jq -r .token)

# Số lần webhook SePay bị từ chối vì sai khoá API. Bỏ phần sau /actuator/metrics để xem danh sách số liệu
docker compose -f docker-compose.prod.yml exec frontend wget -qO- --header "Authorization: Bearer $TOKEN" \
  "http://backend:8080/actuator/metrics/http.server.requests?tag=uri:/api/webhooks/sepay&tag=status:401"
```

Ở máy dev, backend mở cổng 8081 nên hỏi thẳng được: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/actuator/metrics`.

Webhook SePay lỗi 3 lần liên tiếp thì màn hình thu ngân hiện cảnh báo, và log có một dòng `ERROR`. Một webhook hợp lệ tới thì cảnh báo tự tắt.

Trên máy chủ thật, số liệu và log còn được gom về máy công cụ: dashboard Grafana và cảnh báo qua Telegram ([tài liệu 09 mục 9.8](docs-core/09-kien-truc-va-cicd.md)). Cách dựng ở [tài liệu 11 mục 11.10](docs-core/11-trien-khai-van-hanh.md).
