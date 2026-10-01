# Tài liệu phân tích và thiết kế — KB-RMS (bản core)

Bộ tài liệu nộp cho đồ án. Cấu trúc theo repo tham khảo [nazrul-ancala/Restaurant-Management-System](https://github.com/nazrul-ancala/Restaurant-Management-System), nội dung viết cho một nhà hàng ở Hà Nội.

| # | Tài liệu | Tương ứng repo tham khảo | Nội dung |
|---|---|---|---|
| 1 | [Tầm nhìn dự án](01-tam-nhin-du-an.md) | 01-project-vision | Vấn đề, mục tiêu, người dùng, phạm vi, tiêu chí nghiệm thu |
| 2 | [Yêu cầu](02-yeu-cau.md) | 02-requirements | 76 yêu cầu chức năng (16 nhóm, trong đó 4 nhóm nhân sự làm sau bản core), 11 yêu cầu phi chức năng |
| 3 | [User stories](03-user-stories.md) | 03-user-stories | 30 story theo vai trò (7 story nhân sự), kèm tiêu chí chấp nhận |
| 4 | [Quy trình nghiệp vụ](04-quy-trinh-nghiep-vu.md) | 04-business-workflow | Trước và sau; 5 quy trình P1 đến P5 |
| 5 | [Quy tắc nghiệp vụ](05-quy-tac-nghiep-vu.md) | 05-business-rules | 35 quy tắc (6 quy tắc nhân sự), ghi rõ nơi kiểm tra trong mã |
| 6 | [Mô hình miền](06-mo-hinh-mien.md) | 06-domain-model | Sơ đồ lớp, sơ đồ trạng thái, ma trận quyền |
| 7 | [Cơ sở dữ liệu](07-erd.md) | 07-erd | ERD 21 bảng (7 bảng nhân sự ở mục 7.5), ràng buộc, index (database-first) |
| 8 | [Thiết kế hệ thống](08-thiet-ke-he-thong.md) | 08-system-design | REST API, kênh realtime, màn hình, sơ đồ tuần tự |
| 9 | [Kiến trúc và CI/CD](09-kien-truc-va-cicd.md) | 09-system-architecture | Kiến trúc, công nghệ, bảo mật, triển khai, pipeline, kiểm thử |
| 10 | [Kế hoạch phát triển tiếp](10-ke-hoach-phat-trien.md) | — | Backlog theo giai đoạn, lịch 8 sprint, quy trình làm tính năng, rủi ro, phân công theo service |

Bản phân tích mở rộng (chuỗi quán, máy chủ tại quán, chạy offline) ở [`../docs/`](../docs/README.md), chỉ để tham khảo.

## Truy vết

Mỗi nhóm yêu cầu nối tới story, quy tắc, nơi hiện thực và test tự động. Test nằm trong `backend/src/test/java/vn/khoibep/rms/`.

| Yêu cầu | User story | Quy tắc | API và màn hình | Test tự động |
|---|---|---|---|---|
| FR-01 Đăng nhập, phân quyền | US-01, US-03 | BR-01, 02, 03, 31 | `/api/auth/*`; `/login` | `AuthIntegrationTest`, `RateLimitIntegrationTest` |
| FR-02 Nhân viên | US-02, US-03 | BR-01, 03 | `/api/employees`; `/admin/employees` | `AuthIntegrationTest` |
| FR-03 Thực đơn | US-04, US-13 | BR-05, 06, 18 | `/api/categories`, `/api/menu-items`; `/admin/menu`, `/kitchen` | `OrderFlowIntegrationTest`, `GuestQrIntegrationTest` |
| FR-04 Bàn và QR | US-05 | BR-04, 09 | `/api/tables`; `/admin/tables`, `/tables` | `GuestQrIntegrationTest`, `OrderFlowIntegrationTest` |
| FR-05 Gọi món | US-08, US-10, US-11 | BR-04 → BR-08 | `/api/orders`, `/api/order-items`; `/orders/:id` | `OrderFlowIntegrationTest`, `ItemStatusTest` |
| FR-06 Khách gọi qua QR, gọi nhân viên | US-09, US-14, US-15, US-27 | BR-09, 10, 11, 29, 30 | `/api/public/*`, `/api/service-requests`, `/topic/guest/*`; `/q/:token`, nút chuông đầu trang | `GuestQrIntegrationTest`, `StompAuthInterceptorTest`, `ServiceRequestIntegrationTest`, `RateLimitIntegrationTest` |
| FR-07 Màn hình bếp, âm báo | US-09, US-10, US-12 | BR-07, 10, 28 | `/api/kitchen/items`, `/topic/staff`; `/kitchen`, `/tables`, `/orders/:id` | `OrderFlowIntegrationTest`, `KitchenAlertIntegrationTest` |
| FR-08 Thanh toán | US-16 → US-19, US-28, US-30 | BR-12 → BR-17, 32, 33, 35 | `/api/orders/{id}/payments/*`, `/api/orders/{id}/payment`, `/api/orders/{id}/adjustments`, `/api/adjustments/*`, `/api/bank-transactions/*`, `/api/webhooks/sepay`; `/cashier`, `/orders/:id` | `PaymentIntegrationTest`, `PaymentReferenceTest`, `WebhookAlertIntegrationTest`, `AdjustmentIntegrationTest`, `BillSlip.test.tsx`, `adjustment.test.ts` |
| FR-09 Kho | US-06 | BR-19, 20 | `/api/inventory-items`; `/admin/inventory` | `InventoryIntegrationTest` |
| FR-10 Báo cáo | US-07 | BR-21 | `/api/reports/summary`; `/admin/reports` | `ReportIntegrationTest` |
| FR-11 Cài đặt | US-12 | BR-14, 28 | `/api/settings`; `/admin/settings` | `KitchenAlertIntegrationTest` (ngưỡng món chờ lâu), phần còn lại kiểm tra thủ công |
| FR-12 Hồ sơ nhân viên | US-20 | BR-22 | `/api/employees/{id}/profile`, `/resign`; `/admin/employees` | `EmployeeProfileIntegrationTest` |
| FR-13 Xếp ca, nghỉ phép | US-21, US-22 | BR-23, 24 | `/api/work-shifts`, `/api/schedule`, `/api/leave-requests`, `/api/me/*`; `/admin/schedule`, `/admin/leave`, `/me` | `ScheduleIntegrationTest`, `LeaveIntegrationTest` |
| FR-14 Chấm công | US-23, US-24 | BR-25, 27 | `/api/me/attendance/*`, `/api/attendance`; `/me`, `/admin/attendance` | `AttendanceIntegrationTest` |
| FR-15 Tính lương | US-25, US-26 | BR-26, 27 | `/api/payrolls`, `/api/payslips`, `/api/me/payslips`; `/admin/payroll`, `/me` | `PayCalculatorTest`, `PayrollIntegrationTest` |
| FR-16 Nhật ký thao tác | US-29 | BR-34 | `/api/audit-entries`; `/admin/audit` | `AuditIntegrationTest`, `audit.test.ts` |

Kết quả lần chạy gần nhất:
- Backend: 119 test, 0 lỗi (JUnit, PostgreSQL 17 qua Testcontainers), gồm 10 quy tắc kiến trúc của `ArchitectureTest`.
- Frontend: 43 test (gồm test component), lint và kiểm tra kiểu sạch.
- Độ phủ backend (JaCoCo): 86,5% số dòng (1495/1728), tối thiểu 70%.
- `scripts/check-erd.mjs`, chạy trong CI: 12 migration, 21 bảng, 179 cột, 62 khoá, 0 lệch.
- E2E: kịch bản nghiệm thu chạy xanh trong CI. Kiểm thử tải (k6, NFR-02): p95 19,5 ms với 30 người dùng và 6 tháng dữ liệu.
