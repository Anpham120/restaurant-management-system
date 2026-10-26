# Ranh giới module

Năm module nghiệp vụ là **package** trong cùng ứng dụng (`com.restaurant.core.<module>`), không phải service riêng. Mỗi bảng có **một** module chủ (được viết SQL đọc/ghi và đặt API quản lý). Module khác cần dữ liệu thì gọi **Service public** của module chủ (Java, không HTTP nội bộ) hoặc đọc bằng truy vấn chỉ-đọc khi màn hình/báo cáo cần nối nhiều bảng. Người phụ trách: [TEAM_ASSIGNMENT](TEAM_ASSIGNMENT.md).

Đặc tả bảng/cột: [02 §12](../02-danh-gia-database.md) · luồng từng chức năng: [03](../03-luong-du-lieu-chuc-nang.md) · hàm: [07](../07-kiem-toan-ham-rc.md).

| Module | Người phụ trách | Bảng chủ | Hàm `rc_*` / `rpt_*` |
| --- | --- | --- | --- |
| **employee** (11 bảng) | Nguyễn Quang Hiếu | `departments`, `employees`, `accounts`, `roles`, `permissions`, `account_roles`, `role_permissions`, `shifts`, `shift_assignments`, `attendances`, `attendance_adjustments` | — (ghi trực tiếp, `rc_app` được cấp) |
| **menu** (6 bảng) | Lê Anh | `menu_categories`, `menu_items`, `recipes`, `recipe_ingredients`, `daily_menus`, `daily_menu_items` | đọc `rc_quota_used` |
| **order** (6 bảng) | Phạm Duy An | `dining_tables`, `table_sessions`, `orders`, `order_batches`, `order_items`, `order_item_events` | `rc_open_session`, `rc_close_empty_session`, `rc_submit_batch`, `rc_accept_item`, `rc_reject_item`, `rc_start_cooking`, `rc_finish_dish`, `rc_serve_item`, `rc_cancel_item`, `rc_cancel_item_exception`, `rc_reallocate_item`, `rc_abandon_session` |
| **inventory** (18 bảng) | Đỗ Tuấn Anh | `ingredients`, `kitchen_ingredient_types`, `stock_receipts`, `stock_receipt_items`, `inventory_batches`, `stock_issues`, `stock_issue_items`, `kitchen_preparations`, `kitchen_preparation_outputs`, `kitchen_batches`, `stock_reservations`, `stock_returns`, `stock_return_items`, `stock_adjustments`, `stock_adjustment_items`, `stock_counts`, `stock_count_items`, `stock_movements` | `rc_create_stock_receipt/_issue/_count`, `rc_confirm_receipt`, `rc_issue_stock`, `rc_receive_issue`, `rc_cancel_issue`, `rc_confirm_preparation`, `rc_confirm_return`, `rc_confirm_adjustment`, `rc_confirm_count`, `rc_mark_batch_unusable`; `rpt_raw_stock`, `rpt_handover_and_wip`, `rpt_kitchen_stock`, `rpt_unusable_lots`, `rpt_stock_history`, `rpt_preparation_yield` |
| **finance** (7 bảng) — **hai chủ theo lớp** | Phạm Duy An: hóa đơn + thanh toán | `invoices`, `invoice_items`, `payment_requests`, `payments`, `payment_webhook_events` | `rc_checkout_invoice`, `rc_reopen_invoice`, `rc_waive_invoice`, `rc_pay_cash`, `rc_start_qr`, `rc_cancel_payment_request`, `rc_process_verified_qr` (login `rc_webhook`) |
| | Bùi Đào Đức Anh: chi phí, đối soát, báo cáo | `operating_expenses`, `daily_cash_reconciliations` (+ chỉ đọc 5 bảng trên) | `rc_reconcile_cash`; `rpt_revenue_by_day`, `rpt_revenue_by_item`, `rpt_unpaid_closures`, `rpt_cash_reconciliation` |

Thành phần dùng chung **không phải module**: `security` (Nguyễn Quang Hiếu), `common` (nhóm trưởng duyệt).

## Lớp trong `finance` thuộc ai (tránh hai người sửa cùng file)

| Phạm Duy An | Bùi Đào Đức Anh |
| --- | --- |
| `controller/InvoiceController`, `controller/PaymentController` | `controller/ExpenseController`, `controller/CashReconciliationController`, `controller/FinanceReportController` |
| `service/InvoiceService`, `service/PaymentService` | `service/ExpenseService`, `service/CashReconciliationService`, `service/FinanceReportService` |
| `repository/InvoiceRepository`, `repository/PaymentRepository` | `repository/ExpenseRepository`, `repository/CashReconciliationRepository`, `repository/FinanceReportRepository` |
| `strategy/**`, `sepay/**` | — |
| `dto/InvoiceResponse`, `dto/PaymentRequest`, `dto/PaymentResponse`, `dto/ReasonRequest` | DTO mới đặt tên theo tính năng (`Expense…`, `CashReconciliation…`, `…Report…`) |

Báo cáo cần thông tin thanh toán → đọc bảng/`rpt_*` trong repository của Bùi Đào Đức Anh; **không** gọi hay sửa `PaymentRepository`.

## Quy tắc

1. **Hàm `rc_*` xuyên module vẫn là MỘT lời gọi.** `rc_abandon_session` chạm món, kho, hóa đơn, thanh toán, bàn; `rc_accept_item` đặt giữ nguyên liệu (bảng inventory) — đều thuộc Order và không bị tách thành các bước Java.
2. **Không ghi bảng của module khác.** Cần thay đổi → Service của module chủ hoặc hàm `rc_*` tương ứng.
3. **Đọc chéo**: ưu tiên Service public của module chủ; màn hình/báo cáo JOIN nhiều module được viết truy vấn chỉ-đọc trong module của mình, ghi chú bảng thuộc ai.
4. **Không import `repository` hay DTO nội bộ của module khác**, trừ khi hai chủ module thống nhất.
5. **Không vòng phụ thuộc** giữa hai Service.
6. Bảng nhạy cảm (bán hàng, tiền, sổ kho, số dư, chứng từ đã xác nhận) **không có quyền ghi trực tiếp** với `rc_app` — `INSERT/UPDATE` sẽ nhận `42501` (`DB_PERMISSION`).
