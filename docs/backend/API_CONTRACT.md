# REST API Contract

Hợp đồng chốt cho 5 phân hệ. Quy ước chung (định dạng `ApiResponse`, mã lỗi, phân trang, idempotency): [API_CONVENTIONS](API_CONVENTIONS.md). Vai trò khớp ma trận [docs/07 §10](../07-kiem-toan-ham-rc.md); DB kiểm lại vai trò trong mọi hàm `rc_*` (RC403). Chữ ký hàm đã đối chiếu với database thật (`pg_get_function_identity_arguments`).

- **✅** đã cài và có integration test chạy trên PostgreSQL thật · **📝** hợp đồng để thành viên cài.
- Actor: **M** MANAGER · **W** WAITER · **K** KITCHEN · **C** CASHIER. Mọi endpoint trừ `login` và webhook cần JWT (401 nếu thiếu, 403 nếu sai vai trò).
- Lỗi chung không nhắc lại: 400 `VALIDATION_ERROR`, 401, 403 `FORBIDDEN`/`RC403`, 404 `NOT_FOUND`.
- Mọi thao tác ghi có hàm `rc_*` là **một lời gọi** — không `@Transactional`, không ghi bổ sung bằng Java.

## 1. Quản lý đơn hàng — Phạm Duy An

Service/Repository: `order.service.TableService`, `OrderService`, `KitchenService` · `order.repository.TableRepository`, `OrderRepository` · `finance.service.InvoiceService`, `PaymentService` (+ `strategy/*`) · `finance.repository.InvoiceRepository`, `PaymentRepository` · `finance.sepay.SepayWebhookService`, `SepayWebhookRepository`.

### 1.1 Bàn và phiên

| # | Endpoint | Actor | Request → Response | Bảng · hàm | Trước → Sau | Lỗi nghiệp vụ | Test |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ✅ | `GET /api/tables?includeInactive` | M W C | — → `TableResponse[]` (`active`, `currentSessionId`, `currentOrderId`); `includeInactive=true` chỉ có tác dụng với M | `dining_tables`, `table_sessions`, `orders` | — | — | TableIT, OrderLifecycleIT |
| ✅ | `POST /api/tables` | M | `TableRequest{code, capacity}` → 201 `TableResponse` | `dining_tables` (ghi trực tiếp cột `code`, `capacity` — rc_app được cấp) | → bàn AVAILABLE, đang dùng | 409 `DUPLICATE` mã bàn | TableIT |
| ✅ | `PUT /api/tables/{tableId}` | M | `TableRequest` → `TableResponse` | `dining_tables` | đổi mã/sức chứa (kể cả khi có khách); **không** đổi trạng thái | 404 · 409 `DUPLICATE` | TableIT |
| ✅ | `POST /api/tables/{tableId}/deactivate`, `/activate` | M | — → `TableResponse` | `dining_tables.is_active` | ngừng sử dụng **chỉ khi AVAILABLE** (một câu UPDATE, tuần tự với `rc_open_session`); bàn ngừng không mở phiên được (RC422) và ẩn khỏi danh sách thường. Không xóa bàn (có lịch sử phiên; rc_app không có quyền DELETE) | 409 `TABLE_OCCUPIED` · 404 | TableIT |
| ✅ | `GET /api/tables/{tableId}` | M W C | — → `TableResponse` | như trên | — | 404 | OrderLifecycleIT |
| ✅ | `POST /api/tables/{tableId}/sessions` | W M | — → 201 `OpenSessionResponse` (`sessionId`, `tableId`, `orderId`) | `rc_open_session` | bàn AVAILABLE, đang dùng → phiên OPEN + đơn OPEN, bàn OCCUPIED | 409 RC409 bàn có khách · 422 RC422 bàn không tồn tại/ngừng | TableIT, PaymentConcurrencyIT (3 Waiter mở cùng bàn) |
| ✅ | `GET /api/tables/{tableId}/sessions?page&size` | M W C | — → `PageResponse<SessionResponse>` | `table_sessions`, `orders`, `invoices` | — | 404 | OrderLifecycleIT |
| ✅ | `GET /api/sessions/{sessionId}` | M W C | — → `SessionResponse` (trạng thái phiên, đơn, hóa đơn) | như trên | — | 404 | nhiều |
| ✅ | `POST /api/sessions/{sessionId}/close-empty` | W M | — → `SessionResponse` | `rc_close_empty_session` | mọi món CANCELLED/REJECTED, **chưa có hóa đơn** → đơn CANCELLED, phiên CLOSED, bàn AVAILABLE | 409 còn món/đã có hóa đơn (dùng waive/abandon) | SessionEndIT |
| ✅ | `POST /api/sessions/{sessionId}/abandon` | M | `ReasonRequest` → `AbandonResponse` | `rc_abandon_session` | phiên OPEN → món mở bị hủy, hóa đơn **UNCOLLECTED** (0 đồng → WAIVED), không payment, phiên CLOSED, bàn AVAILABLE | 409 phiên/đơn đã đóng · 422 thiếu lý do | SessionEndIT, SepayWebhookIT |

