# REST API Contract

Hợp đồng chốt cho 5 phân hệ. Quy ước chung (định dạng `ApiResponse`, mã lỗi, phân trang, idempotency): [API_CONVENTIONS](API_CONVENTIONS.md). Vai trò khớp ma trận [docs/07 §10](../07-kiem-toan-ham-rc.md); DB kiểm lại vai trò trong mọi hàm `rc_*` (RC403). Chữ ký hàm đã đối chiếu với database thật (`pg_get_function_identity_arguments`).

- **✅** đã cài và có integration test chạy trên PostgreSQL thật.
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
| ✅ | `GET /api/orders/{orderId}/invoice`, `GET /api/invoices/{invoiceId}` | C M W | — → `InvoiceResponse` (dòng, yêu cầu thanh toán — QR đang chờ kèm `qrImageUrl` để hiện lại mã khi tải lại trang —, payment, `uncollected*`, `waived*`) | — | — | 404 | SessionEndIT, OrderLifecycleIT |
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

Package `employee` (`EmployeeController/Service/Repository`, `ShiftController/Service/Repository`, `AuthController/Service`, `AccountRepository`), `security`. Bảng ghi trực tiếp (`rc_app` được cấp) → **@PreAuthorize là lớp kiểm quyền**; nhiều bảng một thao tác → `@Transactional` ở Service. Test: `EmployeeIT`, `AuthIT`.

