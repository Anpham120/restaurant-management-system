# Tài liệu phân tích và thiết kế — BNN-RMS (bản core)

Bộ tài liệu nộp cho đồ án. Cấu trúc theo repo tham khảo [nazrul-ancala/Restaurant-Management-System](https://github.com/nazrul-ancala/Restaurant-Management-System), nội dung viết cho một nhà hàng ở Hà Nội.

| # | Tài liệu | Tương ứng repo tham khảo | Nội dung |
|---|---|---|---|
| 1 | [Tầm nhìn dự án](01-tam-nhin-du-an.md) | 01-project-vision | Vấn đề, mục tiêu, người dùng, phạm vi, tiêu chí nghiệm thu |
| 2 | [Yêu cầu](02-yeu-cau.md) | 02-requirements | 44 yêu cầu chức năng (11 nhóm), 10 yêu cầu phi chức năng |
| 3 | [User stories](03-user-stories.md) | 03-user-stories | 19 story theo vai trò, kèm tiêu chí chấp nhận |
| 4 | [Quy trình nghiệp vụ](04-quy-trinh-nghiep-vu.md) | 04-business-workflow | Trước và sau; 5 quy trình P1 đến P5 |
| 5 | [Quy tắc nghiệp vụ](05-quy-tac-nghiep-vu.md) | 05-business-rules | 21 quy tắc, ghi rõ nơi kiểm tra trong mã |
| 6 | [Mô hình miền](06-mo-hinh-mien.md) | 06-domain-model | Sơ đồ lớp, sơ đồ trạng thái, ma trận quyền |
| 7 | [Cơ sở dữ liệu](07-erd.md) | 07-erd | ERD 11 bảng, ràng buộc, index (database-first) |
| 8 | [Thiết kế hệ thống](08-thiet-ke-he-thong.md) | 08-system-design | REST API, kênh realtime, màn hình, sơ đồ tuần tự |
| 9 | [Kiến trúc và CI/CD](09-kien-truc-va-cicd.md) | 09-system-architecture | Kiến trúc, công nghệ, bảo mật, triển khai, pipeline, kiểm thử |

Bản phân tích mở rộng (chuỗi quán, máy chủ tại quán, chạy offline) ở [`../docs/`](../docs/README.md), chỉ để tham khảo.

## Truy vết

Mỗi nhóm yêu cầu nối tới story, quy tắc, nơi hiện thực và test tự động. Test nằm trong `backend/src/test/java/vn/bnn/rms/`.

| Yêu cầu | User story | Quy tắc | API và màn hình | Test tự động |
|---|---|---|---|---|
| FR-01 Đăng nhập, phân quyền | US-01, US-03 | BR-01, 02, 03 | `/api/auth/*`; `/login` | `AuthIntegrationTest` |
| FR-02 Nhân viên | US-02, US-03 | BR-01, 03 | `/api/employees`; `/admin/employees` | `AuthIntegrationTest` |
| FR-03 Thực đơn | US-04, US-13 | BR-05, 06, 18 | `/api/categories`, `/api/menu-items`; `/admin/menu`, `/kitchen` | `OrderFlowIntegrationTest`, `GuestQrIntegrationTest` |
| FR-04 Bàn và QR | US-05 | BR-04, 09 | `/api/tables`; `/admin/tables`, `/tables` | `GuestQrIntegrationTest`, `OrderFlowIntegrationTest` |
| FR-05 Gọi món | US-08, US-10, US-11 | BR-04 → BR-08 | `/api/orders`, `/api/order-items`; `/orders/:id` | `OrderFlowIntegrationTest`, `ItemStatusTest` |
| FR-06 Khách gọi qua QR | US-09, US-14, US-15 | BR-09, 10, 11 | `/api/public/*`, `/topic/guest/*`; `/q/:token` | `GuestQrIntegrationTest`, `StompAuthInterceptorTest` |
| FR-07 Màn hình bếp | US-12 | BR-07 | `/api/kitchen/items`, `/topic/staff`; `/kitchen` | `OrderFlowIntegrationTest` |
| FR-08 Thanh toán | US-16 → US-19 | BR-12 → BR-17 | `/api/orders/{id}/payments/*`, `/api/webhooks/sepay`; `/cashier` | `PaymentIntegrationTest`, `PaymentReferenceTest` |
| FR-09 Kho | US-06 | BR-19, 20 | `/api/inventory-items`; `/admin/inventory` | `InventoryIntegrationTest` |
| FR-10 Báo cáo | US-07 | BR-21 | `/api/reports/summary`; `/admin/reports` | `ReportIntegrationTest` |
| FR-11 Cài đặt | — | BR-14 | `/api/settings`; `/admin/settings` | Kiểm tra thủ công |

Kết quả lần chạy gần nhất:
- Backend: 64 test, 0 lỗi (JUnit, PostgreSQL 17 qua Testcontainers).
- Frontend: 5 test, lint và kiểm tra kiểu sạch.
- Script đối chiếu ERD với `V1__init.sql`: 11 bảng, 87 cột, 0 lệch.