### 1.2 Đơn hàng, gọi món, bếp, phục vụ, hủy

Đơn hàng được **tạo cùng phiên** (`rc_open_session`, quan hệ 1–1) — không có endpoint tạo đơn riêng. State machine dòng món: PENDING → ACCEPTED → COOKING → READY → SERVED; PENDING → REJECTED; PENDING/ACCEPTED → CANCELLED (hủy thường); COOKING/READY/SERVED → CANCELLED (hủy ngoại lệ, Manager).

| # | Endpoint | Actor | Request → Response | Hàm | Trước → Sau | Lỗi nghiệp vụ | Idempotency | Test |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ✅ | `GET /api/orders/{orderId}` | M W C K | — → `OrderResponse` (phiên + mọi dòng món) | — | — | 404 | — | OrderLifecycleIT |
| ✅ | `POST /api/orders/{orderId}/batches` | W M | `SubmitBatchRequest{requestKey, items[{dailyMenuItemId, quantity, note}]}` → 201 `SubmitBatchResponse{batchId, replayed, warnings[], items[]}` | `rc_submit_batch` (trả **JSONB**) | đơn OPEN, hóa đơn chưa chốt, món thuộc thực đơn PUBLISHED hôm nay, không tạm dừng → dòng PENDING. **Hết suất không chặn**: `warnings[].code = QUOTA_EXCEEDED` | 409 đơn không mở / đang thanh toán (mở lại hóa đơn trước) / món không bán / tạm dừng · 422 thiếu requestKey | `requestKey`: gửi lặp → đợt cũ, `replayed = true` | OrderLifecycleIT, KitchenAndCancelIT |
| ✅ | `GET /api/kitchen/queue?status=…` | K M | — → `OrderItemResponse[]` (đơn OPEN, cũ nhất trước; mặc định PENDING, ACCEPTED, COOKING, READY) | — | — | 400 status khác 4 giá trị | — | OrderLifecycleIT, KitchenAndCancelIT |
| ✅ | `POST /api/order-items/{id}/accept` | K M | — → `AcceptItemResponse{result, reason, item}` | `rc_accept_item` | PENDING → **ACCEPTED** (chiếm suất + đặt giữ nguyên liệu FEFO, nguyên tử) hoặc **REJECTED** kèm lý do DB (vượt hạn mức / thiếu nguyên liệu / tạm dừng) | 409 không còn PENDING | gọi lại → 409 | KitchenAndCancelIT, OrderLifecycleIT |
| ✅ | `POST /api/order-items/{id}/reject` | K M | `ReasonRequest` → `OrderItemResponse` | `rc_reject_item` | PENDING → REJECTED (không chiếm suất) | 409 · 422 lý do trống | — | KitchenAndCancelIT |
| ✅ | `POST /api/order-items/{id}/start-cooking` | K M | — → `OrderItemResponse` | `rc_start_cooking` | ACCEPTED → COOKING; tiêu hao phần đã giữ, ghi sổ COOK một lần | 409 chưa ACCEPTED / lô giữ hỏng (cấp lại) | gọi lại → 409, không trừ lần hai | KitchenAndCancelIT |
| ✅ | `POST /api/order-items/{id}/finish` | K M | — → `OrderItemResponse` | `rc_finish_dish` | COOKING → READY | 409 | — | OrderLifecycleIT |
| ✅ | `POST /api/order-items/{id}/reallocate` | K M | — → `OrderItemResponse` | `rc_reallocate_item` | ACCEPTED có lô hỏng → giữ lại từ lô khác | 409 · RC001 vẫn thiếu | — | (luồng lô hỏng do Đỗ Tuấn Anh đánh dấu; test tích hợp chung) |
| ✅ | `POST /api/order-items/{id}/serve` | W M | — → `OrderItemResponse` | `rc_serve_item` | READY → SERVED | 409 | — | OrderLifecycleIT |
| ✅ | `POST /api/order-items/{id}/cancel` | W K M | `ReasonRequest` → `OrderItemResponse` | `rc_cancel_item` | PENDING/ACCEPTED → CANCELLED; trả phần giữ và suất đúng một lần | 409 đã COOKING ("chỉ Manager…") / đã hủy | gọi lại → 409 | KitchenAndCancelIT, SessionEndIT |
| ✅ | `POST /api/order-items/{id}/cancel-exception` | M | `ReasonRequest` → `OrderItemResponse` | `rc_cancel_item_exception` | COOKING/READY/SERVED → CANCELLED; không hoàn nguyên liệu/suất, không tính tiền | 409 hóa đơn PAYMENT_PENDING (mở lại trước) / PAID | — | KitchenAndCancelIT |
| ✅ | `GET /api/order-items/{id}/events` | M W C K | — → `ItemEventResponse[]` (người, lý do, thời điểm) | — | — | 404 | — | OrderLifecycleIT, KitchenAndCancelIT |

