# Restaurant Core

Backend Spring Boot (Java 21) cho hệ thống quản lý nhà hàng, chạy trên database PostgreSQL v4.1 đã đóng băng trong [`docs/`](docs/README.md).

- Kiến trúc và quy ước: [`docs/backend/`](docs/backend/ARCHITECTURE.md) — [ARCHITECTURE](docs/backend/ARCHITECTURE.md) · [MODULE_BOUNDARIES](docs/backend/MODULE_BOUNDARIES.md) · [DEVELOPMENT_GUIDE](docs/backend/DEVELOPMENT_GUIDE.md) · [TEAM_ASSIGNMENT](docs/backend/TEAM_ASSIGNMENT.md) · [API_CONVENTIONS](docs/backend/API_CONVENTIONS.md)
- Database (schema, hàm `rc_*`, phân quyền, báo cáo, kiểm thử): [`docs/README.md`](docs/README.md)

## Chạy nhanh

Cần JDK 21 và Docker. Maven đi kèm (`./mvnw`).

```bash
cp .env.example .env                 # điền DB_ADMIN_PASSWORD, DB_APP_PASSWORD, JWT_SECRET
docker compose up -d --build         # PostgreSQL 17 (localhost:5433) + app (localhost:8081)
curl http://localhost:8081/actuator/health

# tài khoản Manager đầu tiên (database mới)
docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
    -v username=manager -v full_name='Quản lý' -v password='mat-khau-tam' < docker/bootstrap-manager.sql
curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"manager","password":"mat-khau-tam"}'
```

Chạy app ngoài Docker, test, Flyway và cách thêm API: [DEVELOPMENT_GUIDE](docs/backend/DEVELOPMENT_GUIDE.md).

```bash
./mvnw test        # unit + integration test (Testcontainers, cần Docker)
```

## Phân công

| Thành viên | Phụ trách |
| --- | --- |
| Phạm Duy An (nhóm trưởng) | Quản lý đơn hàng: bàn, phiên, gọi món, bếp, hóa đơn, thanh toán CASH/QR, webhook SePay |
| Bùi Đào Đức Anh | Quản lý tài chính: chi phí, đối soát tiền mặt, báo cáo doanh thu |
| Đỗ Tuấn Anh | Quản lý kho |
| Lê Anh | Quản lý thực đơn |
| Nguyễn Quang Hiếu | Quản lý nhân viên, Spring Security/JWT/RBAC |

Chi tiết: [TEAM_ASSIGNMENT](docs/backend/TEAM_ASSIGNMENT.md) · hợp đồng API: [API_CONTRACT](docs/backend/API_CONTRACT.md).

## Cấu trúc

```text
src/main/java/com/restaurant/core/
├── common/      config (CORS) · aspect (PerformanceLoggingAspect) · exception · response
├── security/    JWT, SecurityConfig, CurrentUser                         (Nguyễn Quang Hiếu)
├── employee/    đăng nhập                                                 (Nguyễn Quang Hiếu)
├── order/       bàn, phiên, đơn, gọi món, bếp, phục vụ, hủy, bỏ về         (Phạm Duy An)
├── finance/     Invoice*, Payment*, strategy/ (CASH/QR), sepay/ (webhook)  (Phạm Duy An)
│                Expense*, CashReconciliation*, FinanceReport*              (Bùi Đào Đức Anh — chưa có)
│   (menu/ — Lê Anh, inventory/ — Đỗ Tuấn Anh: tạo khi bắt đầu)
src/main/resources/  application.yml, application-dev.yml, application-prod.yml, db/migration/
docker/              script khởi tạo login DB, tạo Manager đầu tiên
compose.yaml, Dockerfile, .env.example
```