| # | Endpoint | Actor | Request → Response | Bảng | Trước → Sau | Lỗi |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | `POST /api/auth/login` | công khai | `LoginRequest` → `LoginResponse{accessToken, roles, mustChangePassword}` | `accounts`, `employees`, `account_roles`, `roles` | tài khoản + nhân viên ACTIVE, có vai trò | 401 `INVALID_CREDENTIALS` |
| ✅ | `GET /api/auth/me` | mọi vai trò | — → `MeResponse` | `employees` | — | 401 |
| ✅ | `POST /api/auth/change-password` | mọi vai trò | `ChangePasswordRequest{currentPassword, newPassword (8–72)}` → 200 `data: null` | `accounts` | mật khẩu cũ đúng → hash mới, `must_change_password = false`; token đang dùng được mở khóa ngay. Trước khi đổi, mọi endpoint khác trả 403 `PASSWORD_CHANGE_REQUIRED` | 401 sai mật khẩu cũ · 422 `SAME_PASSWORD` |
| ✅ | `GET /api/departments`, `POST`, `PUT /{id}` | M | `DepartmentRequest{name}` → `DepartmentResponse` | `departments` | — | 409 `DUPLICATE` |
| ✅ | `GET /api/roles` | M | — → `RoleResponse[]` (4 vai trò seed) | `roles` (chỉ đọc) | — | — |
| ✅ | `GET /api/employees?status&page&size`, `GET /api/employees/{id}` | M | — → `PageResponse<EmployeeResponse>` / `EmployeeResponse` (kèm `accountId`, `username`, `accountStatus`, `mustChangePassword`, `roles[]`; không có hash) | `employees`, `departments`, `accounts`, `account_roles` | — | 404 |
| ✅ | `POST /api/employees` | M | `CreateEmployeeRequest{employeeCode, fullName, departmentId, …, username, initialPassword, roles[]}` → 201 | `employees`, `accounts`, `account_roles` (một transaction) | → ACTIVE, tài khoản `must_change_password = true` | 409 `DUPLICATE` mã/username · 422 `UNKNOWN_ROLE`, `REFERENCE_NOT_FOUND` |
| ✅ | `PUT /api/employees/{id}` | M | `UpdateEmployeeRequest{…, workStatus: ACTIVE\|INACTIVE}` → `EmployeeResponse` | `employees` | không sửa nhân viên đã LEFT | 409 `EMPLOYEE_LEFT`, `SELF_LOCKOUT` · 422 `INVALID_DATA` |
| ✅ | `POST /api/employees/{id}/leave` | M | `LeaveRequest{endDate}` → `EmployeeResponse` | `employees` | → LEFT + `end_date`; đăng nhập bị từ chối, token cũ → 401 ở mọi endpoint | 409 `SELF_LOCKOUT` · 422 `INVALID_DATA` (end < hire) |
| ✅ | `PUT /api/accounts/{id}/roles` | M | `RolesRequest{roles[]}` → `EmployeeResponse` | `account_roles` (transaction) | thay toàn bộ vai trò; có hiệu lực ngay với token đang dùng | 422 `UNKNOWN_ROLE` · 409 `SELF_LOCKOUT` (tự bỏ MANAGER) |
| ✅ | `POST /api/accounts/{id}/lock`, `/unlock` | M | — → `EmployeeResponse` | `accounts` | ACTIVE ↔ LOCKED; token của tài khoản bị khóa → 401 ngay | 409 `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_NOT_LOCKED` / `SELF_LOCKOUT` |
| ✅ | `POST /api/accounts/{id}/reset-password` | M | `ResetPasswordRequest{newPassword}` → `EmployeeResponse` | `accounts` | mật khẩu tạm, `must_change_password = true` | 404 |
| ✅ | `GET /api/shifts?activeOnly` · `POST`, `PUT /api/shifts/{id}` | GET mọi vai trò · M | `ShiftRequest{name, startTime, endTime, breakMinutes, active}` → `ShiftResponse` | `shifts` | `endTime ≤ startTime` = ca qua đêm | — |
| ✅ | `POST /api/shift-assignments` | M | `AssignShiftRequest{employeeId, shiftId, workDate, note}` → 201 `ShiftAssignmentResponse` | `shift_assignments` | → SCHEDULED, chụp giờ ca theo giờ Việt Nam (`scheduledStartAt/EndAt`, phút nghỉ) | 409 `OVERLAP` (EXCLUDE, 23P01) · 422 `INVALID_ASSIGNMENT` (ca ngừng / nhân viên không ACTIVE) |
| ✅ | `GET /api/shift-assignments?from&to&employeeId` | mọi vai trò | — → `ShiftAssignmentResponse[]` | như trên | không phải M: luôn chỉ ca của mình | 400 khoảng > 62 ngày |
| ✅ | `POST /api/shift-assignments/{id}/cancel` | M | `ReasonRequest` → `ShiftAssignmentResponse` | `shift_assignments` | SCHEDULED chưa chấm → CANCELLED (+ người, lý do, giờ); khung giờ được phân lại | 409 `NOT_CANCELLABLE` |
| ✅ | `POST /api/attendances` | M | `AttendanceRequest{shiftAssignmentId, status: PRESENT\|ABSENT, actualStartAt, actualEndAt, workedMinutes?, note}` → 201 | `attendances` + phân ca → COMPLETED (một transaction) | chấm một lần/ca; công mặc định = có mặt − phút nghỉ | 409 `DUPLICATE`, `ASSIGNMENT_CANCELLED` · 422 `INVALID_DATA` |
| ✅ | `GET /api/attendances?from&to&employeeId` | mọi vai trò (không phải M: của mình) | — → `AttendanceResponse[]` | `attendances` | — | — |
| ✅ | `POST /api/attendances/{id}/adjustments` · `GET` | M | `AdjustAttendanceRequest{status, actualStartAt, actualEndAt, workedMinutes?, reason}` → `AttendanceResponse` · `AttendanceAdjustmentView[]` | `attendance_adjustments` (chỉ-thêm, giá trị cũ/mới) + `attendances` (một transaction) | — | 422 `INVALID_DATA` (phút công > khoảng có mặt) |

## 3. Quản lý thực đơn — Lê Anh

Package `menu` (`MenuCatalogController/Service/Repository`, `DailyMenuController/Service/Repository`). Bảng ghi trực tiếp; trigger DB chặn sửa công thức đã dùng, hạ hạn mức dưới số suất đã chiếm, sai thứ tự trạng thái thực đơn. Test: `MenuIT`.

