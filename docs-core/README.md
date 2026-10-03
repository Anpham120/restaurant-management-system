# Tài liệu phân tích và thiết kế — KB-RMS (bản core)

Bộ tài liệu nộp cho đồ án. Cấu trúc theo repo tham khảo [nazrul-ancala/Restaurant-Management-System](https://github.com/nazrul-ancala/Restaurant-Management-System), nội dung viết cho một nhà hàng ở Hà Nội.

| # | Tài liệu | Tương ứng repo tham khảo | Nội dung |
|---|---|---|---|
| 1 | [Tầm nhìn dự án](01-tam-nhin-du-an.md) | 01-project-vision | Vấn đề, mục tiêu, người dùng, phạm vi, tiêu chí nghiệm thu |
| 2 | [Yêu cầu](02-yeu-cau.md) | 02-requirements | 113 yêu cầu chức năng (21 nhóm, trong đó 4 nhóm nhân sự làm sau bản core), 13 yêu cầu phi chức năng |
| 3 | [User stories](03-user-stories.md) | 03-user-stories | 41 story theo vai trò (7 story nhân sự), kèm tiêu chí chấp nhận |
| 4 | [Quy trình nghiệp vụ](04-quy-trinh-nghiep-vu.md) | 04-business-workflow | Trước và sau; 10 quy trình P1 đến P10 |
| 5 | [Quy tắc nghiệp vụ](05-quy-tac-nghiep-vu.md) | 05-business-rules | 48 quy tắc (6 quy tắc nhân sự), ghi rõ nơi kiểm tra trong mã |
| 6 | [Mô hình miền](06-mo-hinh-mien.md) | 06-domain-model | Sơ đồ lớp, sơ đồ trạng thái, ma trận quyền |
| 7 | [Cơ sở dữ liệu](07-erd.md) | 07-erd | ERD 35 bảng (7 bảng nhân sự ở mục 7.5), ràng buộc, index (database-first) |
| 8 | [Thiết kế hệ thống](08-thiet-ke-he-thong.md) | 08-system-design | REST API, kênh realtime, màn hình, sơ đồ tuần tự |
| 9 | [Kiến trúc và CI/CD](09-kien-truc-va-cicd.md) | 09-system-architecture | Kiến trúc, công nghệ, bảo mật, triển khai, pipeline, kiểm thử |
| 10 | [Kế hoạch phát triển tiếp](10-ke-hoach-phat-trien.md) | — | Backlog theo giai đoạn, lịch 8 sprint, quy trình làm tính năng, rủi ro, phân công theo service |
| 11 | [Triển khai và vận hành](11-trien-khai-van-hanh.md) | — | Dựng máy ứng dụng và máy công cụ, HTTPS, GitHub, SePay, sao lưu, giám sát và cảnh báo Telegram, cập nhật và quay lại bản cũ |
| 12 | [Thiết kế mở rộng](12-thiet-ke-mo-rong.md) | — | Nhiều chi nhánh, chạy khi mất mạng: chỉ thiết kế (P4-05) |
| 13 | [Kế hoạch và báo cáo kiểm thử](13-kiem-thu.md) | — | Mức kiểm thử, kết quả, 56 test case theo AC của US-01 → US-19, kiểm thử tải, lỗi đã tìm thấy |
| 14 | [Hướng dẫn sử dụng](14-huong-dan-su-dung.md) | — | Từng màn hình theo vai trò: phục vụ, bếp, thu ngân, khách, quản lý, quản trị; 26 ảnh chụp |
| 15 | [Kịch bản demo 10 phút](15-kich-ban-demo.md) | — | Chia thời gian và người nói, chuẩn bị dữ liệu, bốn phần demo, xử lý sự cố, dàn ý 9 slide |

Bản phân tích mở rộng (chuỗi quán, máy chủ tại quán, chạy offline) ở [`../docs/`](../docs/README.md), chỉ để tham khảo.

## Truy vết

Mỗi nhóm yêu cầu nối tới story, quy tắc, nơi hiện thực và test tự động. Test nằm trong `backend/src/test/java/vn/khoibep/rms/`.

| Yêu cầu | User story | Quy tắc | API và màn hình | Test tự động |
|---|---|---|---|---|
| FR-01 Đăng nhập, phân quyền | US-01, US-03, US-36 | BR-01, 02, 03, 31, 41 | `/api/auth/*`; `/login` | `AuthIntegrationTest`, `RateLimitIntegrationTest`, `TokenRevocationIntegrationTest` |
| FR-02 Nhân viên | US-02, US-03 | BR-01, 03, 48 | `/api/employees`; `/admin/employees` | `AuthIntegrationTest`, `InitialAdminInitializerTest` |
| FR-03 Thực đơn | US-04, US-13 | BR-05, 06, 18 | `/api/categories`, `/api/menu-items`; `/admin/menu`, `/kitchen` | `OrderFlowIntegrationTest`, `GuestQrIntegrationTest` |
| FR-04 Bàn và QR | US-05, US-31 | BR-04, 09, 36 | `/api/tables`, `/api/orders/{id}/tables`; `/admin/tables`, `/tables` | `GuestQrIntegrationTest`, `OrderFlowIntegrationTest`, `MoveTablesIntegrationTest` |
| FR-05 Gọi món | US-08, US-10, US-11 | BR-04 → BR-08 | `/api/orders`, `/api/order-items`; `/orders/:id` | `OrderFlowIntegrationTest`, `ItemStatusTest` |
| FR-06 Khách gọi qua QR, gọi nhân viên | US-09, US-14, US-15, US-27 | BR-09, 10, 11, 29, 30 | `/api/public/*`, `/api/service-requests`, `/topic/guest/*`; `/q/:token`, nút chuông đầu trang | `GuestQrIntegrationTest`, `StompAuthInterceptorTest`, `ServiceRequestIntegrationTest`, `RateLimitIntegrationTest` |
| FR-07 Màn hình bếp, âm báo | US-09, US-10, US-12 | BR-07, 10, 28 | `/api/kitchen/items`, `/topic/staff`; `/kitchen`, `/tables`, `/orders/:id` | `OrderFlowIntegrationTest`, `KitchenAlertIntegrationTest` |
| FR-08 Thanh toán | US-16 → US-19, US-28, US-30, US-38 | BR-12 → BR-17, 32, 33, 35, 43 | `/api/orders/{id}/payments/*`, `/api/orders/{id}/payments`, `/api/orders/{id}/adjustments`, `/api/adjustments/*`, `/api/bank-transactions/*`, `/api/webhooks/sepay`; `/cashier`, `/orders/:id` | `PaymentIntegrationTest`, `PaymentReferenceTest`, `WebhookAlertIntegrationTest`, `AdjustmentIntegrationTest`, `SplitBillIntegrationTest`, `BillSlip.test.tsx`, `adjustment.test.ts`, `split.test.ts` |
| FR-09 Kho, nhà cung cấp, phiếu nhập, định lượng | US-06, US-32, US-33 | BR-19, 20, 37, 38 | `/api/inventory-items`, `/api/suppliers`, `/api/goods-receipts`, `/api/recipes`, `/api/inventory-usage`; `/admin/inventory`, `/admin/menu` | `InventoryIntegrationTest`, `PurchaseIntegrationTest`, `StockUsageIntegrationTest` |
| FR-10 Báo cáo | US-07, US-35 | BR-21, 40 | `/api/reports/summary`, `/api/reports/gross-profit`, `/api/reports/exceptions`; `/admin/reports` | `ReportIntegrationTest`, `ProfitReportIntegrationTest`, `report.test.ts` |
| FR-11 Cài đặt | US-12 | BR-14, 28 | `/api/settings`; `/admin/settings` | `KitchenAlertIntegrationTest` (ngưỡng món chờ lâu), phần còn lại kiểm tra thủ công |
| FR-12 Hồ sơ nhân viên | US-20 | BR-22 | `/api/employees/{id}/profile`, `/resign`; `/admin/employees` | `EmployeeProfileIntegrationTest` |
| FR-13 Xếp ca, nghỉ phép | US-21, US-22 | BR-23, 24 | `/api/work-shifts`, `/api/schedule`, `/api/leave-requests`, `/api/me/*`; `/admin/schedule`, `/admin/leave`, `/me` | `ScheduleIntegrationTest`, `LeaveIntegrationTest` |
| FR-14 Chấm công | US-23, US-24 | BR-25, 27 | `/api/me/attendance/*`, `/api/attendance`; `/me`, `/admin/attendance` | `AttendanceIntegrationTest` |
| FR-15 Tính lương | US-25, US-26 | BR-26, 27 | `/api/payrolls`, `/api/payslips`, `/api/me/payslips`; `/admin/payroll`, `/me` | `PayCalculatorTest`, `PayrollIntegrationTest` |
| FR-16 Nhật ký thao tác | US-29 | BR-34 | `/api/audit-entries`; `/admin/audit` | `AuditIntegrationTest`, `audit.test.ts` |
| FR-17 Ca và két | US-34 | BR-39 | `/api/cash-shifts`; `/cashier`, `/admin/cash-shifts` | `CashShiftIntegrationTest` |
| FR-18 Đặt bàn và cọc | US-37 | BR-21, 42 | `/api/reservations`; `/reservations`, `/cashier` | `ReservationIntegrationTest` |
| FR-19 Khách hàng | US-39 | BR-44 | `/api/customers`, `/api/orders/{id}/customer`; `/admin/customers`, `/cashier` | `CustomerIntegrationTest`, `customer.test.ts` |
| FR-20 Hoá đơn điện tử | US-40 | BR-45, BR-46 | `/api/tax-categories`, `/api/einvoices`, `/api/orders/{id}/einvoice`; `/admin/einvoices`, `/admin/menu`, `/cashier` | `EInvoiceIntegrationTest`, `EInvoiceMathTest`, `XlsxTest`, `einvoice.test.ts`, `tax.test.ts` |
| FR-21 Đơn app giao hàng | US-41 | BR-47 | `/api/orders`, `/api/orders/{id}/handover`; `/tables`, `/orders/:id`, `/kitchen`, `/admin/menu` | `AppOrderIntegrationTest`, `appOrder.test.ts` |

Kết quả lần chạy gần nhất:
- Backend: 197 test, 0 lỗi (JUnit, PostgreSQL 17 qua Testcontainers), gồm 10 quy tắc kiến trúc của `ArchitectureTest`.
- Frontend: 83 test (gồm test component), lint và kiểm tra kiểu sạch.
- Độ phủ backend (JaCoCo): 91,9% số dòng (2630/2861), tối thiểu 70%, đo trên Jenkins ngày 03/10/2026.
- Giám sát: 17 quy tắc cảnh báo, mỗi quy tắc có test `promtool` báo khi có sự cố và im khi bình thường.
- `scripts/check-erd.mjs`, chạy trong CI: 23 migration, 35 bảng, 297 cột, 111 khoá, 0 lệch.
- E2E: kịch bản nghiệm thu chạy xanh trong CI. Kiểm thử tải (k6, NFR-02): p95 23,8 ms, 0% lỗi với 30 người dùng và 6 tháng dữ liệu. Báo cáo đầy đủ ở [tài liệu 13](13-kiem-thu.md).
