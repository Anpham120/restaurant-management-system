# Restaurant Core (RMS) – Tài liệu phân tích

Hệ thống quản lý nhà hàng bản Core: Spring Boot REST API, WebSocket, Web responsive. Tài liệu gồm 5 phân hệ, mỗi phân hệ có sơ đồ use case, sơ đồ hoạt động, ERD và README; cả 5 dùng chung một cơ sở dữ liệu.

| STT | Phân hệ | Thư mục |
| --- | --- | --- |
| 1 | Quản lý nhân viên | [01-quan-ly-nhan-vien](01-quan-ly-nhan-vien/README.md) |
| 2 | Quản lý thực đơn | [02-quan-ly-thuc-don](02-quan-ly-thuc-don/README.md) |
| 3 | Quản lý đơn hàng | [03-quan-ly-don-hang](03-quan-ly-don-hang/README.md) |
| 4 | Quản lý kho & nguyên liệu | [04-quan-ly-kho](04-quan-ly-kho/README.md) |
| 5 | Quản lý thu chi & đối soát cuối ngày | [05-quan-ly-thu-chi-doi-soat](05-quan-ly-thu-chi-doi-soat/README.md) |

## ERD tổng thể

![ERD tổng thể](erd-tong-the.png)

Toàn bộ 37 bảng và các quan hệ của 5 phân hệ trên một sơ đồ. Mỗi bảng thuộc đúng một phân hệ sở hữu: từ điển dữ liệu đầy đủ nằm ở README phân hệ đó; các ERD từng phân hệ vẽ lại cùng bảng (đủ cột) khi cần thể hiện liên kết.

| Phân hệ | Bảng sở hữu |
| --- | --- |
| 1 – Nhân viên | `departments`, `employees`, `accounts`, `roles`, `permissions`, `account_roles`, `role_permissions`, `shifts`, `shift_assignments`, `attendances`, `attendance_adjustments` |
| 2 – Thực đơn | `menu_categories`, `menu_items`, `recipes`, `recipe_ingredients`, `daily_menus`, `daily_menu_items` |
| 3 – Đơn hàng | `dining_tables`, `table_sessions`, `orders`, `order_batches`, `order_items`, `order_item_events`, `invoices`, `payment_requests`, `payments`, `payment_webhook_events` |
| 4 – Kho | `ingredients`, `stock_receipts`, `stock_receipt_items`, `inventory_batches`, `stock_movements`, `stock_reservations`, `stock_counts`, `stock_count_items` |
| 5 – Thu chi & đối soát | `operating_expenses`, `daily_cash_reconciliations` |

**Quy ước chung:** tên bảng và cột `snake_case`, tên bảng số nhiều; khóa chính `id BIGINT`; khóa ngoại `<thực thể>_id BIGINT`; chỉ khóa ngoại mới có hậu tố `_id`; thời điểm `TIMESTAMPTZ`, ngày `DATE`, giờ `TIME`; tiền `DECIMAL(12,2)`, số lượng `DECIMAL(12,3)`, không dùng `FLOAT` hay `DOUBLE`.

## Render lại sơ đồ

Cần Java và Graphviz. `plantuml.jar` có sẵn trong extension VS Code `jebbs.plantuml` (thư mục `%USERPROFILE%\.vscode\extensions\jebbs.plantuml-<phiên bản>\`). Chạy từ thư mục gốc dự án:

```powershell
java "-DPLANTUML_LIMIT_SIZE=8192" -jar "<đường dẫn plantuml.jar>" -charset UTF-8 -failfast2 -tpng docs\erd-tong-the.puml
```

Các phân hệ render tương tự với `docs\<thư mục phân hệ>\*.puml`. Style đen trắng dùng chung nằm ở `_style.puml`.