| # | Endpoint | Actor | Request → Response | Bảng · hàm | Trước → Sau | Lỗi |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | `GET /api/menu-categories?activeOnly` · `POST` · `PUT /{id}` | GET mọi vai trò · M | `CategoryRequest{name, sortOrder, active}` → `CategoryResponse` | `menu_categories` | — | 409 `DUPLICATE` tên (không phân biệt hoa thường) |
| ✅ | `GET /api/menu-items?categoryId&activeOnly`, `GET /{id}` · `POST` · `PUT /{id}` | GET mọi vai trò · M | `MenuItemRequest{categoryId, name, price, imageUrl, active}` → `MenuItemResponse{…, currentRecipeVersion}` | `menu_items` | đổi giá **không** ảnh hưởng món đã gọi (snapshot) | 409 `DUPLICATE` · 422 `REFERENCE_NOT_FOUND` |
| ✅ | `GET /api/menu-items/{id}/recipes` (M K) · `POST` (M) | | `RecipeRequest{ingredients[{kitchenIngredientTypeId, quantityPerServing}]}` → 201 `RecipeResponse{version, current, ingredients[]}` | `recipes`, `recipe_ingredients` (một transaction) | version mới thành hiện hành; version cũ giữ nguyên cho thực đơn đã dùng | 404 · 422 |
| ✅ | `GET /api/daily-menus/today`, `?date=`, `/{id}` | mọi vai trò | — → `DailyMenuResponse{id, businessDate, status, items[{dailyMenuItemId, name, price, quotaTotal, quotaUsed, remaining, paused, …}]}` | `daily_menus`, `daily_menu_items`, `rc_quota_used`, `rc_business_date` | **hợp đồng với Order**: Waiter gọi món bằng `dailyMenuItemId` | 404 |
| ✅ | `POST /api/daily-menus` · `POST /{id}/items` · `PUT`, `DELETE /api/daily-menu-items/{id}` | M | `DailyMenuRequest{businessDate}`, `DailyMenuItemRequest{menuItemId, quotaTotal}` (null = không giới hạn), `QuotaRequest` | `daily_menus`, `daily_menu_items` | món dùng công thức hiện hành | 409 `MENU_CLOSED`, `MENU_ITEM_NOT_READY` (chưa có công thức / ngừng bán), `DUPLICATE` · 409/422 trigger hạn mức |
| ✅ | `POST /api/daily-menus/{id}/publish`, `/close` | M | — → `DailyMenuResponse` | `daily_menus` | DRAFT → PUBLISHED (≥ 1 món) → CLOSED | 409 RC409 |
| ✅ | `POST /api/daily-menu-items/{id}/pause` (`ReasonRequest`), `/resume` | K M | — → `DailyMenuItemResponse` | `daily_menu_items` | tạm dừng: Waiter gửi → 409, Kitchen nhận → REJECTED | 409 `ALREADY_PAUSED` / `NOT_PAUSED` |

## 4. Quản lý kho — Đỗ Tuấn Anh

Package `inventory` (`IngredientController/Service/Repository`, `StockDocumentController/Service/Repository`, `KitchenStockController/Service/Repository`, `InventoryReportController/Service/Repository`). Chứng từ nhập/xuất/kiểm kê tạo bằng `rc_create_*` (payload snake_case, idempotent `requestKey`); xác nhận bằng `rc_*`. Không cập nhật số dư/sổ kho bằng SQL. Test: `InventoryIT`.

