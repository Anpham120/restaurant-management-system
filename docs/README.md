# Restaurant Core (RMS) – Tài liệu phân tích

Hệ thống quản lý nhà hàng bản Core: Spring Boot REST API, WebSocket, Web responsive. Tài liệu gồm 5 phân hệ, mỗi phân hệ có sơ đồ use case, sơ đồ hoạt động, ERD và README; cả 5 dùng chung một cơ sở dữ liệu.

| STT | Phân hệ | Thư mục |
| --- | --- | --- |
| 1 | Quản lý nhân viên | [01-quan-ly-nhan-vien](01-quan-ly-nhan-vien/README.md) |
| 2 | Quản lý thực đơn | [02-quan-ly-thuc-don](02-quan-ly-thuc-don/README.md) |
| 3 | Quản lý đơn hàng | [03-quan-ly-don-hang](03-quan-ly-don-hang/README.md) |
| 4 | Quản lý kho & nguyên liệu | [04-quan-ly-kho](04-quan-ly-kho/README.md) |
| 5 | Quản lý tài chính | [05-quan-ly-tai-chinh](05-quan-ly-tai-chinh/README.md) |

**Báo cáo tổng hợp:** [bao-cao-tong-hop.md](bao-cao-tong-hop.md) gộp toàn bộ sơ đồ trong một tài liệu theo thứ tự: bảng xác định actor, [sơ đồ phân cấp chức năng](phan-cap-chuc-nang.png), sơ đồ theo từng nhóm chức năng (use case, hoạt động, ERD), ERD tổng thể.

## ERD tổng thể

![ERD tổng thể](erd-tong-the.png)

Toàn bộ 37 bảng và 53 khóa ngoại của 5 phân hệ trên một sơ đồ, cộng hai liên kết tổng hợp (mũi tên nét đứt) của đối soát tiền mặt. Mỗi bảng thuộc đúng một phân hệ sở hữu: từ điển dữ liệu đầy đủ nằm ở README phân hệ đó; ERD từng phân hệ vẽ lại bảng của phân hệ khác ở dạng tham chiếu (viền nét đứt, chỉ khóa và cột liên quan) khi cần thể hiện liên kết.

| Phân hệ | Bảng sở hữu |
| --- | --- |
| 1 – Nhân viên | `departments`, `employees`, `accounts`, `roles`, `permissions`, `account_roles`, `role_permissions`, `shifts`, `shift_assignments`, `attendances`, `attendance_adjustments` |
| 2 – Thực đơn | `menu_categories`, `menu_items`, `recipes`, `recipe_ingredients`, `daily_menus`, `daily_menu_items` |
| 3 – Đơn hàng | `dining_tables`, `table_sessions`, `orders`, `order_batches`, `order_items`, `order_item_events`, `invoices`, `payment_requests`, `payments`, `payment_webhook_events` |
| 4 – Kho | `ingredients`, `stock_receipts`, `stock_receipt_items`, `inventory_batches`, `stock_movements`, `stock_reservations`, `stock_counts`, `stock_count_items` |
| 5 – Tài chính | `operating_expenses`, `daily_cash_reconciliations` |

**Quy ước chung:** cơ sở dữ liệu PostgreSQL; tên bảng và cột `snake_case`, tên bảng số nhiều; khóa chính `id BIGINT`; khóa ngoại `<thực thể>_id BIGINT`; chỉ khóa ngoại mới có hậu tố `_id`; thời điểm `TIMESTAMPTZ` (múi giờ vận hành `Asia/Ho_Chi_Minh`), ngày `DATE`, giờ `TIME`; tiền `DECIMAL(12,2)`, số lượng `DECIMAL(12,3)`, không dùng `FLOAT` hay `DOUBLE`.

**Quy ước sơ đồ:**