Không hủy một phần số lượng (QĐ-5): hủy cả dòng rồi gửi lượt mới với số lượng đúng.

### 1.3 Hóa đơn

| # | Endpoint | Actor | Request → Response | Hàm | Trước → Sau | Lỗi nghiệp vụ | Test |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ✅ | `POST /api/orders/{orderId}/invoice` | C M | — → `InvoiceResponse` | `rc_checkout_invoice` | mọi món kết thúc, ≥ 1 SERVED, tổng > 0 → hóa đơn **PAYMENT_PENDING**, đủ `invoice_items`, **tổng do DB tính**; Waiter không gửi thêm được | 409 đã chốt / còn món chưa xong / không món phải thu · 422 tổng 0 đồng (→ waive) | OrderLifecycleIT, SessionEndIT |
| ✅ | `GET /api/orders/{orderId}/invoice`, `GET /api/invoices/{invoiceId}` | C M W | — → `InvoiceResponse` (dòng, yêu cầu thanh toán, payment, `uncollected*`, `waived*`) | — | — | 404 | SessionEndIT, OrderLifecycleIT |
| ✅ | `POST /api/invoices/{invoiceId}/reopen` | C M | `ReasonRequest` → `InvoiceResponse` | `rc_reopen_invoice` | PAYMENT_PENDING → DRAFT, hủy yêu cầu thanh toán đang mở; sau đó gọi thêm món / hủy ngoại lệ / chốt lại | 409 không PAYMENT_PENDING | OrderLifecycleIT |
| ✅ | `POST /api/orders/{orderId}/waive` | M | `ReasonRequest` → `InvoiceResponse` | `rc_waive_invoice` | DRAFT/PAYMENT_PENDING (mọi món kết thúc) → **WAIVED**, giữ giá trị, không payment, phiên CLOSED, bàn AVAILABLE; cũng là cách đóng hóa đơn 0 đồng | 409 PAID/UNCOLLECTED/WAIVED, còn món chưa xong | SessionEndIT |

### 1.4 Thanh toán (Strategy CASH/QR) và webhook SePay