| # | Endpoint | Actor | Request → Response | Hàm / bảng | Trước → Sau | Lỗi · Idempotency |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | `GET/POST/PUT /api/ingredients`, `/api/kitchen-ingredient-types?ingredientId` | GET M K · ghi M | `IngredientRequest{name, baseUnit, minQuantity, expiryTracked}`, `KitchenTypeRequest{rawIngredientId, name, handlingType: DIRECT\|PREPARED, shelfLifeHours, minimumQuantity}` | `ingredients`, `kitchen_ingredient_types` | mỗi nguyên liệu tối đa 1 loại DIRECT; không đổi nguyên liệu gốc/cách dùng sau khi tạo | 409 `DUPLICATE` · 422 `IMMUTABLE_FIELD` |
| ✅ | `POST /api/stock-receipts` · `GET ?status&page&size` · `GET /{id}` · `DELETE /{id}` | M | `ReceiptRequest{requestKey, supplierName, note, items[{ingredientId, inputQuantity, inputUnit, conversionFactor, unitPricePerInputUnit, lotCode, expiryDate}]}` → 201 `ReceiptResponse{…, totalAmount, lines[{baseQuantity, lineAmount, inventoryBatchId}]}` | `rc_create_stock_receipt`; xóa nháp (transaction) | → DRAFT | cùng khóa → phiếu cũ · khác nội dung → 409 RC409 · xóa phiếu đã xác nhận → 409 `NOT_DRAFT` |
| ✅ | `POST /api/stock-receipts/{id}/confirm` | M | `{receivedAt?}` → `ReceiptResponse` | `rc_confirm_receipt` | DRAFT → CONFIRMED, tạo lô RAW + sổ RECEIPT | 409 xác nhận lặp · 422 lô quá hạn/thiếu hạn |
| ✅ | `POST /api/stock-issues` · `GET ?status` · `GET /{id}` · `/{id}/issue` · `/{id}/cancel` | M (GET: K) | `IssueRequest{requestKey, note, items[{inventoryBatchId, quantity, fulfillmentMode: DIRECT\|PREPARE}]}` → `IssueResponse{lines[{wipRemainingQuantity, kitchenBatchId}]}` | `rc_create_stock_issue`, `rc_issue_stock`, `rc_cancel_issue` | DRAFT → ISSUED (trừ RAW, FEFO) → CANCELLED hoàn RAW | 409 sai FEFO / sai bước |
| ✅ | `POST /api/stock-issues/{id}/receive` | K M | — → `IssueResponse` | `rc_receive_issue` | ISSUED → RECEIVED (DIRECT → lô bếp; PREPARE → WIP) | 409 nhận lặp · 422 chưa khai báo loại DIRECT |
| ✅ | `POST /api/kitchen-preparations` · `GET /{id}` · `/{id}/confirm` | K M | `PreparationRequest{requestKey, stockIssueItemId, inputQuantity, lossReason, outputs[{kitchenIngredientTypeId, outputQuantity, usableUntil?}]}` → `PreparationResponse{outputQuantity, lossQuantity, outputs[{kitchenBatchId}]}` | nháp (transaction) + `rc_confirm_preparation` | trừ WIP, tạo lô PREPARED, ghi hao hụt | cùng khóa → phiếu cũ · 409/422 từ hàm |
| ✅ | `POST /api/stock-returns` (K M) · `GET /{id}` · `/{id}/confirm` (M) | | `ReturnRequest{requestKey, reason, items[{sourceIssueItemId, directKitchenBatchId?, quantity}]}` | nháp + `rc_confirm_return` | hàng về lại lô RAW gốc | cùng khóa → phiếu cũ · 409 vượt khả dụng |
| ✅ | `POST /api/stock-adjustments` (K M) · `GET /{id}` · `/{id}/confirm` (M) | | `AdjustmentRequest{requestKey, adjustmentType: DAMAGE\|EXPIRED\|QUALITY\|OTHER, reason, items[{rawBatchId \| wipIssueItemId \| kitchenBatchId, quantity, note}]}` (lượng hủy dương; DB lưu delta âm) | nháp + `rc_confirm_adjustment` | ghi WASTE | 400 không đúng một nguồn |
| ✅ | `POST /api/stock-counts` · `GET /{id}` · `/{id}/confirm` | M | `CountRequest{requestKey, note, items[{rawBatchId \| wipIssueItemId \| kitchenBatchId, actualQuantity, reason}]}` → `CountResponse{lines[{bookQuantity, difference}]}` | `rc_create_stock_count`, `rc_confirm_count` | ghi COUNT_ADJUST chênh lệch | 409 sổ đã đổi từ lúc lập phiếu |
| ✅ | `GET /api/kitchen-preparations`, `/api/stock-returns`, `/api/stock-adjustments` (K M) · `GET /api/stock-counts` (M) `?status&page&size` | | — → danh sách header, mới nhất trước | các bảng chứng từ tương ứng | để màn hình duyệt phiếu nháp | — |
| ✅ | `POST /api/stock-batches/{RAW\|KITCHEN}/{batchId}/mark-unusable` | M | `ReasonRequest` → `{kind, batchId, affectedOrderItems}` | `rc_mark_batch_unusable` | lô hỏng; món đang giữ bật `hasStockException` (bếp gọi `/order-items/{id}/reallocate`) | 422 thiếu lý do |
| ✅ | `GET /api/inventory/raw-stock`, `/history?from&to&ingredientId` (Instant ISO), `/preparation-yield?from&to`, `/raw-lots?ingredientId` | M | — → bảng | `rpt_raw_stock`, `rpt_stock_history`, `rpt_preparation_yield`; lô RAW theo FEFO | — | 400 khoảng sai |
| ✅ | `GET /api/inventory/handover`, `/kitchen-stock`, `/unusable-lots`, `/kitchen-lots?typeId` | K M | — → bảng | `rpt_handover_and_wip`, `rpt_kitchen_stock`, `rpt_unusable_lots`; lô bếp (kèm `directIssueItemId` để lập phiếu hoàn trả) | — | — |

