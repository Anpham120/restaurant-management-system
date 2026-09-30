# Bếp Nhà & Nướng — Hệ thống quản lý nhà hàng (bản core)

Đồ án môn học: quản lý một nhà hàng ở Hà Nội.
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

Khi chưa nối SePay thật, tự gửi webhook bằng lệnh dưới. Thay `BNNXXXXXXXX` bằng mã thanh toán hiện trên màn hình, và `245000` bằng số tiền. Mỗi lần gửi phải dùng một `id` mới.

```bash
curl -X POST http://localhost:8080/api/webhooks/sepay \
  -H "Authorization: Apikey dev-sepay-key" -H "Content-Type: application/json" \
  -d '{"id": 1001, "gateway": "Vietcombank", "content": "BNNXXXXXXXX", "transferType": "in", "transferAmount": 245000}'
```

PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/webhooks/sepay `
  -Headers @{ Authorization = "Apikey dev-sepay-key" } -ContentType "application/json" `
  -Body '{"id": 1001, "gateway": "Vietcombank", "content": "BNNXXXXXXXX", "transferType": "in", "transferAmount": 245000}'
```

Nối SePay thật:
1. Trên my.sepay.vn, tạo webhook trỏ tới `https://<tên-miền>/api/webhooks/sepay`, sự kiện "Có tiền vào", xác thực **API Key**.
2. Đặt cùng khoá đó vào biến `SEPAY_API_KEY`.
3. Nhập tài khoản nhận tiền ở màn hình **Cài đặt**.

## Kiểm thử

```bash
node scripts/check-erd.mjs
cd backend && ./mvnw verify
cd frontend && npm run lint && npm test && npm run build
```

- `scripts/check-erd.mjs` so ERD trong [docs-core/07-erd.md](docs-core/07-erd.md) với các migration Flyway (database-first). CI chạy lệnh này trước khi build backend.
- Test backend chạy với PostgreSQL thật qua Testcontainers, nên cần Docker đang chạy.

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
1. **Settings → Rules → Rulesets**, áp cho `main` và `develop`:
   - Bắt buộc đi qua pull request, không push thẳng.
   - Bắt buộc 2 check xanh: *Backend - build and test* và *Frontend - lint, test, build*.
2. **Settings → Environments:**
   - Tạo `staging`, không cần duyệt.
   - Tạo `production`, bật *Required reviewers*.
   - Mỗi environment có secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` và variable `PUBLIC_URL`.
3. **Settings → Variables:** đặt `DEPLOY_ENABLED=true` khi đã có máy chủ. Chưa đặt thì pipeline chỉ test và build image, không deploy.
4. **Settings → Code security:** bật *Dependabot alerts* và *Dependabot security updates*, để GitHub báo và tự mở PR vá khi thư viện có lỗ hổng.

## CI/CD

File [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml):

1. Pull request vào `develop` hoặc `main` thì chạy test backend (PostgreSQL thật qua Testcontainers) và frontend (lint, test, build).
2. Push vào `develop` hoặc `main` thì build 2 image, đẩy lên GHCR. Tag là mã commit, kèm `develop` hoặc `latest`.
3. Deploy:
   - `develop` lên máy chủ staging, thư mục `~/bnn-rms-staging`.
   - `main` lên production, thư mục `~/bnn-rms`.
   - Mỗi thư mục có một file `.env` làm từ [deploy/.env.example](deploy/.env.example). Staging dùng `HTTP_PORT=8080` nếu chạy chung máy với production.
4. Sau khi deploy, pipeline gọi `/actuator/health` để kiểm tra.
5. Quay lại bản cũ: chạy lại job deploy của lần chạy tốt gần nhất.
6. Chạy lại test bằng tay: tab **Actions → CI/CD → Run workflow**.
7. Quét lỗ hổng, kết quả ở tab **Security**:
   - [`codeql.yml`](.github/workflows/codeql.yml) phân tích mã Java và TypeScript ở mỗi PR và mỗi tuần.
   - Sau khi build image, Trivy quét 2 image.
   - [`dependabot.yml`](.github/dependabot.yml) mở PR cập nhật thư viện mỗi tuần vào `develop`.
