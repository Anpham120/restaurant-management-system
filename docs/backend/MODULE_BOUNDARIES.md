# Ranh giới module

Năm module nghiệp vụ là **package** trong cùng ứng dụng (`com.restaurant.core.<module>`), không phải service riêng. Mỗi bảng có **một** module chủ: chỉ module đó ghi bảng (qua JPA repository trong Service) và đặt API quản lý. Module khác cần dữ liệu thì gọi **interface ở package gốc** của module chủ (Java, không HTTP nội bộ), hoặc đọc bằng truy vấn chỉ-đọc khi màn hình/báo cáo cần nối nhiều bảng. Người phụ trách: [TEAM_ASSIGNMENT](TEAM_ASSIGNMENT.md).

Nguồn chính về kiến trúc hiện hành là [ARCHITECTURE](ARCHITECTURE.md): **nghiệp vụ nằm ở tầng Service Java**. Các hàm `rc_*` / `rpt_*` (PL/pgSQL) của bản thiết kế v4.1 đã bị migration `V3__xoa_ham_nghiep_vu_sql.sql` xóa; chỉ còn các hàm phục vụ trigger bảo vệ dữ liệu (`rc_guard_*`, `rc_raise`, `rc_forbid_*`, `rc_quota_used`…). Tài liệu [07](../dac-ta/07-kiem-toan-ham-rc.md) và phần hàm trong `docs/04`, `docs/10` là **lịch sử thiết kế**, không phải cách viết code mới.

Đặc tả bảng/cột: [02 §12](../dac-ta/02-danh-gia-database.md) · luồng từng chức năng: [03](../dac-ta/03-luong-du-lieu-chuc-nang.md).

| Module | Người phụ trách | Bảng chủ | API cho module khác (package gốc) |
| --- | --- | --- | --- |
| **employee** (11 bảng) | Nguyễn Quang Hiếu | `departments`, `employees`, `accounts`, `roles`, `permissions`, `account_roles`, `role_permissions`, `shifts`, `shift_assignments`, `attendances`, `attendance_adjustments` | — |
| **menu** (6 bảng) | Lê Anh | `menu_categories`, `menu_items`, `recipes`, `recipe_ingredients`, `daily_menus`, `daily_menu_items` | `DailyMenuLookup` (món đang bán, công thức); khai báo `QuotaUsageCounter` (order cài đặt) |
| **order** (6 bảng) | Phạm Duy An | `dining_tables`, `table_sessions`, `orders`, `order_batches`, `order_items`, `order_item_events` | `OrderBillingApi` (cho finance); khai báo `OrderInvoiceAccess` (finance cài đặt) |
| **inventory** (18 bảng) | Đỗ Tuấn Anh | `ingredients`, `kitchen_ingredient_types`, `stock_receipts`, `stock_receipt_items`, `inventory_batches`, `stock_issues`, `stock_issue_items`, `kitchen_preparations`, `kitchen_preparation_outputs`, `kitchen_batches`, `stock_reservations`, `stock_returns`, `stock_return_items`, `stock_adjustments`, `stock_adjustment_items`, `stock_counts`, `stock_count_items`, `stock_movements` | `KitchenStockAllocator` (giữ/trả nguyên liệu FEFO); khai báo `OrderItemStockFlags` (order cài đặt) |
| **finance** (7 bảng) — **hai chủ theo lớp** | Phạm Duy An: hóa đơn + thanh toán | `invoices`, `invoice_items`, `payment_requests`, `payments`, `payment_webhook_events` | — |
| | Bùi Đào Đức Anh: chi phí, đối soát, báo cáo | `operating_expenses`, `daily_cash_reconciliations` (+ chỉ đọc 5 bảng trên) | — |

Thành phần dùng chung **không phải module nghiệp vụ**: `security` (Nguyễn Quang Hiếu; cho module khác dùng `CurrentUser`), `common` (nhóm trưởng duyệt; module mở, mọi module dùng trực tiếp).

## Lớp trong `finance` thuộc ai (tránh hai người sửa cùng file)

| Phạm Duy An | Bùi Đào Đức Anh |
| --- | --- |
| `controller/InvoiceController`, `controller/PaymentController` | `controller/ExpenseController`, `controller/CashReconciliationController`, `controller/FinanceReportController` |
| `service/InvoiceService`, `service/PaymentService` và `service/impl/Invoice*`, `PaymentServiceImpl`, `QrTransferProcessor`, `OrderInvoiceAccessImpl` | `service/ExpenseService`, `service/CashReconciliationService`, `service/FinanceReportService` và các `…Impl` |
| `repository/Invoice*`, `repository/Payment*` | `repository/OperatingExpenseRepository`, `ExpenseQueryRepository`, `DailyCashReconciliationRepository`, `CashReconciliationQueryRepository`, `FinanceReportRepository` |
| `strategy/**`, `sepay/**` | — |
| `dto/response/InvoiceResponse`, `dto/request/StartPaymentRequest`, `dto/response/PaymentResponse`, `dto/request/ReasonRequest` | DTO đặt tên theo tính năng (`Expense…`, `CashReconciliation…`, `FinanceReport…`) |

Báo cáo cần thông tin thanh toán → đọc bảng trong `FinanceReportRepository` (truy vấn chỉ-đọc); **không** gọi hay sửa `PaymentRepository`.

## Quy tắc

1. **Nghiệp vụ viết trong Service Java.** Kiểm trạng thái, khóa dòng (`…ForUpdate`), tính tiền, giữ/trừ kho đều ở `service/impl`. Không viết hàm hay thủ tục SQL mới để chứa nghiệp vụ.
2. **Không ghi bảng của module khác.** Cần thay đổi → interface ở package gốc của module chủ (ví dụ order trả nguyên liệu qua `KitchenStockAllocator`, finance đóng đơn qua `OrderBillingApi`).
3. **Đọc chéo**: ưu tiên interface của module chủ; màn hình/báo cáo JOIN nhiều module được viết truy vấn chỉ-đọc (`…QueryRepository`, JdbcClient) trong module của mình, ghi chú bảng thuộc ai.
4. **Không import package con** (`controller`, `service`, `repository`, `entity`, `dto`) của module khác. `ModularityTest` (Spring Modulith) kiểm điều này và `allowedDependencies` trong `package-info.java`.
5. **Không vòng phụ thuộc** giữa hai module. Chiều cho phép: `menu` ← `inventory` ← `order` ← `finance`. Module phía trước cần dữ liệu của module phía sau thì khai báo interface và để module kia cài đặt (đảo phụ thuộc).
6. **Trigger trong DB là lưới an toàn cuối cùng**, không phải nơi viết nghiệp vụ: máy trạng thái, bảng chỉ-thêm (`stock_movements`, `payments`, `order_item_events`…), cân sổ kho. Service phải tự kiểm trước để trả lỗi rõ ràng; trigger báo lỗi nghĩa là Service còn thiếu kiểm tra.
7. Ứng dụng chạy bằng tài khoản thuộc role `rc_app` (không phải superuser, không có quyền DDL). Thay đổi schema chỉ qua migration Flyway (`src/main/resources/db/migration`), chạy bằng tài khoản quản trị.