## 5. Quản lý tài chính — Bùi Đào Đức Anh

Package `finance` (lớp `Expense*`, `CashReconciliation*`, `FinanceReport*`). **Chỉ đọc** dữ liệu thanh toán do phần của Phạm Duy An tạo; không tạo/sửa payment, hóa đơn, webhook. Test: `FinanceIT`.

| # | Endpoint | Actor | Request → Response | Hàm / bảng | Trước → Sau | Lỗi |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | `POST /api/expenses` | M | `ExpenseRequest{requestKey, category, description, amount, paymentMethod: CASH\|BANK_TRANSFER, fundSource: CASH_DRAWER\|OWNER\|BANK, stockReceiptId?, paidAt?}` → 201 `ExpenseResponse` | `operating_expenses` (`business_date = rc_business_date(paidAt)` trong SQL) | → PAID; cùng `requestKey` → trả khoản đã ghi | 422 `INVALID_DATA` (quỹ quầy phải tiền mặt, paidAt tương lai) · 409 `DUPLICATE` phiếu nhập đã có khoản chi |
| ✅ | `GET /api/expenses?from&to&category&status`, `GET /{id}` · `POST /{id}/void` | M | `ReasonRequest` → `ExpenseResponse` | `operating_expenses` | PAID → VOIDED (không sửa nội dung) | 409 `ALREADY_VOIDED` |
| ✅ | `POST /api/cash-reconciliations` | M | `ReconcileRequest{businessDate, openingCash, actualCash, reason?, revisionReason?}` → 201 `CashReconciliationResponse` | `rc_reconcile_cash` | → phiên bản mới CURRENT, bản cũ SUPERSEDED | 422 RC422 ngày tương lai · 422 `INVALID_DATA` lệch tiền thiếu `reason` / lập lại thiếu `revisionReason` |
| ✅ | `GET /api/cash-reconciliations/{businessDate}` | M | — → `CashReconciliationResponse{…, stale, versions[]}` | `rpt_cash_reconciliation`, `daily_cash_reconciliations` | `stale = true` khi có thu/chi sau lần đối soát hiện hành | — |
| ✅ | `GET /api/reports/revenue-by-day?from&to`, `/revenue-by-item`, `/unpaid-closures`, `/expenses-by-category` | M | — → bảng báo cáo | `rpt_revenue_by_day`, `rpt_revenue_by_item`, `rpt_unpaid_closures`; chi PAID theo nhóm | doanh thu chỉ từ `payments`; UNCOLLECTED/WAIVED liệt kê riêng, không là doanh thu | 400 khoảng ngày sai (> 366 ngày) |