- Use case: khung nét liền là ranh giới hệ thống (actor đứng ngoài); khung nét đứt chỉ nhóm chức năng, không phải hệ thống riêng; actor nối use case bằng đường liên kết không mũi tên; chỉ dùng «include» khi bước chung luôn được thực hiện và «extend» khi hành vi thêm xảy ra có điều kiện.
- Activity: làn bơi theo actor và "Hệ thống"; dữ liệu sai thì báo lỗi để người dùng sửa, chỉ lưu khi hợp lệ.
- ERD: bảng của phân hệ đang mô tả vẽ viền liền, đủ cột và ràng buộc; bảng của phân hệ khác vẽ viền nét đứt, chỉ hiện khóa và các cột phân hệ đó dùng (định nghĩa đầy đủ ở phân hệ sở hữu và ERD tổng thể). `*` là cột bắt buộc; `PK`, `FK`, `UK` là khóa chính, khóa ngoại, duy nhất; ràng buộc nằm dưới đường chấm trong bảng, ghi dạng `UK (...)`, `PARTIAL UK (...) WHERE ...`, `FK (...) → bảng (...)` (khóa ngoại ghép), `CHECK ...`. Đường nối có chân chim là khóa ngoại thật, bản số theo NOT NULL và UNIQUE của cột khóa ngoại; mũi tên nét đứt là nguồn tổng hợp số liệu, nhãn ghi "không phải FK". Mọi khóa ngoại `ON DELETE RESTRICT`.
- Sơ đồ không có khung chú giải ở cuối; ký hiệu giải thích ở mục này, ghi chú nghiệp vụ nằm ở README phân hệ.

**Lược đồ PostgreSQL** (cần PostgreSQL 15 trở lên):

- Phân hệ 1: [schema-postgresql.sql](01-quan-ly-nhan-vien/schema-postgresql.sql) viết tay, bộ kiểm thử [test-postgresql.sql](01-quan-ly-nhan-vien/test-postgresql.sql); chi tiết ở mục 7 của [README phân hệ 1](01-quan-ly-nhan-vien/README.md).
- Phân hệ 2 đến 5: [database/schema-phan-he-2-5.sql](database/schema-phan-he-2-5.sql) (26 bảng) sinh từ ERD tổng thể và các đoạn SQL trong README phân hệ bằng `node docs\tools\gen-schema.js`; không sửa tay, sửa ERD hoặc README rồi sinh lại. Mỗi ghi chú ràng buộc trên ERD thành một ràng buộc PostgreSQL thực thi được (UNIQUE, chỉ mục duy nhất một phần, CHECK, khóa ngoại đơn và ghép, trigger).
- Kiểm thử liên phân hệ 1–5: [database/test-lien-phan-he.sql](database/test-lien-phan-he.sql), chạy sau hai tệp lược đồ trên.

## Tập giá trị trạng thái

Mọi cột trạng thái, phương thức, loại lưu `VARCHAR` kèm CHECK tập giá trị trong PostgreSQL; backend Spring Boot ánh xạ bằng enum Java cùng tên giá trị với `@Enumerated(EnumType.STRING)`, không lưu số thứ tự. Bảng dưới khớp tuyệt đối ghi chú `CHECK ... IN (...)` của ERD, từ điển dữ liệu README phân hệ và lược đồ SQL; [check-docs.js](tools/check-docs.js) kiểm tra tự động, sơ đồ hoạt động và use case chỉ được dùng các mã này. Không bổ sung giá trị nào ngoài nghiệp vụ đã chốt.

