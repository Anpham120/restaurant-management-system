# Restaurant Core

Backend Spring Boot (Java 25) cho hệ thống quản lý nhà hàng, chạy trên database PostgreSQL v4.1 đã đóng băng trong [`docs/`](docs/README.md).

- Kiến trúc và quy ước: [`docs/backend/`](docs/backend/ARCHITECTURE.md) — [ARCHITECTURE](docs/backend/ARCHITECTURE.md) · [MODULE_BOUNDARIES](docs/backend/MODULE_BOUNDARIES.md) · [DEVELOPMENT_GUIDE](docs/backend/DEVELOPMENT_GUIDE.md) · [TEAM_ASSIGNMENT](docs/backend/TEAM_ASSIGNMENT.md) · [API_CONVENTIONS](docs/backend/API_CONVENTIONS.md)
- Database (schema, trigger bảo vệ dữ liệu, phân quyền; đặc tả nghiệp vụ gốc): [`docs/README.md`](docs/README.md). Nghiệp vụ chạy ở tầng Service Java; thay đổi DB sau baseline nằm ở `src/main/resources/db/migration`.

## Chạy nhanh

Cần JDK 25 và Docker. Maven đi kèm (`./mvnw`).

```bash
cp .env.example .env                 # điền DB_ADMIN_PASSWORD, DB_APP_PASSWORD, JWT_SECRET
docker compose up -d --build         # PostgreSQL 17 (localhost:5433) + app (localhost:8081)
curl http://localhost:8081/actuator/health

# tài khoản Manager đầu tiên (database mới). Tự đặt mật khẩu mạnh; read -s để không lưu vào lịch sử lệnh.
read -s -p "Mật khẩu manager: " PW; echo
docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
    -v username=manager -v full_name='Quản lý' -v password="$PW" < docker/bootstrap-manager.sql
unset PW
# Đăng nhập lần đầu bị buộc đổi mật khẩu. Sai 5 lần liên tiếp thì tên đăng nhập bị khóa tạm 15 phút.
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

## Cấu trúc thư mục

```text
src/main/java/com/restaurant/core/      Backend: mỗi package con là một module (Spring Modulith)
├── common/      dùng chung: config, aspect, exception, response, idempotency, time, validation
├── security/    JWT, SecurityConfig, CurrentUser, đăng nhập, đổi mật khẩu   (Nguyễn Quang Hiếu)
├── employee/    nhân viên, tài khoản, ca làm, chấm công                     (Nguyễn Quang Hiếu)
├── menu/        nhóm món, món, công thức, thực đơn ngày, hạn mức             (Lê Anh)
├── order/       bàn, phiên, đơn, gọi món, bếp, phục vụ, hủy, khách bỏ về    (Phạm Duy An)
├── inventory/   nguyên liệu, nhập/xuất/kiểm kê, sơ chế, hoàn trả, báo cáo   (Đỗ Tuấn Anh)
└── finance/     hóa đơn, thanh toán (strategy/, sepay/)                    (Phạm Duy An)
                 khoản chi, đối soát tiền mặt, báo cáo doanh thu            (Bùi Đào Đức Anh)
   Mỗi module: controller/ · service/ (interface) + service/impl/ · repository/ · entity/ · dto/request|response/
   Interface ở package gốc của module là API cho module khác (OrderBillingApi, DailyMenuLookup, ...).
src/main/resources/     application*.yml, db/migration/ (Flyway, sau baseline docs/database/04)
src/test/java/...       test theo đúng module: <module>/<Tên>IT.java (integration, Testcontainers),
                        IntegrationTestBase, TestData, ModularityTest ở package gốc
frontend/               React + Vite; src/pages/<module>/ theo cùng module với backend (frontend/README.md)
docs/
├── dac-ta/             đặc tả nghiệp vụ: luồng chính, đánh giá database, luồng dữ liệu, kiểm toán hàm (01, 02, 03, 07)
├── database/           schema v4.1 (04), test, ACL, báo cáo SQL (05, 06, 08–12): baseline mà Kustomize, compose, test nạp
├── backend/            kiến trúc, ranh giới module, hướng dẫn, phân công, hợp đồng API
├── devops/             quy trình triển khai, Jenkins rootless, cấu hình nginx
├── erd/                ERD theo phân hệ (draw.io, DBML)
└── bao-cao/, bao-cao-ltnc/   báo cáo Triển khai phần mềm, báo cáo Lập trình nâng cao
deploy/
├── k8s/                Kustomize + Argo CD (base, overlays staging/prod, platform)
├── monitoring/         Prometheus, Alertmanager, Grafana, Loki, blackbox; máy 1, 2 tự đồng bộ (sync/)
├── jenkins/            Jenkins rootless (casc, Dockerfile)
└── seaweedfs/          kho S3 nhận WAL sao lưu
scripts/                ci/, release/, backup/, ops/ (scripts/README.md)
docker/                 khởi tạo login DB, tạo Manager đầu tiên
compose.yaml, compose.staging.yaml, Dockerfile, Jenkinsfile, .env.example
```