| # | Endpoint | Actor | Request → Response | Hàm | Trước → Sau | Lỗi nghiệp vụ | Idempotency | Test |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ✅ | `POST /api/invoices/{invoiceId}/payments` `method=CASH` | C M | `PaymentRequest{method, cashReceived}` → 201 `PaymentResponse{status: PAID, paymentId, amountDue}` | `rc_pay_cash` (`CashPaymentStrategy`) | PAYMENT_PENDING, không có QR đang mở → payment + hóa đơn **PAID**, đơn/phiên CLOSED, bàn AVAILABLE | 409 không chờ thu / QR đang mở / đã thu · 422 tiền khách đưa < số phải thu | thu lần hai → 409; 3 request đồng thời → đúng 1 thành công | PaymentIT, OrderLifecycleIT, PaymentConcurrencyIT |
| ✅ | `POST /api/invoices/{invoiceId}/payments` `method=QR` | C M | `PaymentRequest{method}` → 201 `PaymentResponse{status: WAITING_TRANSFER, paymentReference, amountDue, qrImageUrl}` | `rc_start_qr` (`QrPaymentStrategy`) | PAYMENT_PENDING → yêu cầu WAITING_TRANSFER; hóa đơn **vẫn PAYMENT_PENDING** | 409 đã có yêu cầu mở | — | PaymentIT, SepayWebhookIT |
| ✅ | `POST /api/payment-requests/{requestId}/cancel` | C M | `ReasonRequest` → `InvoiceResponse` | `rc_cancel_payment_request` | yêu cầu mở → CANCELLED (webhook đến sau → AFTER_CANCEL) | 409 không còn mở | — | SepayWebhookIT, PaymentConcurrencyIT |
| ✅ | `POST /api/webhooks/sepay` | SePay (header `Authorization: Apikey <SEPAY_API_KEY>`) | JSON SePay (`id`, `transferType`, `transferAmount`, `code`, `content`…) → 200 `{"success": true, "data": {transactionCode, result, reference}}` | `rc_process_verified_qr` qua login **`rc_webhook`** | `in` + tham chiếu + đúng số tiền + yêu cầu đang chờ → **PROCESSED**: payment QR, hóa đơn PAID, phiên đóng. Lệch tiền / không thấy tham chiếu → **MISMATCH**; yêu cầu đã hủy/hóa đơn đã kết thúc → **AFTER_CANCEL** (sự cố OPEN, không PAID). `out` → `IGNORED_OUTGOING` | 401 sai/thiếu key · 400 payload hỏng · 503 chưa cấu hình | theo `id` giao dịch SePay: gửi lặp/đồng thời → một bản ghi, cùng kết quả | SepayWebhookIT, PaymentConcurrencyIT |