| Enum Java dự kiến | Cột | Giá trị |
| --- | --- | --- |
| `EmployeeWorkStatus` | `employees.work_status` | `ACTIVE`, `ON_LEAVE`, `RESIGNED` |
| `AccountStatus` | `accounts.account_status` | `ACTIVE`, `LOCKED`, `SUSPENDED`, `DISABLED` |
| `ShiftAssignmentStatus` | `shift_assignments.status` | `SCHEDULED`, `CANCELLED` |
| `AttendanceStatus` | `attendances.attendance_status`, `attendance_adjustments.old_status`, `attendance_adjustments.new_status` | `PRESENT`, `ABSENT`, `DIFFERENT_HOURS` |
| `DailyMenuStatus` | `daily_menus.status` | `DRAFT`, `PUBLISHED`, `CLOSED` |
| `DiningTableStatus` | `dining_tables.status` | `AVAILABLE`, `OCCUPIED` |
| `TableSessionStatus` | `table_sessions.status` | `OPEN`, `CLOSED` |
| `OrderStatus` | `orders.status` | `OPEN`, `CLOSED`, `CANCELLED` |
| `OrderItemStatus` | `order_items.status`, `order_item_events.from_status`, `order_item_events.to_status` | `PENDING`, `ACCEPTED`, `IN_KITCHEN`, `COOKING`, `READY`, `CANCELLED`, `REJECTED` |
| `InvoiceStatus` | `invoices.status` | `DRAFT`, `PAYMENT_PENDING`, `PAID` |
| `PaymentMethod` | `payment_requests.method`, `payments.method` | `QR`, `CASH` |
| `PaymentRequestStatus` | `payment_requests.status` | `REQUESTED`, `APPROVED`, `WAITING_TRANSFER`, `WAITING_CASH_HANDOVER`, `PAID`, `CANCELLED` |
| `WebhookProcessingResult` | `payment_webhook_events.processing_result` | `PROCESSED`, `MISMATCH`, `AFTER_CANCEL` |
| `StockReceiptStatus` | `stock_receipts.status` | `DRAFT`, `CONFIRMED` |
| `StockMovementType` | `stock_movements.movement_type` | `RECEIPT`, `CONSUMPTION`, `ADJUSTMENT` |
| `StockReservationStatus` | `stock_reservations.status` | `ACTIVE`, `CONSUMED`, `RELEASED` |
| `ReservationReleaseReason` | `stock_reservations.release_reason` | `ITEM_CANCELLED`, `ITEM_EDITED`, `LOT_UNAVAILABLE` |
| `StockCountStatus` | `stock_counts.status` | `DRAFT`, `CONFIRMED` |
| `ExpenseCategory` | `operating_expenses.category` | `INGREDIENT`, `SUPPLIES`, `TRANSPORT`, `OTHER` |
| `ExpensePaymentMethod` | `operating_expenses.payment_method` | `CASH`, `TRANSFER` |
| `FundSource` | `operating_expenses.fund_source` | `COUNTER`, `OTHER` |
| `ExpenseStatus` | `operating_expenses.status` | `RECORDED`, `VOIDED` |
| `ReconciliationStatus` | `daily_cash_reconciliations.status` | `CURRENT`, `SUPERSEDED` |
| `MenuAvailability` | suy ra khi đọc, không lưu (phân hệ 2) | `AVAILABLE`, `UNAVAILABLE` |
| `UnavailableReason` | suy ra khi đọc, không lưu (phân hệ 2) | `PAUSED`, `SOLD_OUT`, `OUT_OF_STOCK` |

Cột chữ không thuộc nhóm này: `employees.gender` (tài liệu chưa chốt tập giá trị nên không ràng buộc), `roles.role_code`, `permissions.permission_code` (dữ liệu danh mục trong bảng), `ingredients.base_unit`, `stock_receipt_items.input_unit` (đơn vị đo).

## Kiểm tra nhất quán tài liệu

Chạy từ thư mục gốc dự án (cần Node.js 18 trở lên, không cài thêm gói):

```powershell
node docs\tools\check-docs.js
```

Công cụ [check-docs.js](tools/check-docs.js) đọc 5 ERD phân hệ, ERD tổng thể, sơ đồ use case, sơ đồ hoạt động, README, báo cáo tổng hợp và tệp SQL. Về trạng thái và PostgreSQL: mọi cột trạng thái có CHECK tập giá trị trong ERD, tập giá trị khớp bảng "Tập giá trị trạng thái", từ điển dữ liệu và lược đồ phân hệ 1; sơ đồ hoạt động và use case chỉ dùng mã trạng thái đã khai báo; lược đồ phân hệ 2–5 khớp kết quả sinh từ ERD và README; truy vấn mẫu có tham số trong README có nguyên văn trong bộ kiểm thử liên phân hệ. Về use case: bảng use case trong README khớp sơ đồ (mã, tên, actor) và số "Phân hệ có N use case"; báo cáo tổng hợp khớp README (bảng theo nhóm, bảng thống kê, cột use case của từng actor). Về ERD: mỗi bảng thuộc đúng một phân hệ, ERD tổng thể khớp định nghĩa gốc; khóa chính, khóa ngoại và khóa ngoại ghép trỏ tới bảng, cột có thật và cùng kiểu; mỗi đường chân chim ứng với một khóa ngoại thật và bản số khớp NOT NULL, UNIQUE; mũi tên nét đứt ghi rõ không phải FK; từ điển dữ liệu README khớp ERD; ảnh `.png` render từ đúng nguồn `.puml` hiện tại (so nguồn nhúng trong ảnh, không dựa vào giờ sửa tệp); tên bảng, cột, trạng thái dùng trong sơ đồ hoạt động có thật. Công cụ còn tìm cụm bảng rời trong từng ERD và phân loại: thiếu liên kết khóa ngoại thật (lỗi), nối bằng liên kết tổng hợp hoặc khai báo độc lập hợp lệ (`' check-erd: doc-lap bang_a, bang_b : lý do` trong `.puml`), hoặc chỉ nối được qua bảng chưa vẽ (cảnh báo kèm đường gợi ý). Lệnh trả mã 1 khi có lỗi.

## Kiểm thử PostgreSQL

Hai bộ kiểm thử chạy lại được, mỗi bộ trên một cơ sở dữ liệu trống, toàn bộ trong một transaction kết thúc bằng `ROLLBACK`; mỗi ca nêu SQLSTATE và tên ràng buộc phải từ chối, có ca thất bại thì lệnh trả mã khác 0.

Trên PostgreSQL server (15 trở lên):

```powershell
psql -v ON_ERROR_STOP=1 -d rms_test_ph1 -f docs\01-quan-ly-nhan-vien\schema-postgresql.sql -f docs\01-quan-ly-nhan-vien\test-postgresql.sql
psql -v ON_ERROR_STOP=1 -d rms_test_lien -f docs\01-quan-ly-nhan-vien\schema-postgresql.sql -f docs\database\schema-phan-he-2-5.sql -f docs\database\test-lien-phan-he.sql
```

Không có server thì dùng [run-pg-tests.mjs](tools/run-pg-tests.mjs) chạy cả hai bộ trên PGlite (PostgreSQL biên dịch WebAssembly, chạy trong Node.js; cài gói `@electric-sql/pglite` ở thư mục bất kỳ):

```powershell
node docs\tools\run-pg-tests.mjs --pglite <thư mục>\node_modules\@electric-sql\pglite
```

| Bộ kiểm thử | Lược đồ nạp | Kết quả gần nhất |
| --- | --- | --- |
| Phân hệ 1, ba múi giờ phiên (UTC+7, UTC, America/New_York) | 11 bảng, 13 khóa ngoại, 21 CHECK, 1 EXCLUDE, 1 trigger | 96/96 ca đạt ở cả ba múi giờ |
| Liên phân hệ 1–5 | 37 bảng, 56 khóa ngoại, 32 UNIQUE, 8 chỉ mục duy nhất một phần, 95 CHECK, 3 trigger | 135/135 ca đạt |

Bộ liên phân hệ gồm: khóa ngoại ghép công thức – món và lô – dòng phiếu; nhiều lô cùng nguyên liệu trong một phiếu nhập và FEFO; phiên gắn cố định một bàn; tiền mặt và tiền thừa (khách đưa thiếu, tiền thừa sai, số đếm lệch, chưa kiểm đếm, QR không chịu ràng buộc tiền mặt); chống ghi trùng webhook, khoản thanh toán, khoản chi (`request_key`, một khoản chi còn hiệu lực cho mỗi phiếu nhập, hủy rồi ghi lại vẫn giữ lịch sử); đối soát bất biến; một ca giá trị ngoài tập cho mỗi cột trạng thái của phân hệ 2–5 (phân hệ 1 nằm trong bộ riêng); truy vấn mẫu README; xóa bị chặn. Đã thử làm hỏng lược đồ (vô hiệu từng ràng buộc mới) để chắc mỗi ràng buộc có ca bắt lỗi.

Giới hạn: kết quả trên là PostgreSQL 18.3 chạy qua PGlite 0.5.8 (một kết nối, trong Node.js); chưa chạy trên server cài riêng và chưa kiểm thử đồng thời nhiều kết nối (khóa dòng `FOR UPDATE`, hai giao dịch tranh nhau). `UNIQUE NULLS NOT DISTINCT` cần PostgreSQL 15 trở lên.

## Render lại sơ đồ

Cần Java và Graphviz. `plantuml.jar` có sẵn trong extension VS Code `jebbs.plantuml` (thư mục `%USERPROFILE%\.vscode\extensions\jebbs.plantuml-<phiên bản>\`). Chạy từ thư mục gốc dự án:

```powershell
java "-DPLANTUML_LIMIT_SIZE=8192" -jar "<đường dẫn plantuml.jar>" -charset UTF-8 -failfast2 -tpng "docs\*\*.puml" docs\erd-tong-the.puml docs\phan-cap-chuc-nang.puml
```

Một lệnh render toàn bộ 50 sơ đồ (48 sơ đồ phân hệ, ERD tổng thể và sơ đồ phân cấp chức năng); `-DPLANTUML_LIMIT_SIZE=8192` cần cho ERD tổng thể vì ảnh rộng hơn giới hạn mặc định. Style đen trắng dùng chung nằm ở `_style.puml`; mọi ảnh `.png` phải render lại sau khi sửa `.puml` hoặc style.