Chỉ dùng cách xác thực **API Key** mà SePay mô tả cho webhook ([docs.sepay.vn/tich-hop-webhooks](https://docs.sepay.vn/tich-hop-webhooks.html)); không tự giả định chữ ký. Mã tham chiếu lấy từ `code` (cấu hình nhận diện mã thanh toán trong SePay theo mẫu `RC<số>Q<số>`) hoặc tìm trong `content`. Ảnh QR: `https://qr.sepay.vn/img?acc=…&bank=…&amount=…&des=<mã>` khi đặt `SEPAY_BANK_ACCOUNT`, `SEPAY_BANK_NAME`.

## 2. Quản lý nhân viên — Nguyễn Quang Hiếu

Package `employee`, `security`. Bảng ghi trực tiếp (`rc_app` được cấp) → **@PreAuthorize là lớp kiểm duy nhất**; nhiều bảng một thao tác → `@Transactional` ở Service.

| # | Endpoint | Actor | Request → Response | Bảng | Trước → Sau | Lỗi | Test bắt buộc |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ✅ | `POST /api/auth/login` | công khai | `LoginRequest` → `LoginResponse{accessToken, roles, mustChangePassword}` | `accounts`, `employees`, `account_roles`, `roles` | tài khoản + nhân viên ACTIVE, có vai trò | 401 `INVALID_CREDENTIALS` | AuthIT |
| ✅ | `GET /api/auth/me` | mọi vai trò | — → `MeResponse` | `employees` | — | 401 | AuthIT |
| 📝 | `POST /api/auth/change-password` | mọi vai trò | `ChangePasswordRequest{currentPassword, newPassword}` → 204 | `accounts` | mật khẩu cũ đúng → hash mới, `must_change_password = false` | 401 sai mật khẩu cũ · 400 | đúng/sai mật khẩu cũ, token cũ vẫn dùng được tới hết hạn |
| 📝 | `GET /api/employees?page&size&status` | M | — → `PageResponse<EmployeeResponse>` | `employees`, `departments`, `accounts` | — | — | phân trang, 403 vai trò khác |
| 📝 | `POST /api/employees` | M | `CreateEmployeeRequest{employeeCode, fullName, departmentId, username, initialPassword, roles[]}` → 201 | `employees`, `accounts`, `account_roles` (một transaction) | → nhân viên ACTIVE, tài khoản `must_change_password = true` | 409 `DUPLICATE` mã/username · 422 phòng ban sai | trùng username, vai trò không tồn tại |
| 📝 | `PUT /api/employees/{id}` | M | `UpdateEmployeeRequest` → `EmployeeResponse` | `employees` | — | 422 `INVALID_DATA` (CHECK ngày) | — |
| 📝 | `POST /api/employees/{id}/leave` | M | `LeaveRequest{endDate}` → `EmployeeResponse` | `employees` | → LEFT + `end_date` (rc_authorize sẽ chặn nhân viên này) | 422 | sau khi nghỉ, token cũ gọi `rc_*` → RC403 |
| 📝 | `PUT /api/accounts/{id}/roles`, `POST /api/accounts/{id}/lock`, `/unlock`, `/reset-password` | M | `RolesRequest{roles[]}` / — | `account_roles`, `accounts` | — | 404 | Manager tự khóa mình → chặn ở Service |
| 📝 | `GET /api/roles` | M | — → `RoleResponse[]` (4 vai trò seed) | `roles` | — | — | — |
| 📝 | `GET/POST/PUT /api/shifts` | M | `ShiftRequest` → `ShiftResponse` | `shifts` | — | — | — |
| 📝 | `POST /api/shift-assignments`, `GET /api/shift-assignments?from&to&employeeId` | M (GET: mọi vai trò xem ca của mình) | `AssignShiftRequest{employeeId, shiftId, workDate}` → 201 | `shift_assignments` | → SCHEDULED; chồng ca bị EXCLUDE chặn | 409 `OVERLAP` (SQLSTATE 23P01) | chồng ca |
| 📝 | `POST /api/shift-assignments/{id}/cancel` | M | `ReasonRequest` | `shift_assignments` | SCHEDULED → CANCELLED (+ người, lý do, giờ) | 422 | — |
| 📝 | `POST /api/attendances`, `POST /api/attendances/{id}/adjustments` | M | `AttendanceRequest` / `AdjustAttendanceRequest{…, reason}` | `attendances`, `attendance_adjustments` (chỉ-thêm, cùng transaction) | chấm một lần/ca; điều chỉnh lưu giá trị cũ/mới | 409 `DUPLICATE` · 422 `INVALID_DATA` | chấm lặp, phút công > khoảng có mặt |

## 3. Quản lý thực đơn — Lê Anh

Package `menu`. Bảng ghi trực tiếp; trigger DB chặn sửa công thức đã dùng, hạ hạn mức dưới số suất đã chiếm, sai thứ tự trạng thái thực đơn.

| # | Endpoint | Actor | Request → Response | Bảng · hàm | Trước → Sau | Lỗi | Test bắt buộc |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 📝 | `GET/POST/PUT /api/menu-categories` | M (GET: mọi vai trò) | `CategoryRequest{name, sortOrder, active}` → `CategoryResponse` | `menu_categories` | — | 409 `DUPLICATE` tên (không phân biệt hoa thường) | trùng tên |
| 📝 | `GET/POST/PUT /api/menu-items` | M (GET: mọi vai trò) | `MenuItemRequest{categoryId, name, price, imageUrl, active}` → `MenuItemResponse` | `menu_items` | đổi giá **không** ảnh hưởng món đã gọi (snapshot) | 409 `DUPLICATE` · 422 giá âm | đổi giá sau khi gọi món |
| 📝 | `POST /api/menu-items/{id}/recipes`, `GET /api/menu-items/{id}/recipes` | M | `RecipeRequest{ingredients[{kitchenIngredientTypeId, quantityPerServing}]}` → `RecipeResponse{version, current}` | `recipes`, `recipe_ingredients` (một transaction) | → version mới `is_current`, version cũ bỏ cờ | 409 RC409 sửa công thức đã dùng | sửa công thức đã dùng phải tạo version mới |
| 📝 | `POST /api/daily-menus`, `POST /api/daily-menus/{id}/items`, `PUT /api/daily-menu-items/{id}` | M | `DailyMenuRequest{businessDate}`, `DailyMenuItemRequest{menuItemId, quotaTotal}` (NULL = không giới hạn, 0 = hết suất) | `daily_menus`, `daily_menu_items` | DRAFT; đã có món gọi thì không đổi món/công thức | 409 RC409 hạ quota dưới số đã chiếm | hạ quota |
| 📝 | `POST /api/daily-menus/{id}/publish`, `/close` | M | — | `daily_menus` | DRAFT → PUBLISHED (có ≥ 1 món) → CLOSED | 409 RC409 | publish rỗng |
| 📝 | `POST /api/daily-menu-items/{id}/pause` (`ReasonRequest`), `/resume` | K M | — | `daily_menu_items` | tạm dừng: Waiter không gửi được, Kitchen nhận → REJECTED | 422 thiếu lý do | gửi món đang tạm dừng → 409 |
| 📝 | `GET /api/daily-menus/today` | W K C M | — → `TodayMenuResponse{businessDate, items[{dailyMenuItemId, name, price, quotaTotal, quotaUsed, remaining, paused}]}` | `daily_menus`, `daily_menu_items`, `menu_items`, `rc_quota_used`, `rc_business_date` | **Hợp đồng với Order**: màn hình gọi món dùng `dailyMenuItemId` này | — | món hết suất vẫn hiển thị (remaining 0) |

## 4. Quản lý kho — Đỗ Tuấn Anh

Package `inventory`. Chứng từ nhập/xuất/kiểm kê tạo bằng `rc_create_*` (idempotent `requestKey`); xác nhận bằng `rc_confirm_*`. Không cập nhật số dư/sổ kho bằng SQL.

| # | Endpoint | Actor | Request → Response | Hàm / bảng | Trước → Sau | Lỗi | Idempotency / Test |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 📝 | `GET/POST/PUT /api/ingredients`, `/api/kitchen-ingredient-types` | M (GET: K) | `IngredientRequest`, `KitchenTypeRequest{rawIngredientId, name, handlingType, shelfLifeHours}` | `ingredients`, `kitchen_ingredient_types` | mỗi nguyên liệu tối đa 1 loại DIRECT | 409 `DUPLICATE` | — |
| 📝 | `POST /api/stock-receipts` | M | `CreateReceiptRequest{requestKey, supplierName, note, items[]}` → 201 `ReceiptResponse` | `rc_create_stock_receipt` | → DRAFT | 409 RC409 cùng khóa khác nội dung · 422 | cùng khóa → phiếu cũ; test lặp/khác nội dung |
| 📝 | `POST /api/stock-receipts/{id}/confirm` | M | `{receivedAt?}` → `ReceiptResponse` | `rc_confirm_receipt` | DRAFT → CONFIRMED, tạo lô RAW + sổ RECEIPT | 409 · 422 lô quá hạn/thiếu hạn | xác nhận lặp → 409 |
| 📝 | `POST /api/stock-issues`, `/{id}/issue`, `/{id}/cancel` | M | `CreateIssueRequest{requestKey, items[{inventoryBatchId, quantity, fulfillmentMode}]}` | `rc_create_stock_issue`, `rc_issue_stock`, `rc_cancel_issue` | DRAFT → ISSUED (trừ RAW, FEFO) → CANCELLED (hoàn RAW) | 409 không FEFO / thiếu tồn / lô hỏng | 2 phiếu tranh lô cuối |
| 📝 | `POST /api/stock-issues/{id}/receive` | K M | — | `rc_receive_issue` | ISSUED → RECEIVED (WIP hoặc lô bếp DIRECT) | 409 nhận lặp | — |
| 📝 | `POST /api/kitchen-preparations` + `/{id}/confirm` | K M | `PreparationRequest{requestKey, stockIssueItemId, inputQuantity, lossReason, outputs[]}` | `kitchen_preparations`, `kitchen_preparation_outputs` (nháp, một transaction) + `rc_confirm_preparation` | → lô bếp PREPARED, hao hụt = vào − ra | 409 vượt WIP · 422 đầu ra > đầu vào | `DUPLICATE` requestKey → đọc lại bản ghi |
| 📝 | `POST /api/stock-returns` + `/{id}/confirm`, `POST /api/stock-adjustments` + `/{id}/confirm` | K M tạo · M xác nhận | `ReturnRequest`, `AdjustmentRequest{requestKey, type, reason, items[]}` | nháp + `rc_confirm_return`, `rc_confirm_adjustment` | trả về RAW / ghi WASTE | 409 vượt khả dụng (phần đang giữ) | — |
| 📝 | `POST /api/stock-counts` + `/{id}/confirm` | M | `CreateCountRequest{requestKey, items[{rawBatchId \| wipIssueItemId \| kitchenBatchId, actualQuantity, reason}]}` | `rc_create_stock_count`, `rc_confirm_count` | ghi COUNT_ADJUST chênh lệch | 409 số sổ đã đổi (stale) | — |
| 📝 | `POST /api/stock-batches/{kind}/{batchId}/mark-unusable` | M | `ReasonRequest` → `{affectedOrderItems}` | `rc_mark_batch_unusable` | lô hỏng; món đang giữ bật `hasStockException` (bếp gọi `/order-items/{id}/reallocate`) | 409 | tích hợp với Order |
| 📝 | `GET /api/inventory/raw-stock`, `/handover`, `/kitchen-stock`, `/unusable-lots`, `/history?from&to&ingredientId`, `/preparation-yield?from&to` | M (kitchen-stock: K) | — → bảng báo cáo | `rpt_raw_stock`, `rpt_handover_and_wip`, `rpt_kitchen_stock`, `rpt_unusable_lots`, `rpt_stock_history`, `rpt_preparation_yield` | — | — | số liệu như docs/11 |

## 5. Quản lý tài chính — Bùi Đào Đức Anh

Package `finance` (lớp `Expense*`, `CashReconciliation*`, `FinanceReport*`). **Chỉ đọc** dữ liệu thanh toán do phần của Phạm Duy An tạo; không tạo/sửa payment, hóa đơn, webhook.

| # | Endpoint | Actor | Request → Response | Hàm / bảng | Trước → Sau | Lỗi | Test bắt buộc |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 📝 | `POST /api/expenses` | M | `ExpenseRequest{requestKey, category, description, amount, paymentMethod, fundSource, stockReceiptId?, paidAt}` → 201 | `operating_expenses` (`business_date = rc_business_date(paidAt)` trong SQL) | → PAID | 409 `DUPLICATE` requestKey / phiếu nhập đã có khoản chi · 422 quỹ quầy phải tiền mặt | gửi lặp |
| 📝 | `GET /api/expenses?from&to&category`, `POST /api/expenses/{id}/void` | M | `ReasonRequest` | `operating_expenses` | PAID → VOIDED (không sửa nội dung) | 409 đã VOIDED | — |
| 📝 | `POST /api/cash-reconciliations` | M | `ReconcileRequest{businessDate, openingCash, actualCash, reason?, revisionReason?}` → 201 | `rc_reconcile_cash` | → phiên bản mới CURRENT, bản cũ SUPERSEDED | 422 ngày tương lai, lệch tiền thiếu lý do (CHECK) | lập lại không `revisionReason` |
| 📝 | `GET /api/cash-reconciliations/{businessDate}` | M | — → `CashReconciliationResponse{…, stale}` | `rpt_cash_reconciliation` | — | — | stale khi có thu/chi sau khi đối soát |
| 📝 | `GET /api/reports/revenue-by-day?from&to`, `/revenue-by-item`, `/unpaid-closures` | M | — → bảng báo cáo | `rpt_revenue_by_day`, `rpt_revenue_by_item`, `rpt_unpaid_closures` | doanh thu chỉ từ `payments`; UNCOLLECTED/WAIVED liệt kê riêng | 400 khoảng ngày sai | số liệu như docs/11; webhook lặp không làm tăng doanh thu |
