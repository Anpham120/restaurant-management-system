# 07 · Kiểm toán hàm `rc_*` — schema v4.1 bản chốt nghiệp vụ

> **Phạm vi:** 28 hàm nghiệp vụ `rc_*` trong [04-schema-v4.1.sql](04-schema-v4.1.sql) (20 tên của v4 + 6 hàm DB-34 + 2 hàm của QĐ-2/QĐ-3), 12 hàm hỗ trợ và 22 hàm trigger.
> **Cách kiểm:** mỗi hàm được đọc theo 12 tiêu chí (vào/ra, điều kiện, actor, bảng đọc-ghi, trạng thái, khóa, idempotency, ràng buộc, rollback, tương thích v4, ngoại lệ, kiểm thử), rồi đối chiếu với kết quả chạy thật.
> **Mức xác minh:** PostgreSQL 15.18, 16.14, 17.5 và 18.4 (server thật, không phải PGlite): schema, file 05, 06 và 08 đạt trên cả bốn phiên bản; kiểm bằng lỗi cài cố ý (mutation) chạy trên 17.5. Số liệu ở mục 9; giới hạn ở mục 8.
> **Không tuyên bố tương đương với mã v4:** repository không chứa SQL v4 gốc — xem mục 1.

## 0. Kết luận

| Mục | Kết quả |
| --- | --- |
| Hàm nghiệp vụ | **28** = 20 tên của v4 + 6 hàm DB-34 + 2 hàm mới (`rc_cancel_item_exception`, `rc_abandon_session`). Cộng 12 hàm hỗ trợ + 22 hàm trigger = 62 hàm `rc_*` |
| So với baseline v4.1 | 2 hàm mới · 24 hàm sửa (21 hàm đổi hành vi, khóa hoặc kiểm đầu vào; 3 hàm chỉ đổi mã lỗi) · 2 hàm giữ nguyên |
| Lỗi thật tìm ra khi chạy trên server | 2 lỗi baseline tái hiện được (**deadlock do đảo thứ tự khóa**, **webhook trùng đồng thời nhận 23505**), 1 họ deadlock ở chứng từ kho (tái hiện bằng mutation), 1 hạn chế môi trường (locale `C`). Chi tiết mục 7 |
| Kiểm thử | Trên PostgreSQL 15.18, 16.14, 17.5 và 18.4: 05: **198 ca dương + 246 ca âm** đạt · 06: **67/67** đạt (15 vòng đua) · mutation SQL **40/40** · mutation đồng thời **12/15** (3 mutant sống sót đều tương đương, mục 9) |
| Quyền | DB cưỡng chế vai trò MANAGER cho 2 hàm theo QĐ-2/QĐ-3; các hàm còn lại tin `p_actor` do ứng dụng truyền (spec A2). Mô hình GRANT/REVOKE được kiểm bằng hai role thử |
| Việc nhóm cần xác nhận | QĐ-5 và 6 điểm khác ở mục 8 |

## 1. Phạm vi, nguồn đối chiếu và giới hạn

- **Nguồn:** Master Specification v4 do người dùng cung cấp (không nằm trong repository), 04 (schema), 05 (kiểm thử SQL), 06 (kiểm thử đồng thời).
- **SQL v4 gốc không có trong repository** (thư mục `docs/` chỉ chứa tài liệu của v4.1). Vì vậy **không thể đối chiếu mã nguồn v4 ↔ v4.1 và không có tuyên bố "tương đương v4"**. Spec §I4 chỉ liệt kê 20 *tên* hàm, không có thân hàm.
- Cột "Tương thích nghiệp vụ v4" ở mục 4 đối chiếu với **hợp đồng trong Master Spec v4** (§E2 chuyển trạng thái, §G luồng, §I3 khóa, §I4, §M) — đó là so sánh theo hợp đồng, không phải so sánh mã.
- Dòng "Thay đổi so với baseline" so với **baseline v4.1** = bản `04-schema-v4.1.sql` mà repository chứa khi bắt đầu vòng chốt nghiệp vụ. Bản đó không còn là file riêng trong repo; nó được dựng lại từ nhật ký phiên làm việc trước, nạp được trên PostgreSQL 17.5 (48 bảng, 37 hàm `rc_*`, 9 trigger) và dùng để tái hiện các lỗi ở mục 7.

## 2. Quy ước chung của mọi hàm

**Mã lỗi** (SQLSTATE do `rc_raise` phát ra) để backend ánh xạ:

| Mã | Ý nghĩa | Gợi ý xử lý ở backend |
| --- | --- | --- |
| `RC001` | Thiếu nguyên liệu (chỉ nội bộ `rc_allocate_item`; `rc_accept_item` đổi thành REJECTED, `rc_reallocate_item` để lan ra) | Hiển thị lý do cho Kitchen |
| `RC403` | Không đủ vai trò (chỉ hai hàm Manager-only) | HTTP 403 |
| `RC409` | Xung đột trạng thái: gọi lặp, sai bước, đối tượng đã đổi | HTTP 409; với gọi lặp coi như đã xử lý |
| `RC422` | Dữ liệu vào không hợp lệ (thiếu lý do, phiếu rỗng, không tồn tại) | HTTP 422 |
| `RC500` | Bất biến bị phá (số dư lệch sổ, trạng thái không có sự kiện) — chỉ phát ra từ constraint trigger lúc COMMIT | HTTP 500 + cảnh báo vận hành |
| `23505` `23503` `23514` | UNIQUE / FK / CHECK | Lỗi lập trình hoặc trùng yêu cầu |
| `40001` `40P01` | Serialization / deadlock | Thử lại giao dịch (không còn xảy ra trong các ca đã kiểm) |

**Thứ tự khóa dùng chung** (header file 04) — mọi hàm khóa theo đúng thứ tự này, trong cùng một mức thì theo `id` tăng dần (lô bếp theo loại rồi `id`), chứng từ kho (header) khóa trước tài nguyên nó chạm:

| Mức | Đối tượng | Hàm giữ khóa này |
| :-: | --- | --- |
| 1 | `orders` | gửi món, chốt hóa đơn, thanh toán, hủy ngoại lệ, bỏ về, đóng phiên trống |
| 2 | `invoices` | như trên (qua `rc_lock_invoice` hoặc trực tiếp) |
| 3 | `payment_requests` đang mở | thanh toán, QR, mở lại, bỏ về |
| 4 | `table_sessions`, `dining_tables` | mở bàn, đóng phiên, thanh toán, bỏ về |
| 5 | `order_items` | mọi bước của món |
| 6 | `daily_menu_items` | `rc_accept_item` (tuần tự hóa quota) |
| 7 | `inventory_batches` | nhập/xuất/hủy phiếu xuất, hoàn trả, hủy hàng, kiểm kê |
| 8 | `stock_issue_items` (WIP) | sơ chế, hoàn trả, hủy hàng, kiểm kê |
| 9 | `kitchen_batches` theo (loại, id) | cấp phát, nấu, hủy, cấp lại, hoàn trả, hủy hàng, kiểm kê |

**Idempotency** có ba lớp: (A) khóa theo mã yêu cầu — `rc_submit_batch` (`request_key`), `rc_process_verified_qr` (`provider_transaction_code`) trả lại kết quả cũ; (B) một lần theo trạng thái — mọi hàm chuyển trạng thái/xác nhận chứng từ: lần hai gặp RC409, sổ kho có `event_key` UNIQUE làm hàng rào thứ hai; (C) không idempotent theo thiết kế — `rc_open_session` (RC409 khi bàn có khách), `rc_reconcile_cash` (mỗi lần một phiên bản, bấm lặp không lý do bị CHECK chặn).

**Quyền.** `p_actor` là `employees.id` do backend lấy từ JWT. DB chỉ cưỡng chế vai trò ở `rc_cancel_item_exception` và `rc_abandon_session` (`rc_require_role`: nhân viên ACTIVE, tài khoản ACTIVE, đúng vai trò). Phần còn lại theo spec A2: kiểm `permissions` ở `@PreAuthorize`/service. Lớp bảo vệ thứ hai là phân quyền cơ sở dữ liệu: mọi hàm `rc_*` là `SECURITY DEFINER` với `search_path` cố định, `REVOKE ... FROM PUBLIC`; role ứng dụng chỉ nhận `EXECUTE` + `SELECT`, role webhook chỉ nhận `EXECUTE` trên `rc_process_verified_qr` — khối ACL cuối file 05 chứng minh role đó ghi được qua hàm nhưng `UPDATE/INSERT/DELETE` trực tiếp bị `42501`.

**Rollback.** Mỗi lời gọi hàm là một đơn vị: lỗi ở bất kỳ bước nào hoàn tác toàn bộ lời gọi. Ngoại lệ có chủ đích duy nhất là savepoint trong `rc_accept_item`: thiếu suất hoặc nguyên liệu → hoàn tác phần đã giữ rồi *ghi* REJECTED kèm lý do (kết quả nghiệp vụ hợp lệ).

## 3. Quyết định nghiệp vụ → hàm

| Quyết định | Hàm / đối tượng DB | Ca kiểm thử |
| --- | --- | --- |
| **QĐ-1** quota NULL = không giới hạn, 0 = hết suất; vẫn kiểm nguyên liệu | `rc_accept_item`, `rc_submit_batch`, `rc_quota_used`, `ck_daily_menu_quota_nonneg`, `rc_guard_daily_menu_item` | QĐ-1, C1, C2 |
| **QĐ-2** hủy trước COOKING theo quyền; từ COOKING chỉ Manager, có lý do, không hoàn nguyên liệu/quota, không tính tiền | `rc_cancel_item`, **`rc_cancel_item_exception`**, `rc_require_role`, `rc_quota_used`, `rc_guard_order_item`, `rc_guard_invoice_items` | QĐ-2, I-14, C4, C8b |
| **QĐ-3** khách bỏ về: Manager, lý do, đóng phiên + trả bàn sau khi ghi nhận nghĩa vụ; không payments/PAID/doanh thu | **`rc_abandon_session`**, `invoices.uncollected_*`, `rc_guard_invoice`, `rc_guard_order`, `rc_close_empty_session` | QĐ-3, I-11, I-12, C8, C8b |
| **QĐ-4** Cashier chốt hóa đơn: đủ `invoice_items`, tự tính tổng, đối chiếu đơn, PAYMENT_PENDING, Waiter không gửi thêm, mở lại có kiểm soát, khóa chống tranh chấp | `rc_checkout_invoice`, `rc_invoice_total_checked`, `rc_reopen_invoice`, `rc_submit_batch`, `rc_lock_invoice`, `rc_pay_cash`, `rc_start_qr` | QĐ-4, C5, C6, C8 |
| **QĐ-5** hủy một phần số lượng | Chưa áp dụng — giữ hiện trạng (hủy cả dòng, gửi lại), chờ nhóm | — |
| **QĐ-6** đầu ra sơ chế cùng đơn vị | Áp dụng quy ước sẵn có (`base_unit` của nguyên liệu gốc); `rc_confirm_preparation` kiểm Σ đầu ra ≤ đầu vào | PREP-02, PREP-03 |
| **QĐ-7** giữ bảng `orders` | Áp dụng — không đổi số bảng (48) | — |
| **QĐ-8** suất chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING trả đúng một lần; từ COOKING không hoàn; nguyên tử | `rc_accept_item`, `rc_quota_used`, `rc_cancel_item`, `rc_allocate_item`, `rc_guard_daily_menu_item` | QĐ-8, C1, C3, C4 |

## 4. Kiểm toán từng hàm nghiệp vụ

Bảng tổng hợp (cột "ca đúng / ca bị chặn" đếm số lời gọi trong file 05, tính tự động):

| Hàm | Nhóm | Nguồn | Trạng thái | Ca đúng / ca bị chặn (05) | Ca đồng thời (06) |
| --- | :-: | --- | --- | :-: | --- |
| `rc_open_session` | 3.1 | v4 | sửa | 5 / 2 | C9 |
| `rc_close_empty_session` | 3.1 | v4 | sửa | 13 / 3 | — |
| `rc_submit_batch` | 3.2 | DB-34 | sửa | 6 / 10 | C5, C8b |
| `rc_accept_item` | 3.3 · 3.4 | v4 | sửa | 28 / 2 | C1, C2, C3, C4, C5 |
| `rc_reject_item` | 3.3 | DB-34 | sửa | 3 / 4 | — |
| `rc_start_cooking` | 3.3 · 3.4 | v4 | sửa | 9 / 5 | C3, C4, C5 |
| `rc_finish_dish` | 3.3 | v4 | giữ nguyên | 4 / 1 | C5 |
| `rc_serve_item` | 3.3 | DB-34 | giữ nguyên | 3 / 1 | C5 |
| `rc_cancel_item` | 3.3 · 3.4 | v4 | sửa | 10 / 5 | C4 |
| `rc_cancel_item_exception` | 3.3 | QĐ-2/3 | mới | 6 / 9 | C8, C8b |
| `rc_mark_batch_unusable` | 4.1 · 4.5 | DB-34 | sửa | 4 / 4 | — |
| `rc_reallocate_item` | 3.4 | DB-34 | sửa | 1 / 2 | — |
| `rc_checkout_invoice` | 3.5 | v4 | sửa | 11 / 5 | C5, C8, C8b |
| `rc_reopen_invoice` | 3.5 | v4 | sửa | 5 / 2 | C8 |
| `rc_pay_cash` | 3.6 | v4 | sửa | 5 / 4 | C6, C8 |
| `rc_start_qr` | 3.7 | v4 | sửa | 5 / 2 | C6, C7, C8 |
| `rc_process_verified_qr` | 3.7 | v4 | sửa | 11 / 1 | C6, C7, C8 |
| `rc_cancel_payment_request` | 3.6 · 3.7 | v4 | sửa | 1 / 3 | C6 |
| `rc_abandon_session` | 3.1 | QĐ-2/3 | mới | 5 / 5 | C8, C8b |
| `rc_confirm_receipt` | 4.2 | v4 | sửa | 7 / 6 | C9, C10 |
| `rc_issue_stock` | 4.3 | v4 | sửa | 9 / 9 | C9, C10 |
| `rc_receive_issue` | 4.3 | v4 | sửa | 5 / 3 | C9, C10 |
| `rc_cancel_issue` | 4.3 | v4 | sửa | 3 / 4 | — |
| `rc_confirm_preparation` | 4.4 | v4 | sửa | 2 / 4 | C10 |
| `rc_confirm_return` | 4.3 | v4 | sửa | 1 / 2 | C10 |
| `rc_confirm_adjustment` | 4.7 | v4 | sửa | 3 / 5 | C10 |
| `rc_confirm_count` | 4.7 | v4 | sửa | 3 / 5 | C10 |
| `rc_reconcile_cash` | 5.3 | DB-34 | sửa | 2 / 3 | — |

### 4.1 Bàn và phiên phục vụ (3.1)

#### `rc_open_session` — nhóm 3.1 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_open_session(p_table, p_actor)` → BIGINT (id phiên) |
| **2 · Điều kiện trước** | Bàn tồn tại, `is_active`, `status = AVAILABLE`. |
| **3 · Actor và quyền** | Waiter (kiểm ở ứng dụng, spec A2). DB ghi `opened_by_id = p_actor` (FK `employees`). |
| **4 · Bảng đọc / ghi** | R `dining_tables` · W C `table_sessions`, C `orders` (OPEN), U `dining_tables` → OCCUPIED. |
| **5 · Trạng thái trước → sau** | Bàn AVAILABLE → OCCUPIED; phiên OPEN; đơn OPEN. |
| **6 · Khóa hàng và transaction** | `dining_tables` FOR UPDATE (mức 4); một transaction cho cả bốn thay đổi. |
| **7 · Idempotency** | Không idempotent theo thiết kế: lần hai gặp bàn OCCUPIED → RC409. Hai Waiter mở cùng bàn: một thành công, một RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ux_table_sessions_open` (một phiên OPEN mỗi bàn), `orders_table_session_id_key` (1–1), `trg_dining_tables_guard` (OCCUPIED chỉ khi có phiên OPEN). |
| **9 · Lỗi và rollback** | RC422 (bàn không tồn tại hoặc ngừng sử dụng), RC409 (bàn có khách). Mọi lỗi rollback cả phiên, đơn và trạng thái bàn. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G3.1: một transaction tạo phiên OPEN + đơn OPEN + bàn OCCUPIED; unique partial ngăn hai phiên mở song song. |
| **11 · Ngoại lệ** | Bàn ngừng sử dụng (DB-15) bị chặn bằng RC422. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: TABLE-01, TABLE-02). File 06: C9. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Chỉ đổi lỗi thường thành mã RC422/RC409. |

#### `rc_close_empty_session` — nhóm 3.1 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_close_empty_session(p_session, p_actor)` → void |
| **2 · Điều kiện trước** | Phiên và đơn đều OPEN; chưa có hóa đơn (kể cả DRAFT); mọi dòng món (nếu có) là CANCELLED hoặc REJECTED. |
| **3 · Actor và quyền** | Waiter (ứng dụng). DB ghi `closed_by_id`. |
| **4 · Bảng đọc / ghi** | R `orders`, `table_sessions`, `invoices`, `order_items` · W U `orders` → CANCELLED, U `table_sessions` → CLOSED, U `dining_tables` → AVAILABLE. |
| **5 · Trạng thái trước → sau** | Phiên OPEN → CLOSED; đơn OPEN → CANCELLED; bàn OCCUPIED → AVAILABLE. |
| **6 · Khóa hàng và transaction** | `orders` (1) → `table_sessions` (4) → `dining_tables` (4). |
| **7 · Idempotency** | Lần hai → RC409 (phiên không mở). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `trg_orders_guard` (CANCELLED chỉ khi không còn món/hóa đơn có nghĩa vụ), `trg_table_sessions_guard`, `trg_dining_tables_guard` (không trả bàn khi còn phiên OPEN). |
| **9 · Lỗi và rollback** | RC422 (phiên không tồn tại), RC409 (phiên không mở, đã có hóa đơn, còn món đang xử lý/đã phục vụ). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec E2 và G3.6: chỉ đóng trống khi không có nghĩa vụ; có hóa đơn thì không tự trả bàn. Khách bỏ về đi qua `rc_abandon_session` (QĐ-3 ghi đè cho đúng trường hợp đó). |
| **11 · Ngoại lệ** | Hóa đơn DRAFT 0 đồng còn sót sau khi Manager miễn phí toàn bộ (QĐ-2) cũng chặn hàm này — dùng `rc_abandon_session` (trả NULL, đơn CANCELLED). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 13 lời gọi trong ca đúng, 3 lời gọi bị chặn (ca: FNC-EXP, MENU-02, QĐ-1, QĐ-2, QĐ-8, RETURN-02, TABLE-02). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thêm khóa bàn trước khi trả bàn, tách lỗi RC422/RC409, thông điệp trỏ tới `rc_abandon_session`. |

#### `rc_abandon_session` — nhóm 3.1 · hàm bổ sung vòng chốt (QĐ-2/QĐ-3)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_abandon_session(p_session, p_actor, p_reason)` → BIGINT (id hóa đơn UNCOLLECTED, hoặc NULL nếu không có món phải thu) |
| **2 · Điều kiện trước** | Người gọi có vai trò MANAGER đang hoạt động; lý do không rỗng; đơn và phiên OPEN. |
| **3 · Actor và quyền** | MANAGER — cưỡng chế trong DB bằng `rc_require_role`; người và lý do lưu vào `invoices.uncollected_*`, `table_sessions.closed_by_id`, `order_item_events`. |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `payment_requests`, `order_items`, `table_sessions` · W U `order_items` → CANCELLED (+ `order_item_events`), U `stock_reservations`/`kitchen_batches` (món chưa nấu), C `invoices` + `invoice_items` (nếu cần), U `payment_requests` → CANCELLED, U `invoices` → UNCOLLECTED, U `orders`, `table_sessions`, `dining_tables`. |
| **5 · Trạng thái trước → sau** | Dòng PENDING/ACCEPTED → CANCELLED (giải phóng phần giữ); COOKING/READY → CANCELLED (không hoàn); SERVED giữ nguyên và vào hóa đơn. Hóa đơn → UNCOLLECTED; đơn CLOSED (hoặc CANCELLED nếu không có món SERVED); phiên CLOSED; bàn AVAILABLE. |
| **6 · Khóa hàng và transaction** | `orders` (1) → `invoices` (2) → `payment_requests` (3) → `table_sessions` và `dining_tables` (4) → `order_items` (5). |
| **7 · Idempotency** | Lần hai → RC409 (đơn không còn OPEN). Hai Manager cùng lúc hoặc song song với thanh toán: một bên thắng. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_invoice_uncollected_fields` (lý do, người, giờ đủ ba), `invoices_uncollected_by_id_fkey`, `trg_invoice_guard` (UNCOLLECTED bất biến, không có payments), `trg_orders_guard` (CLOSED chỉ sau PAID/UNCOLLECTED), bất biến I-11/I-12. |
| **9 · Lỗi và rollback** | RC403, RC422, RC409. Rollback toàn bộ — bàn không bao giờ được trả mà thiếu bản ghi nghĩa vụ. |
| **10 · Tương thích nghiệp vụ v4** | Không có trong v4: spec M6 để ngỏ ("cần nghiệp vụ riêng được duyệt") và G3.6 cấm tự trả bàn khi hóa đơn chưa PAID. QĐ-3 của nhóm cho phép đóng phiên sau khi ghi nhận nghĩa vụ — chỉ áp dụng qua hàm này. |
| **11 · Ngoại lệ** | Không tạo `payments`, không PAID, không vào doanh thu thực thu, không xóa dữ liệu. Không có luồng thu bù/xóa nợ sau đó (ngoài Core). QR của khách đến muộn → AFTER_CANCEL. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: QĐ-2, QĐ-3). File 06: C8, C8b. |
| **Thay đổi so với baseline** | Hàm mới (không có trong baseline). Hàm mới (QĐ-3) + 3 cột `uncollected_*` và trạng thái UNCOLLECTED. |

### 4.2 Gửi món (3.2)

#### `rc_submit_batch` — nhóm 3.2 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_submit_batch(p_order, p_actor, p_request_key, p_items JSONB)` → BIGINT (id đợt) |
| **2 · Điều kiện trước** | `request_key` không rỗng; `p_items` là mảng không rỗng; đơn OPEN; hóa đơn chưa PAYMENT_PENDING/PAID/UNCOLLECTED; mỗi món: số lượng > 0, có trong thực đơn PUBLISHED hôm nay, món còn kinh doanh, không tạm dừng, còn suất (kiểm mềm khi quota khác NULL). |
| **3 · Actor và quyền** | Waiter (ứng dụng). DB ghi `waiter_id = p_actor`. |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `daily_menu_items`, `daily_menus`, `menu_items`, `order_items` (đếm suất) · W C `order_batches`, C `order_items` (PENDING, chụp tên/giá), C `order_item_events` (NULL → PENDING). |
| **5 · Trạng thái trước → sau** | Món mới ở PENDING (chưa chiếm suất). |
| **6 · Khóa hàng và transaction** | `orders` FOR UPDATE (mức 1) — tuần tự với chốt hóa đơn, hủy ngoại lệ, bỏ về; kiểm lại `request_key` sau khi giữ khóa. |
| **7 · Idempotency** | Có: `order_batches.request_key` UNIQUE. Gửi lặp cùng key và cùng đơn → trả đợt cũ, không ghi thêm. Cùng key khác đơn → RC409 (hai giao dịch khác đơn bắn cùng key cùng lúc: bên sau nhận 23505 từ UNIQUE, vẫn không ghi trùng). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `order_batches_request_key_key`, `ck_order_items_quantity_positive`, `ck_order_items_unit_price_nonneg`, `trg_order_item_events_chain`, `trg_order_items_event_sync`. |
| **9 · Lỗi và rollback** | Một dòng sai (RC422/RC409) → rollback cả đợt; không có đợt nhận một phần. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G3.2 (đợt mới mỗi lần gọi, chống bấm trùng, snapshot, PENDING) và G9.3 (PAYMENT_PENDING không nhận batch mới). |
| **11 · Ngoại lệ** | Quota 0 hoặc đã hết → RC409 "không đủ suất: còn n"; kiểm cứng nằm ở `rc_accept_item` (QĐ-8). Món ngừng kinh doanh/tạm dừng → RC409. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 6 lời gọi trong ca đúng, 10 lời gọi bị chặn (ca: MENU-02, ORDER-01, ORDER-04, QĐ-1, QĐ-3, QĐ-4, TABLE-01). File 06: C5, C8b. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Kiểm lại key sau khi giữ khóa, chặn khi hóa đơn UNCOLLECTED, kiểm mềm quota (QĐ-1/QĐ-8), kiểm số lượng, món còn kinh doanh, mảng rỗng, mã lỗi. |

### 4.3 Bếp xử lý món và nguyên liệu (3.3, 3.4)

#### `rc_accept_item` — nhóm 3.3 · 3.4 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_accept_item(p_item, p_actor)` → TEXT (ACCEPTED hoặc REJECTED) |
| **2 · Điều kiện trước** | Dòng ở PENDING. Nhận được khi thực đơn PUBLISHED đúng ngày, món không tạm dừng, còn suất (quota NULL = không giới hạn, 0 = hết suất) và đủ nguyên liệu khả dụng theo từng loại bếp của công thức. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). DB ghi `actor_id` vào `order_item_events`. |
| **4 · Bảng đọc / ghi** | R `order_items`, `daily_menu_items`, `daily_menus`, `recipe_ingredients`, `kitchen_batches`, `stock_reservations`, `order_item_events` · W U `kitchen_batches.reserved_quantity`, C `stock_reservations` (ACTIVE), U `order_items`, C `order_item_events`. |
| **5 · Trạng thái trước → sau** | PENDING → ACCEPTED (có phần giữ) hoặc PENDING → REJECTED (lý do, không giữ gì). |
| **6 · Khóa hàng và transaction** | `order_items` (5) → `daily_menu_items` FOR UPDATE (6, tuần tự hóa mọi lần nhận cùng một món) → `kitchen_batches` (9) theo (loại, id). `rc_allocate_item` chạy trong savepoint. |
| **7 · Idempotency** | Không trả lại kết quả cũ: lần hai → RC409 (không còn PENDING). Hai Kitchen cùng nhận một dòng: một thắng, một RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_kitchen_reserved_le_on_hand`, `ux_stock_reservation_batch`, `ck_stock_reservations_quantity_positive`, `trg_stock_reservations_balance` (reserved = Σ ACTIVE), `ck_order_item_event_reason` (REJECTED cần lý do). |
| **9 · Lỗi và rollback** | Thiếu suất hoặc thiếu bất kỳ nguyên liệu nào → savepoint hoàn tác mọi phần đã giữ rồi ghi REJECTED kèm lý do (đây là kết quả hợp lệ, không phải lỗi SQL). RC422/RC409 cho dòng không tồn tại/sai bước. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G3.3 và G7: kiểm menu, quota, công thức; `needed = quantity × quantity_per_serving` theo từng loại; FEFO `usable_until ASC NULLS LAST, id`; ACCEPTED chỉ tăng reserved; thiếu → từ chối, rollback phần dở. I3.4 yêu cầu khóa dòng thực đơn rồi lô bếp có thứ tự — đúng. |
| **11 · Ngoại lệ** | Quota NULL vẫn kiểm nguyên liệu (QĐ-1). REJECTED không chiếm suất; ACCEPTED chiếm suất (QĐ-8). Món tạm dừng/thực đơn hết hạn bán → REJECTED. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 28 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: FNC-EXP, MENU-02, ORDER-02, QĐ-1, QĐ-2, QĐ-8, RETURN-02). File 06: C1, C2, C3, C4, C5. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. QĐ-1 (quota NULL/0), QĐ-8 (suất suy ra bằng `rc_quota_used`, gồm CANCELLED sau COOKING), khóa lô gom lên đầu theo (loại, id), mã lỗi. |

#### `rc_reject_item` — nhóm 3.3 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_reject_item(p_item, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Dòng ở PENDING; lý do không rỗng. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). |
| **4 · Bảng đọc / ghi** | W U `order_items` → REJECTED, C `order_item_events` (có `reason`). |
| **5 · Trạng thái trước → sau** | PENDING → REJECTED. |
| **6 · Khóa hàng và transaction** | `order_items` FOR UPDATE (5) trong `rc_item_transition`. |
| **7 · Idempotency** | Lần hai → RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_order_item_event_reason` (REJECTED/CANCELLED cần lý do không rỗng — đã siết bằng `btrim`), `trg_order_item_events_chain`, `trg_order_items_guard`. |
| **9 · Lỗi và rollback** | RC422 (thiếu lý do), RC409 (sai bước). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G3.3: REJECTED phải có lý do, không tính vào hóa đơn. |
| **11 · Ngoại lệ** | REJECTED không chiếm suất (QĐ-8). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 lời gọi trong ca đúng, 4 lời gọi bị chặn (ca: INV-01, QĐ-8). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Hàm mới của DB-34; vòng này thêm kiểm lý do rỗng (baseline để chuỗi trắng lọt qua CHECK `IS NOT NULL`). |

#### `rc_start_cooking` — nhóm 3.3 · 3.4 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_start_cooking(p_item, p_actor)` → void |
| **2 · Điều kiện trước** | Dòng ở ACCEPTED, không cờ `has_stock_exception`; mọi lô đang giữ còn dùng được và còn hạn. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). |
| **4 · Bảng đọc / ghi** | R `stock_reservations`, `kitchen_batches` · W U `kitchen_batches` (on_hand −q, reserved −q), U `stock_reservations` → CONSUMED, C `stock_movements` (COOK, −q), U `order_items`, C `order_item_events`. |
| **5 · Trạng thái trước → sau** | ACCEPTED → COOKING; reservation ACTIVE → CONSUMED. |
| **6 · Khóa hàng và transaction** | `order_items` (5) → `kitchen_batches` (9) theo (loại, id). |
| **7 · Idempotency** | Theo trạng thái: lần hai → RC409. `stock_movements.event_key = COOK:<id reservation>` UNIQUE là hàng rào thứ hai chống trừ hai lần. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_movement_type_shape`, `stock_movements_event_key_key`, `trg_kitchen_batches_balance` và `trg_stock_movements_balance` (số dư = sổ tại COMMIT). |
| **9 · Lỗi và rollback** | RC409 (sai bước, lô giữ hỏng/hết hạn). Rollback toàn bộ — không có COOK nửa chừng. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G7.5 và E2: COOKING giảm đồng thời on_hand và reserved, CONSUMED, ghi COOK đúng lô; từ đây không hoàn nguyên liệu (M5). |
| **11 · Ngoại lệ** | Lô giữ bị đánh dấu hỏng/hết hạn → RC409, phải `rc_reallocate_item` hoặc hủy món. Từ COOKING quota không còn hoàn (QĐ-2/QĐ-8). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 9 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: EXC-01, FNC-EXP, ORDER-03, QĐ-2). File 06: C3, C4, C5. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thứ tự khóa lô thống nhất (loại, id) với `rc_allocate_item`; mã lỗi. |

#### `rc_finish_dish` — nhóm 3.3 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_finish_dish(p_item, p_actor)` → void |
| **2 · Điều kiện trước** | Dòng ở COOKING. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). |
| **4 · Bảng đọc / ghi** | W U `order_items` → READY, C `order_item_events`. |
| **5 · Trạng thái trước → sau** | COOKING → READY. |
| **6 · Khóa hàng và transaction** | `order_items` (5). |
| **7 · Idempotency** | Lần hai → RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `trg_order_items_guard` (đồ thị chuyển trạng thái), `trg_order_item_events_chain`. |
| **9 · Lỗi và rollback** | RC409 nếu sai bước. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G3.4 / E2. |
| **11 · Ngoại lệ** | Không đụng kho (tiêu hao đã ghi ở bước COOKING). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 4 lời gọi trong ca đúng, 1 lời gọi bị chặn (ca: EXC-01, ORDER-03). File 06: C5. |
| **Thay đổi so với baseline** | Không đổi so với baseline. Không đổi (hàm giữ nguyên, dùng `rc_item_transition` đã có mã lỗi). |

#### `rc_serve_item` — nhóm 3.3 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_serve_item(p_item, p_actor)` → void |
| **2 · Điều kiện trước** | Dòng ở READY. |
| **3 · Actor và quyền** | Waiter (ứng dụng). |
| **4 · Bảng đọc / ghi** | W U `order_items` → SERVED, C `order_item_events`. |
| **5 · Trạng thái trước → sau** | READY → SERVED. |
| **6 · Khóa hàng và transaction** | `order_items` (5). |
| **7 · Idempotency** | Lần hai → RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `trg_order_items_guard`, `trg_order_item_events_chain`. |
| **9 · Lỗi và rollback** | RC409 nếu sai bước. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G3.4 (Waiter đánh dấu SERVED). |
| **11 · Ngoại lệ** | SERVED là điều kiện để dòng vào hóa đơn (G9). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 lời gọi trong ca đúng, 1 lời gọi bị chặn (ca: EXC-01, INV-01). File 06: C5. |
| **Thay đổi so với baseline** | Không đổi so với baseline. Không đổi so với baseline. |

#### `rc_cancel_item` — nhóm 3.3 · 3.4 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_cancel_item(p_item, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Dòng ở PENDING hoặc ACCEPTED; lý do không rỗng. |
| **3 · Actor và quyền** | Kitchen/Waiter theo quyền (ứng dụng). Từ COOKING trở đi hàm này luôn từ chối kể cả Manager. |
| **4 · Bảng đọc / ghi** | R `order_items`, `stock_reservations` · W U `stock_reservations` → RELEASED, U `kitchen_batches.reserved_quantity` (−), U `order_items` → CANCELLED, C `order_item_events`. |
| **5 · Trạng thái trước → sau** | PENDING/ACCEPTED → CANCELLED; reservation ACTIVE → RELEASED (không tạo biến động vật lý). |
| **6 · Khóa hàng và transaction** | `order_items` (5) → `kitchen_batches` (9) theo (loại, id) qua `rc_release_item_reservations`. |
| **7 · Idempotency** | Lần hai → RC409; suất giải phóng đúng một lần vì dòng rời tập "đã chiếm" đúng một lần. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_order_item_event_reason`, `trg_stock_reservations_balance`, `ck_reservation_close_fields`, `ck_reservation_release_reason`. |
| **9 · Lỗi và rollback** | RC422 (thiếu lý do), RC409 (đã COOKING/READY/SERVED: "chỉ Manager hủy ngoại lệ"). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G3.5, G7.6 và E2: chỉ PENDING/ACCEPTED, có lý do, RELEASED chỉ giảm reserved. |
| **11 · Ngoại lệ** | Hủy ACCEPTED trả lại suất (QĐ-8). Hủy một phần số lượng không có (QĐ-5 giữ hiện trạng: hủy cả dòng rồi gửi lại). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 10 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: FNC-EXP, MENU-02, ORDER-02, QĐ-1, QĐ-2, QĐ-8, RETURN-02). File 06: C4. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. QĐ-2: lý do bắt buộc, thông điệp trỏ tới hủy ngoại lệ; dùng chung `rc_release_item_reservations` (thứ tự khóa chuẩn). |

#### `rc_cancel_item_exception` — nhóm 3.3 · hàm bổ sung vòng chốt (QĐ-2/QĐ-3)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_cancel_item_exception(p_item, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Người gọi có vai trò MANAGER đang hoạt động; lý do không rỗng; dòng ở COOKING, READY hoặc SERVED; đơn OPEN; hóa đơn chưa có hoặc DRAFT (PAYMENT_PENDING phải mở lại trước; PAID/UNCOLLECTED không áp dụng). |
| **3 · Actor và quyền** | MANAGER — DB cưỡng chế bằng `rc_require_role` (nhân viên ACTIVE, tài khoản ACTIVE, đúng vai trò). |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `order_items` · W U `order_items` → CANCELLED, C `order_item_events` (người, lý do), D `invoice_items` (dòng bị hủy), U `invoices.total`. Không ghi `stock_movements`, `kitchen_batches`, `stock_reservations`. |
| **5 · Trạng thái trước → sau** | COOKING/READY/SERVED → CANCELLED. Reservation giữ nguyên CONSUMED. |
| **6 · Khóa hàng và transaction** | `orders` (1) → `invoices` (2) → `order_items` (5): tuần tự với chốt hóa đơn và thanh toán. |
| **7 · Idempotency** | Lần hai → RC409 ("chỉ áp dụng từ COOKING"). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `trg_order_items_guard` (COOKING/READY/SERVED → CANCELLED), `trg_invoice_items_guard` (chỉ xóa dòng khi hóa đơn DRAFT), `trg_orders_guard`, bất biến I-14 (hủy ngoại lệ luôn do Manager và có lý do). |
| **9 · Lỗi và rollback** | RC403 (không đúng vai trò), RC422 (thiếu lý do), RC409 (đơn đã đóng, hóa đơn không DRAFT, sai bước). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Không có trong v4: spec E2/M5 cấm hủy sau COOKING và không có luồng miễn phí. QĐ-2 của nhóm bổ sung ngoại lệ có kiểm soát, không tự ý ghi đè phần còn lại của spec. |
| **11 · Ngoại lệ** | Không hoàn nguyên liệu đã tiêu hao, không hoàn quota (`rc_quota_used` vẫn đếm CANCELLED sau COOKING), món không tính tiền. Sau khi hóa đơn PAID đơn đã CLOSED nên không hủy được. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 6 lời gọi trong ca đúng, 9 lời gọi bị chặn (ca: QĐ-2, QĐ-3, QĐ-8). File 06: C8, C8b. |
| **Thay đổi so với baseline** | Hàm mới (không có trong baseline). Hàm mới (QĐ-2). |

#### `rc_mark_batch_unusable` — nhóm 4.1 · 4.5 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_mark_batch_unusable(p_kind, p_batch, p_actor, p_reason)` → INT (số món bị bật cờ) |
| **2 · Điều kiện trước** | `p_kind` là RAW hoặc KITCHEN; lô tồn tại và còn dùng được; lý do không rỗng. |
| **3 · Actor và quyền** | Manager (lô RAW) hoặc Kitchen (lô bếp) theo ứng dụng. Spec G7.8 đặt ngoại lệ lô đang giữ dưới quyền Manager — DB không cưỡng chế vai trò; xem mục 8. |
| **4 · Bảng đọc / ghi** | W U `inventory_batches` hoặc `kitchen_batches` (`is_usable = false`, lý do, người, giờ); lô bếp đang giữ: U `order_items.has_stock_exception = true`. |
| **5 · Trạng thái trước → sau** | Lô `is_usable`: true → false (một chiều). Món đang giữ lô: bật cờ ngoại lệ, không tự xóa reservation. |
| **6 · Khóa hàng và transaction** | Lô bếp: các `order_items` đang giữ (5) rồi `kitchen_batches` (9). Lô RAW: một dòng. |
| **7 · Idempotency** | Lần hai → RC409 ("đã đánh dấu"). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_kitchen_batch_unusable_fields`, `ck_inventory_batch_unusable_fields`, `ck_order_item_exception_accepted`. |
| **9 · Lỗi và rollback** | RC422 (loại sai, thiếu lý do), RC409 (lô không tồn tại hoặc đã đánh dấu). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G7.8: lô hỏng không được dùng kể cả số dư dương; không xóa reserve ngầm. |
| **11 · Ngoại lệ** | Không đổi số dư (giảm tồn bằng WASTE ở 4.7). Lô RAW không dùng được bị `rc_issue_stock` từ chối. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 4 lời gọi trong ca đúng, 4 lời gọi bị chặn (ca: EXC-01, FNC-EXP). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thêm kiểm lý do rỗng; mã lỗi. |

#### `rc_reallocate_item` — nhóm 3.4 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_reallocate_item(p_item, p_actor)` → void |
| **2 · Điều kiện trước** | Dòng ở ACCEPTED (thường đang mang cờ ngoại lệ). |
| **3 · Actor và quyền** | Kitchen (ứng dụng); spec G7.8 gắn ngoại lệ với Manager — xem mục 8. |
| **4 · Bảng đọc / ghi** | W U `stock_reservations` (lô hỏng/hết hạn → RELEASED `BATCH_UNUSABLE`), C `stock_reservations` mới, U `kitchen_batches.reserved_quantity`, U `order_items.has_stock_exception = false`. |
| **5 · Trạng thái trước → sau** | Reservation cũ ACTIVE → RELEASED; reservation mới ACTIVE từ lô khác; cờ xóa. |
| **6 · Khóa hàng và transaction** | `order_items` (5) → mọi lô đang giữ và mọi lô còn dùng được của các loại trong công thức, theo (loại, id) (9). |
| **7 · Idempotency** | Gọi khi đã cấp lại: không còn phần giữ hỏng nên không đổi gì; sai bước → RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_reservation_release_reason`, `trg_stock_reservations_balance`, `ck_kitchen_reserved_le_on_hand`. |
| **9 · Lỗi và rollback** | Vẫn thiếu nguyên liệu → RC001, cả lệnh rollback (phần giữ cũ còn nguyên); Kitchen hủy món. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G7.8 (không xóa reserve ngầm: có release_reason và dấu vết). |
| **11 · Ngoại lệ** | Lô thay thế hết hạn/không dùng được không được chọn. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 1 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: EXC-01, FNC-EXP). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Khóa trước toàn bộ lô ứng viên theo (loại, id) thay vì chỉ lô đang giữ (chống deadlock với `rc_accept_item`). |

### 4.4 Hóa đơn và thanh toán (3.5, 3.6, 3.7)

#### `rc_checkout_invoice` — nhóm 3.5 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_checkout_invoice(p_order, p_actor)` → BIGINT (id hóa đơn) |
| **2 · Điều kiện trước** | Đơn OPEN; hóa đơn chưa có hoặc DRAFT; mọi dòng món ∈ {SERVED, CANCELLED, REJECTED}; có ít nhất một dòng SERVED; tổng > 0. |
| **3 · Actor và quyền** | Cashier (ứng dụng, spec A2). DB ghi `created_by_id`. Tổng tiền do hàm tự tính, không nhận từ client. |
| **4 · Bảng đọc / ghi** | R `orders`, `order_items`, `order_batches`, `invoices`, `invoice_items` · W C `invoices` (nếu chưa có), C `invoice_items` (mỗi dòng SERVED chưa có), U `invoices` (total, → PAYMENT_PENDING). |
| **5 · Trạng thái trước → sau** | Hóa đơn DRAFT → PAYMENT_PENDING (đóng băng chi tiết). |
| **6 · Khóa hàng và transaction** | `orders` FOR UPDATE (1) → `invoices` FOR UPDATE (2): chặn gửi món chen vào, hủy ngoại lệ, thanh toán. |
| **7 · Idempotency** | Lần hai → RC409 (hóa đơn không còn DRAFT). Dòng đã vào hóa đơn không thêm lần hai (`invoice_items.order_item_id` UNIQUE). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `invoice_items_order_item_id_key`, `trg_invoice_items_guard` (chỉ bản chụp đúng của dòng SERVED cùng đơn), `trg_invoice_guard` (khóa total khi PAYMENT_PENDING), bất biến I-4. |
| **9 · Lỗi và rollback** | RC409 (đơn không mở, còn món chưa xong, không có món thu, hóa đơn lệch đơn), RC422 (tổng ≤ 0). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G9: tạo đủ `invoice_items`, đối chiếu tổng và không sót dòng SERVED, PAYMENT_PENDING đóng băng. I3.5 yêu cầu khóa `orders` — đúng. |
| **11 · Ngoại lệ** | Không chốt hóa đơn 0 đồng (dùng `rc_close_empty_session`). Hóa đơn DRAFT sau khi mở lại hoặc hủy ngoại lệ được chốt lại trên cùng bản ghi. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 11 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: EXC-01, INV-01, QĐ-2, QĐ-4). File 06: C5, C8, C8b. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. QĐ-4: khóa orders → invoices; `rc_invoice_total_checked` đối chiếu từng dòng hóa đơn với dòng SERVED (baseline chỉ cộng tổng); mã lỗi. |

#### `rc_reopen_invoice` — nhóm 3.5 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_reopen_invoice(p_invoice, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Hóa đơn PAYMENT_PENDING; lý do không rỗng. |
| **3 · Actor và quyền** | Cashier (ứng dụng). |
| **4 · Bảng đọc / ghi** | W U `payment_requests` (yêu cầu đang mở → CANCELLED + lý do, người, giờ), U `invoices` → DRAFT. |
| **5 · Trạng thái trước → sau** | Hóa đơn PAYMENT_PENDING → DRAFT; request REQUESTED/WAITING_TRANSFER → CANCELLED. |
| **6 · Khóa hàng và transaction** | `rc_lock_invoice`: `orders` (1) → `invoices` (2) → `payment_requests` đang mở (3). |
| **7 · Idempotency** | Lần hai → RC409 (không còn PAYMENT_PENDING). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_payment_request_cancel_fields`, `trg_payment_requests_guard`, `trg_invoice_guard`. |
| **9 · Lỗi và rollback** | RC422 (thiếu lý do), RC409 (sai trạng thái). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G9.5 và E2: PAYMENT_PENDING → DRAFT theo luồng mở lại; PAID không mở lại. |
| **11 · Ngoại lệ** | QR đã quét kịp chuyển tiền sau khi request bị hủy → webhook AFTER_CANCEL (sự cố đối soát, không mất dấu). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: QĐ-2, QĐ-3, QĐ-4). File 06: C8. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Lý do bắt buộc; khóa chuẩn qua `rc_lock_invoice` (baseline khóa invoices rồi mới gặp orders ở nhánh khác → deadlock). |

#### `rc_pay_cash` — nhóm 3.6 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_pay_cash(p_invoice, p_actor, p_cash_received)` → BIGINT (id payment) |
| **2 · Điều kiện trước** | Hóa đơn PAYMENT_PENDING; không có request QR đang mở; `p_cash_received ≥ total`. |
| **3 · Actor và quyền** | Cashier (ứng dụng). DB ghi `cashier_id`. |
| **4 · Bảng đọc / ghi** | W C `payment_requests` (CASH, PAID), C `payments` (amount = số phải thu, `business_date`), U `invoices` → PAID, U `orders` → CLOSED, U `table_sessions` → CLOSED, U `dining_tables` → AVAILABLE. |
| **5 · Trạng thái trước → sau** | Hóa đơn PAYMENT_PENDING → PAID; đơn, phiên đóng; bàn trống. |
| **6 · Khóa hàng và transaction** | `rc_lock_invoice`: `orders` (1) → `invoices` (2) → `payment_requests` (3); `rc_settle_invoice` lấy tiếp phiên/bàn (4). |
| **7 · Idempotency** | Lần hai (kể cả đồng thời) → đúng một thành công, bên kia RC409. `payments.invoice_id` UNIQUE là hàng rào cuối. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `payments_invoice_id_key`, `payments_payment_request_id_key`, `trg_payments_guard` (chỉ ghi khi PAYMENT_PENDING, đúng số phải thu), `ck_payment_method_fields`, `fk_payments_request_invoice_method`. |
| **9 · Lỗi và rollback** | RC409 (sai trạng thái, QR đang mở), RC422 (tiền thiếu). Rollback toàn bộ — không có PAID nửa chừng. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G10: `payments.amount` bằng `amount_due`, không phải tiền khách đưa; đóng đơn/phiên/bàn trong một transaction. I3.6. |
| **11 · Ngoại lệ** | Tiền thừa chỉ nằm ở `payment_requests.change_amount` (cột GENERATED). |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 4 lời gọi bị chặn (ca: CASH-01, EXC-01, QĐ-2, QĐ-3, QĐ-4, TABLE-02). File 06: C6, C8. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Khóa chuẩn orders → invoices (sửa deadlock với chốt hóa đơn/gửi món); mã lỗi. |

#### `rc_start_qr` — nhóm 3.7 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_start_qr(p_invoice, p_actor)` → TEXT (mã tham chiếu) |
| **2 · Điều kiện trước** | Hóa đơn PAYMENT_PENDING; chưa có request đang mở. |
| **3 · Actor và quyền** | Cashier (ứng dụng). |
| **4 · Bảng đọc / ghi** | W C `payment_requests` (QR, WAITING_TRANSFER, `payment_reference` UNIQUE). |
| **5 · Trạng thái trước → sau** | Request mới WAITING_TRANSFER. |
| **6 · Khóa hàng và transaction** | `rc_lock_invoice`: orders (1) → invoices (2) → payment_requests (3). |
| **7 · Idempotency** | Lần hai khi request còn mở → RC409. `ux_payment_request_active` cho phép tối đa một request mở mỗi hóa đơn. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ux_payment_request_active`, `payment_requests_payment_reference_key`, `trg_payment_requests_guard` (amount_due = total). |
| **9 · Lỗi và rollback** | RC409. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G10 và E2: một request mở mỗi hóa đơn, mã tham chiếu duy nhất. |
| **11 · Ngoại lệ** | Mã tham chiếu chỉ gồm chữ và số để đưa vào nội dung chuyển khoản. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: QR-02, QĐ-2, QĐ-3, QĐ-4, TABLE-02). File 06: C6, C7, C8. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Khóa chuẩn; mã lỗi. |

#### `rc_process_verified_qr` — nhóm 3.7 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_process_verified_qr(p_transaction_code, p_reference, p_amount, p_payload)` → TEXT (PROCESSED, MISMATCH hoặc AFTER_CANCEL) |
| **2 · Điều kiện trước** | Backend đã xác thực chữ ký/API key/nguồn SePay trước khi gọi. Tham số không NULL, mã giao dịch không rỗng. |
| **3 · Actor và quyền** | Role webhook (chỉ được EXECUTE hàm này — kiểm bằng test ACL). Không có `p_actor`: người thao tác là Cashier của request. |
| **4 · Bảng đọc / ghi** | W C `payment_webhook_events`; nếu PROCESSED: C `payments` (QR), U `payment_requests` → PAID, U `invoices` → PAID, U `orders`/`table_sessions` → CLOSED, U `dining_tables` → AVAILABLE. |
| **5 · Trạng thái trước → sau** | Kết quả: PROCESSED (khớp mã, số tiền, request WAITING_TRANSFER, hóa đơn PAYMENT_PENDING); MISMATCH (sai mã/số tiền); AFTER_CANCEL (request đã hủy hoặc hóa đơn không còn chờ). |
| **6 · Khóa hàng và transaction** | Tìm request theo mã tham chiếu rồi `rc_lock_invoice`: orders (1) → invoices (2) → payment_requests (3); đọc lại kết quả theo `provider_transaction_code` sau khi giữ khóa. |
| **7 · Idempotency** | Có và an toàn khi đồng thời: kiểm trước khóa, kiểm lại sau khóa, ghi bằng `ON CONFLICT (provider_transaction_code) DO NOTHING`; webhook lặp trả đúng kết quả đã lưu (backend trả 200), không tạo payment thứ hai. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `payment_webhook_events_provider_transaction_code_key`, `payments_payment_webhook_event_id_key`, `ck_payments_qr_webhook`, `fk_payments_webhook_request`, `trg_webhook_event_guard` (chứng cứ bất biến, sự cố tự mở). |
| **9 · Lỗi và rollback** | RC422 (tham số thiếu). Lỗi giữa chừng rollback cả sự kiện lẫn thanh toán — SePay gửi lại sẽ được xử lý đúng. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G10 và I3.7: chống replay bằng UNIQUE mã giao dịch; QR đến muộn lưu thành sự cố đối soát, không im lặng. |
| **11 · Ngoại lệ** | MISMATCH/AFTER_CANCEL không tạo payment; trigger mở sự cố (`resolution_status = OPEN`) cho Manager xử lý. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 11 lời gọi trong ca đúng, 1 lời gọi bị chặn (ca: DB-12, QR-01, QR-02, QĐ-3, QĐ-4). File 06: C6, C7, C8. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Sửa lỗi thật: hai webhook trùng đến cùng lúc làm bên sau nhận 23505 (reproduce trên PostgreSQL 17.5); khóa chuẩn; kiểm tham số. |

#### `rc_cancel_payment_request` — nhóm 3.6 · 3.7 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_cancel_payment_request(p_request, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Request ở REQUESTED hoặc WAITING_TRANSFER; lý do không rỗng. |
| **3 · Actor và quyền** | Cashier (ứng dụng). DB ghi `cancelled_by_id`. |
| **4 · Bảng đọc / ghi** | W U `payment_requests` → CANCELLED (lý do, người, giờ). |
| **5 · Trạng thái trước → sau** | REQUESTED/WAITING_TRANSFER → CANCELLED. |
| **6 · Khóa hàng và transaction** | `rc_lock_invoice` (1→2→3) rồi khóa chính request. |
| **7 · Idempotency** | Lần hai → RC409 (không còn mở). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_payment_request_cancel_fields`, `trg_payment_requests_guard` (PAID/CANCELLED bất biến). |
| **9 · Lỗi và rollback** | RC422 (thiếu lý do, request không tồn tại), RC409. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec E2: active → CANCELLED; QR đến sau đó vào audit. |
| **11 · Ngoại lệ** | Hủy lúc webhook đang đến: đúng một bên thắng (PAID hoặc CANCELLED), không vừa thu vừa hủy. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 1 lời gọi trong ca đúng, 3 lời gọi bị chặn (ca: DB-12). File 06: C6. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Lý do bắt buộc; khóa chuẩn thay vì chỉ invoice → request; mã lỗi. |

### 4.5 Kho RAW, bếp, sơ chế, kiểm kê (4.2, 4.3, 4.4, 4.7)

#### `rc_confirm_receipt` — nhóm 4.2 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_confirm_receipt(p_receipt, p_actor, p_received_at DEFAULT now())` → void |
| **2 · Điều kiện trước** | Phiếu DRAFT, không rỗng; nguyên liệu còn `is_active`; nguyên liệu theo dõi hạn dùng phải có `expiry_date`; không nhận lô đã quá hạn (theo ngày kinh doanh). |
| **3 · Actor và quyền** | Manager (ứng dụng). DB ghi `confirmed_by_id`. |
| **4 · Bảng đọc / ghi** | R `stock_receipts`, `stock_receipt_items`, `ingredients` · W U `stock_receipts` → CONFIRMED, C `inventory_batches` (mỗi dòng một lô), C `stock_movements` (RECEIPT, +). |
| **5 · Trạng thái trước → sau** | Phiếu DRAFT → CONFIRMED. |
| **6 · Khóa hàng và transaction** | `stock_receipts` FOR UPDATE; lô RAW mới nên không cần khóa tài nguyên khác. |
| **7 · Idempotency** | Lần hai (kể cả đồng thời) → RC409 hoặc lỗi trigger; `inventory_batches.stock_receipt_item_id` UNIQUE và `event_key = RECEIPT:<dòng>` UNIQUE chặn lô/sổ trùng — ba lớp bảo vệ. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `inventory_batches_stock_receipt_item_id_key`, `stock_movements_event_key_key`, `ck_stock_receipt_confirm_fields`, `trg_stock_receipts_guard`, `trg_stock_receipt_items_lines`, `trg_inventory_batches_balance`. |
| **9 · Lỗi và rollback** | RC422 (rỗng, nguyên liệu ngừng dùng, thiếu hạn, lô quá hạn), RC409 (không còn DRAFT). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G4 và I3.1: lock phiếu trước khi xác nhận, sự kiện một lần, hai đợt nhập là hai lô, quy đổi `input × conversion_factor`, không tự sinh khoản chi. |
| **11 · Ngoại lệ** | Lô đã quá hạn không được nhận vào kho. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 7 lời gọi trong ca đúng, 6 lời gọi bị chặn (ca: RAW-01, RAW-03). File 06: C9, C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thêm kiểm nguyên liệu ngừng dùng và lô quá hạn; mã lỗi. |

#### `rc_issue_stock` — nhóm 4.3 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_issue_stock(p_issue, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu DRAFT, không rỗng; dòng DIRECT phải có loại bếp DIRECT đang dùng; mỗi lô dùng được, còn hạn, đủ số lượng; xuất đúng FEFO. |
| **3 · Actor và quyền** | Manager (ứng dụng). DB ghi `issued_by_id`. |
| **4 · Bảng đọc / ghi** | R `stock_issues`, `stock_issue_items`, `inventory_batches`, `stock_receipt_items`, `kitchen_ingredient_types` · W U `inventory_batches` (−), C `stock_movements` (ISSUE_OUT), U `stock_issues` → ISSUED. |
| **5 · Trạng thái trước → sau** | Phiếu DRAFT → ISSUED; RAW giảm, bếp chưa tăng (vật tư đang chuyển giao, không phải COOK). |
| **6 · Khóa hàng và transaction** | `stock_issues` FOR UPDATE → mọi lô RAW của các nguyên liệu liên quan theo `id` (7) để kiểm FEFO không bị chen. |
| **7 · Idempotency** | Lần hai → RC409. `event_key = ISSUE_OUT:<dòng>` UNIQUE. Hai phiếu cùng lô: serial hóa bằng khóa lô, tổng không vượt tồn. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ux_stock_issue_batch_mode`, `ck_issue_item_wip_bounds`, `trg_inventory_batches_balance`, `trg_stock_issue_items_lines`. |
| **9 · Lỗi và rollback** | RC422 (rỗng, DIRECT thiếu loại bếp), RC409 (lô không dùng được/hết hạn, thiếu số lượng, sai FEFO). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G5, I3.2 và M9: lock chứng từ và lô theo thứ tự xác định, đủ và đúng FEFO, trừ vật lý và ghi sổ nguyên tử. |
| **11 · Ngoại lệ** | FEFO: lô có hạn xuất trước lô không hạn; cùng hạn thì lô nhận trước. Lô hết hạn hoặc không dùng được bị từ chối dù số dư dương. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 9 lời gọi trong ca đúng, 9 lời gọi bị chặn (ca: EXC-01, FNC-EXP, FNC-FEFO, ISSUE-01, RAW-02, RAW-03). File 06: C9, C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thêm kiểm DIRECT cần loại bếp ngay lúc xuất (baseline chỉ lỗi ở bước bếp nhận, khi RAW đã bị trừ); mã lỗi. Kiểm lô hết hạn/không dùng được đã có từ baseline nhưng chưa có test — nay đã có. |

#### `rc_receive_issue` — nhóm 4.3 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_receive_issue(p_issue, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu ISSUED; nhận toàn bộ hoặc không (không nhận một phần); dòng DIRECT có loại bếp DIRECT. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). DB ghi `received_by_id`. |
| **4 · Bảng đọc / ghi** | W U `stock_issue_items.wip_remaining_quantity` (PREPARE), C `kitchen_batches` (DIRECT), C `stock_movements` (ISSUE_RECEIVED hoặc DIRECT_IN), U `stock_issues` → RECEIVED. |
| **5 · Trạng thái trước → sau** | Phiếu ISSUED → RECEIVED; WIP hoặc lô bếp DIRECT bắt đầu tồn tại. |
| **6 · Khóa hàng và transaction** | `stock_issues` FOR UPDATE; lô bếp và WIP mới tạo/được bồi trong chính transaction. |
| **7 · Idempotency** | Nhận một lần: lần hai → RC409. `event_key` UNIQUE; `kitchen_batches.direct_issue_item_id` UNIQUE. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_issue_item_wip_bounds`, `kitchen_batches_direct_issue_item_id_key`, `trg_stock_issue_items_balance`, `trg_kitchen_batches_balance`. |
| **9 · Lỗi và rollback** | RC422 (phiếu không tồn tại, thiếu loại bếp), RC409 (không phải ISSUED). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G5: PREPARE cộng WIP, DIRECT tạo lô bếp, không nhập chung số dư giữa các giai đoạn. |
| **11 · Ngoại lệ** | Hàng đến thiếu: hủy phiếu và lập phiếu mới, không nhận một phần. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 5 lời gọi trong ca đúng, 3 lời gọi bị chặn (ca: EXC-01, ISSUE-03). File 06: C9, C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Mã lỗi (RC422/RC409). |

#### `rc_cancel_issue` — nhóm 4.3 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_cancel_issue(p_issue, p_actor, p_reason)` → void |
| **2 · Điều kiện trước** | Phiếu DRAFT hoặc ISSUED; lý do không rỗng. Phiếu RECEIVED không hủy lùi (dùng phiếu hoàn trả). |
| **3 · Actor và quyền** | Manager (ứng dụng). DB ghi `cancelled_by_id`. |
| **4 · Bảng đọc / ghi** | ISSUED: W U `inventory_batches` (+), C `stock_movements` (ISSUE_REVERSAL) · U `stock_issues` → CANCELLED (lý do, người, giờ). |
| **5 · Trạng thái trước → sau** | DRAFT/ISSUED → CANCELLED. |
| **6 · Khóa hàng và transaction** | `stock_issues` FOR UPDATE → `inventory_batches` theo `id` (7). |
| **7 · Idempotency** | Lần hai → RC409. `event_key = ISSUE_REVERSAL:<dòng>` UNIQUE. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_stock_issue_fields`, `trg_stock_issues_guard`, `trg_inventory_batches_balance`. |
| **9 · Lỗi và rollback** | RC422, RC409. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec E2 và G5: hủy ISSUED hoàn RAW bằng sổ ISSUE_REVERSAL, không xóa bản ghi xuất cũ. |
| **11 · Ngoại lệ** | Phiếu đã nhận không hủy được. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 lời gọi trong ca đúng, 4 lời gọi bị chặn (ca: COUNT-01, ISSUE-02, RAW-03). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Lý do bắt buộc; mã lỗi. |

#### `rc_confirm_preparation` — nhóm 4.4 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_confirm_preparation(p_prep, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu sơ chế DRAFT; dòng xuất là PREPARE của phiếu RECEIVED; đầu vào ≤ WIP còn; 0 < Σ đầu ra ≤ đầu vào; mọi đầu ra là loại PREPARED của cùng nguyên liệu gốc; hạn đầu ra không vượt hạn lô gốc. |
| **3 · Actor và quyền** | Kitchen (ứng dụng). DB ghi `prepared_by_id`. |
| **4 · Bảng đọc / ghi** | W U `stock_issue_items.wip_remaining_quantity` (−), C `stock_movements` (PREP_INPUT, PREP_OUTPUT), C `kitchen_batches` (mỗi đầu ra một lô), U `kitchen_preparation_outputs.usable_until`, U `kitchen_preparations` → CONFIRMED. |
| **5 · Trạng thái trước → sau** | DRAFT → CONFIRMED; WIP giảm, tồn bếp PREPARED tăng. |
| **6 · Khóa hàng và transaction** | `kitchen_preparations` FOR UPDATE → dòng WIP `stock_issue_items` FOR UPDATE (8). |
| **7 · Idempotency** | Lần hai → RC409. Hai phiếu cùng vượt WIP: một thắng, một RC409. `request_key` UNIQUE chống tạo phiếu trùng. Hao hụt = đầu vào − Σ đầu ra là giá trị suy ra, không ghi sổ thêm. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_issue_item_wip_bounds`, `ux_prep_output_id_type`, `fk_kitchen_batch_output_type`, `kitchen_batches_preparation_output_id_key`, `trg_stock_issue_items_balance`. |
| **9 · Lỗi và rollback** | RC422/RC409. Rollback toàn bộ — không có PREP_INPUT mà thiếu PREP_OUTPUT. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G6 và I3.3: tổng đầu ra > 0 và ≤ đầu vào, truy vết tới một dòng xuất và một lô RAW; QĐ-6 giữ quy ước một đơn vị `base_unit`. |
| **11 · Ngoại lệ** | Hao hụt sơ chế tách biệt với WASTE bảo quản. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 2 lời gọi trong ca đúng, 4 lời gọi bị chặn (ca: DB-24, EXC-01, PREP-02, PREP-03). File 06: C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Mã lỗi (RC422/RC409). |

#### `rc_confirm_return` — nhóm 4.3 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_confirm_return(p_return, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu trả DRAFT, không rỗng; dòng thuộc phiếu xuất RECEIVED; PREPARE: lượng ≤ WIP còn; DIRECT: chỉ rõ lô bếp và lượng ≤ khả dụng (on_hand − reserved) của lô còn dùng được; không trả lô PREPARED. |
| **3 · Actor và quyền** | Manager xác nhận (ứng dụng); Kitchen đề xuất. DB ghi `confirmed_by_id`. |
| **4 · Bảng đọc / ghi** | W U `stock_issue_items` hoặc `kitchen_batches` (−), U `inventory_batches` (+) đúng lô nguồn, C `stock_movements` (RETURN hai chiều), U `stock_returns` → CONFIRMED. |
| **5 · Trạng thái trước → sau** | Phiếu DRAFT → CONFIRMED. |
| **6 · Khóa hàng và transaction** | `stock_returns` FOR UPDATE → khóa trước toàn bộ tài nguyên theo thứ tự chung: `inventory_batches` (7) → `stock_issue_items` (8) → `kitchen_batches` (9), rồi mới ghi theo dòng phiếu. |
| **7 · Idempotency** | Lần hai → RC409. `request_key` UNIQUE; `event_key = RETURN-OUT/RETURN-IN:<dòng>` UNIQUE. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `fk_return_direct_batch_of_issue_item`, `ux_return_item_source`, `ck_issue_item_wip_bounds`, `ck_kitchen_reserved_le_on_hand`, các trigger số dư ↔ sổ. |
| **9 · Lỗi và rollback** | RC422, RC409 (vượt WIP, vượt khả dụng, phần đang giữ cho món). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G8 và I3.3: giảm nguồn bếp/WIP, tăng đúng RAW nguồn, sổ RETURN, PREPARED không trả ngược. |
| **11 · Ngoại lệ** | Phần đang giữ cho món (reserved) không trả được; hàng đã tiêu hao (COOK) không thể trả. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 1 lời gọi trong ca đúng, 2 lời gọi bị chặn (ca: RETURN-01, RETURN-02). File 06: C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Sửa lỗi thật: khóa tài nguyên theo thứ tự chung trước khi ghi (baseline khóa theo thứ tự dòng phiếu, hai phiếu ngược thứ tự deadlock — gỡ khóa trước ra thì 8/8 vòng deadlock); mã lỗi. |

#### `rc_confirm_adjustment` — nhóm 4.7 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_confirm_adjustment(p_adjustment, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu DRAFT, không rỗng; mỗi dòng đúng một nguồn (RAW, WIP hoặc bếp) và chỉ giảm; không vượt khả dụng (bếp: không xuống dưới phần đang giữ). |
| **3 · Actor và quyền** | Manager duyệt (ứng dụng); Kitchen đề xuất. DB ghi `confirmed_by_id`. |
| **4 · Bảng đọc / ghi** | W U `inventory_batches` / `stock_issue_items` / `kitchen_batches` (−), C `stock_movements` (WASTE, lý do = loại phiếu), U `stock_adjustments` → CONFIRMED. |
| **5 · Trạng thái trước → sau** | DRAFT → CONFIRMED. |
| **6 · Khóa hàng và transaction** | `stock_adjustments` FOR UPDATE → khóa trước mọi nguồn theo thứ tự chung RAW (7) → WIP (8) → bếp (9). |
| **7 · Idempotency** | Lần hai → RC409; `request_key` UNIQUE khi tạo phiếu; `event_key = WASTE:<dòng>` UNIQUE. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_adjust_delta_negative` (chỉ giảm), `ck_stock_adjust_one_source`, `ck_movement_type_shape`, các trigger số dư ↔ sổ. |
| **9 · Lỗi và rollback** | RC422 (rỗng), RC409 (vượt khả dụng). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G8: Manager duyệt, giảm số dư + WASTE, không đẩy sự cố bảo quản vào hao hụt sơ chế. |
| **11 · Ngoại lệ** | Lô bếp đang giữ cho món phải được cấp lại trước khi hủy hàng. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: DB-22, WASTE-01). File 06: C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Khóa trước tài nguyên theo thứ tự chung (chống deadlock); mã lỗi. |

#### `rc_confirm_count` — nhóm 4.7 · tên có trong v4 (spec I4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_confirm_count(p_count, p_actor)` → void |
| **2 · Điều kiện trước** | Phiếu DRAFT, không rỗng; `book_quantity` của mọi dòng phải bằng số dư hiện hành (không stale); bếp: thực đếm ≥ phần đang giữ. |
| **3 · Actor và quyền** | Manager duyệt (ứng dụng). DB ghi `confirmed_by_id`. |
| **4 · Bảng đọc / ghi** | W U số dư nguồn (RAW/WIP/bếp) về số thực đếm, C `stock_movements` (COUNT_ADJUST = thực đếm − sổ), U `stock_counts` → CONFIRMED. |
| **5 · Trạng thái trước → sau** | DRAFT → CONFIRMED. Dòng không chênh lệch không ghi sổ. |
| **6 · Khóa hàng và transaction** | `stock_counts` FOR UPDATE → khóa trước mọi nguồn theo thứ tự chung RAW (7) → WIP (8) → bếp (9). |
| **7 · Idempotency** | Lần hai → RC409. Phiếu thứ hai lập từ cùng số sổ cũ → RC409 (stale), không điều chỉnh hai lần. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_stock_count_one_source`, `ck_movement_type_shape`, các trigger số dư ↔ sổ. |
| **9 · Lỗi và rollback** | RC422 (rỗng), RC409 (stale, dưới phần đang giữ). Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G8: xác minh số sổ còn khớp snapshot, ghi `COUNT_ADJUST`, không tạo thêm phiếu hủy hàng cho cùng chênh lệch. |
| **11 · Ngoại lệ** | Phiếu kiểm kê không có `request_key` theo spec; tạo hai phiếu nháp là hai chứng từ, chỉ phiếu được xác nhận mới ghi sổ. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 lời gọi trong ca đúng, 5 lời gọi bị chặn (ca: COUNT-01, ISSUE-02). File 06: C10. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Khóa trước mọi nguồn thay vì khóa trong vòng lặp theo thứ tự dòng (chống deadlock); mã lỗi. |

### 4.6 Đối soát tiền mặt (5.3)

#### `rc_reconcile_cash` — nhóm 5.3 · hàm bổ sung DB-34

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_reconcile_cash(p_business_date, p_opening_cash, p_actual_cash, p_actor, p_reason DEFAULT NULL, p_revision_reason DEFAULT NULL)` → BIGINT (id bản đối soát) |
| **2 · Điều kiện trước** | Ngày kinh doanh không NULL và không ở tương lai; quỹ đầu ngày và tiền thực đếm không NULL; có chênh lệch thì phải có lý do; version > 1 thì phải có lý do sửa (CHECK). |
| **3 · Actor và quyền** | Manager (ứng dụng). DB ghi `reconciled_by_id`. |
| **4 · Bảng đọc / ghi** | R `payments`, `operating_expenses` · W U `daily_cash_reconciliations` (CURRENT cũ → SUPERSEDED), C bản mới (`version + 1`, CURRENT, `cash_sales`/`cash_expenses` tính từ dữ liệu thật). |
| **5 · Trạng thái trước → sau** | CURRENT → SUPERSEDED; bản mới CURRENT. |
| **6 · Khóa hàng và transaction** | `pg_advisory_xact_lock` theo ngày kinh doanh; UPDATE bản cũ trước INSERT bản mới (do `ux_cash_recon_current`). |
| **7 · Idempotency** | Không idempotent theo thiết kế (mỗi lần tạo một phiên bản). Bấm lặp mà không có lý do sửa bị CHECK 23514 chặn. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ux_cash_recon_current`, `ux_cash_recon_date_version`, `ck_recon_reason_when_diff`, `ck_recon_revision_reason`, `trg_cash_recon_guard` (chỉ CURRENT → SUPERSEDED). |
| **9 · Lỗi và rollback** | RC422, 23514. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Không có hàm trong v4 (DB-34). Khớp spec G11: chỉ tiền mặt của ngày, chỉ chi từ két, một bản CURRENT, bản cũ bất biến. |
| **11 · Ngoại lệ** | Doanh thu QR không vào quỹ tiền mặt; hóa đơn UNCOLLECTED không có payments nên không vào. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 2 lời gọi trong ca đúng, 3 lời gọi bị chặn (ca: FIN-02). File 06: không có ca đồng thời riêng. |
| **Thay đổi so với baseline** | Đã sửa so với baseline. Thêm kiểm tham số đầu vào (ngày tương lai, NULL). |

## 5. Hàm hỗ trợ và trigger

**12 hàm hỗ trợ** (không gọi trực tiếp từ ứng dụng):

| Hàm | Việc làm | Dùng bởi |
| --- | --- | --- |
| `rc_raise(code, message)` | Ném lỗi với SQLSTATE riêng | mọi hàm |
| `rc_require_role(actor, role)` | Nhân viên ACTIVE + tài khoản ACTIVE + đúng vai trò, nếu không RC403 | `rc_cancel_item_exception`, `rc_abandon_session` |
| `rc_business_date(ts)` | Ngày kinh doanh, chốt 04:00 giờ Việt Nam (DB-33) | thanh toán, chi phí, thực đơn ngày, đối soát, nhập kho |
| `rc_usable_until(expiry, shelf_hours)` | Hạn dùng tại bếp = sớm hơn của hết ngày hạn lô và bây giờ + hạn bảo quản | `rc_receive_issue`, `rc_confirm_preparation` |
| `rc_quota_used(dmi)` | Số suất đã chiếm, **suy ra** từ trạng thái dòng món (ACCEPTED, COOKING, READY, SERVED và CANCELLED sau COOKING) | nhận món, gửi món, trigger quota |
| `rc_check_balance(kind, id)` | So số dư lưu với tổng sổ kho / tổng reservation ACTIVE (RC500 nếu lệch) | 5 constraint trigger hoãn tới COMMIT |
| `rc_item_transition(item, from[], to, actor, reason)` | Khóa dòng, kiểm trạng thái nguồn, đổi trạng thái, ghi sự kiện | mọi bước của món |
| `rc_allocate_item(item)` | Cấp FEFO phần nguyên liệu còn thiếu cho một món; thiếu → RC001 | `rc_accept_item`, `rc_reallocate_item` |
| `rc_release_item_reservations(item, reason)` | ACTIVE → RELEASED, giảm reserved, xóa cờ ngoại lệ; không tạo biến động vật lý | `rc_cancel_item`, `rc_abandon_session` |
| `rc_lock_invoice(invoice)` | Khóa orders → invoices → payment_requests đang mở theo thứ tự chung | thanh toán, QR, mở lại, hủy request, webhook |
| `rc_invoice_total_checked(invoice)` | Đối chiếu dòng hóa đơn với đúng tập dòng SERVED (tên, số lượng, đơn giá); trả tổng tính lại | `rc_checkout_invoice`, `rc_abandon_session` |
| `rc_settle_invoice(invoice, actor)` | PAID → đóng đơn, phiên, trả bàn | `rc_pay_cash`, `rc_process_verified_qr` |

**22 hàm trigger** (47 trigger) giữ bất biến cho mọi đường ghi, kể cả DML trực tiếp:

| Nhóm | Hàm trigger | Bảo vệ |
| --- | --- | --- |
| Chỉ-thêm | `rc_forbid_change` | `stock_movements`, `payments`, `order_item_events`, `attendance_adjustments` không UPDATE/DELETE |
| Không xóa | `rc_forbid_delete` | 9 bảng giao dịch (đơn, đợt, dòng món, phiên, request, reservation, lô RAW/bếp, khoản chi) |
| Thực đơn | `rc_guard_used_recipe`, `rc_guard_daily_menu_item`, `rc_guard_daily_menu` | công thức đã dùng bất biến; không hạ quota dưới số đã chiếm; đồ thị trạng thái thực đơn |
| Bán hàng | `rc_guard_dining_table`, `rc_guard_table_session`, `rc_guard_order`, `rc_guard_order_item`, `rc_guard_order_item_event`, `rc_check_item_event_sync` | bàn OCCUPIED ⇔ phiên OPEN; đơn chỉ CLOSED sau PAID/UNCOLLECTED; snapshot dòng món; đồ thị trạng thái; chuỗi sự kiện nối đúng; đổi trạng thái không có sự kiện bị bắt lúc COMMIT |
| Hóa đơn, tiền | `rc_guard_invoice`, `rc_guard_invoice_items`, `rc_guard_payment_request`, `rc_guard_payment`, `rc_guard_webhook_event` | DRAFT → PAYMENT_PENDING → PAID/UNCOLLECTED; dòng hóa đơn là bản chụp đúng; payments chỉ khi PAYMENT_PENDING và đúng số phải thu; chứng cứ webhook bất biến |
| Kho | `rc_trg_balance`, `rc_guard_reservation`, `rc_guard_doc_header`, `rc_guard_doc_lines` | số dư = sổ kho tại COMMIT (5 constraint trigger); reservation một chiều; chứng từ CONFIRMED bất biến và dòng bị khóa |
| Tài chính | `rc_guard_expense`, `rc_guard_cash_recon` | khoản chi chỉ PAID → VOIDED; đối soát chỉ CURRENT → SUPERSEDED |

## 6. Đối chiếu hợp đồng Master Spec v4 ↔ v4.1

Đây là đối chiếu **theo hợp đồng**, không phải theo mã (mục 1).

| Spec | Yêu cầu | v4.1 thực hiện | Bằng chứng |
| --- | --- | --- | --- |
| I3.1 | Lock `stock_receipts`; sự kiện một lần; UNIQUE dòng nguồn → lô | `rc_confirm_receipt` khóa header; `event_key` và `inventory_batches.stock_receipt_item_id` UNIQUE | RAW-01, C9 |
| I3.2 | Lock chứng từ và lô theo thứ tự xác định; đủ + FEFO; trừ và ghi sổ nguyên tử | `rc_issue_stock` khóa phiếu rồi mọi lô liên quan theo `id` | RAW-02, RAW-03, FNC-EXP, FNC-FEFO, C9 |
| I3.3 | Nhận bếp/sơ chế/hoàn trả khóa chứng từ + lô; không xử lý lại WIP/DIRECT | `rc_receive_issue`, `rc_confirm_preparation`, `rc_confirm_return` | ISSUE-03, PREP-03, RETURN-02, C10 |
| I3.4 | Khóa `order_items` và dòng menu/quota rồi `kitchen_batches` có thứ tự; thiếu → rollback reservation | `rc_accept_item` (5 → 6 → 9) + savepoint | MENU-02, C1, C2, C3 |
| I3.5 | Chốt invoice khóa `orders`, chặn batch mới, so số dòng với toàn bộ dòng SERVED | `rc_checkout_invoice` + `rc_submit_batch` + `rc_invoice_total_checked` | INV-01, QĐ-4, C5 |
| I3.6 | Thanh toán khóa invoice/request; `payments.invoice_id` UNIQUE; đồng bộ invoice/đơn/phiên/bàn | `rc_pay_cash` + `rc_settle_invoice` | CASH-01, C6, C8 |
| I3.7 | Webhook chống replay; QR muộn tách khỏi trạng thái hóa đơn | `rc_process_verified_qr` idempotent, kết quả AFTER_CANCEL + sự cố mở | QR-01, QR-02, C6, C7 |
| I3.8 | Đối soát snapshot có phiên bản; `business_date` theo lịch vận hành | `rc_reconcile_cash` + `rc_business_date` | FIN-02 |
| E2 | Các đồ thị trạng thái (phiên, đơn, dòng món, chứng từ, reservation, hóa đơn, request, đối soát) | Trigger `rc_guard_*` đóng đồ thị ở mọi đường ghi | GRD, CON |
| G3.6 / M6 | Không tự trả bàn khi hóa đơn chưa PAID; khách bỏ đi cần nghiệp vụ riêng được duyệt | **Ghi đè có chủ đích bởi QĐ-3** chỉ cho `rc_abandon_session` (Manager, lý do, hóa đơn UNCOLLECTED) | QĐ-3 |
| E2 / M5 | Không hủy sau COOKING; hóa đơn miễn phí chưa hỗ trợ | **Bổ sung có chủ đích bởi QĐ-2**: hủy ngoại lệ của Manager, không hoàn kho, không tính tiền | QĐ-2 |
| M9 | Test nhiều dòng, lô hết hạn, lô không dùng được, NULL expiry | Đã có ca riêng cho từng tình huống | FNC-EXP, FNC-FEFO, RAW-02 |
| M7 | Áp ACL và test không cho DML trực tiếp | Khối ACL + GRD/CON | ACL, GRD |

## 7. Phát hiện kiểm toán

Mã DB-36…DB-47 trùng với [02 §10](02-danh-gia-database.md). Mức: 🔴 sai dữ liệu hoặc chặn nghiệp vụ khi chạy thật · 🟠 thiếu hàng rào hoặc kiểm tra · 🟢 môi trường hoặc kiểm thử.

| Mã | Mức | Phát hiện | Bằng chứng chạy thật | Xử lý |
| --- | :-: | --- | --- | --- |
| DB-36 | 🔴 | **Deadlock do đảo thứ tự khóa**: thanh toán, QR, hủy request khóa invoices rồi (qua `rc_settle_invoice`) mới khóa orders; chốt hóa đơn và gửi món khóa orders rồi invoices | Tái hiện trên baseline: `40P01` giữa luồng thanh toán và `rc_checkout_invoice` | Thứ tự chung 1 → 9, `rc_lock_invoice`; ca khóa xác định cho 7 hàm và đua tự do (C5, C6, C8); mutation "invoices trước orders" bị phát hiện |
| DB-37 | 🔴 | **Webhook trùng đến cùng lúc**: bên sau vượt kiểm tra trước khóa rồi nhận `23505` thay vì kết quả đã lưu (SePay sẽ gửi lại mãi) | Tái hiện trên baseline: lỗi `23505` "duplicate key value violates unique constraint payment_webhook_events_provider_transaction_code_key" | Kiểm lại sau khóa + `ON CONFLICT DO NOTHING` (C7); mutation bị phát hiện |
| DB-38 | 🔴 | Baseline chỉ có 9 trigger: DML trực tiếp đổi được trạng thái món, bàn, đơn, request, `payments` và chứng từ kho đã chốt mà không qua hàm | Đối chiếu danh mục: 9 → 47 trigger | 38 trigger mới (đồ thị trạng thái E2, cấm xóa, khóa dòng chi tiết); ca GRD; mutation bỏ trigger bị phát hiện |
| DB-39 | 🔴 | Số dư kho và `reserved_quantity` chỉ có CHECK khoảng: sửa tay `quantity_on_hand` không bị phát hiện, đổi trạng thái món không ghi sự kiện cũng không | Ca GRD "sửa tay số dư" chỉ đạt khi có trigger | 5 constraint trigger hoãn tới COMMIT (`rc_check_balance`) + `trg_order_items_event_sync`; mutation bỏ trigger bị phát hiện |
| DB-40 | 🟠 | Họ deadlock ở chứng từ kho: hoàn trả, hủy hàng, kiểm kê khóa nguồn theo thứ tự dòng phiếu; hai phiếu liệt kê ngược thứ tự thì chờ vòng tròn | Gỡ khóa trước khỏi mã final (tương đương baseline) → 8/8 vòng deadlock | Khóa trước toàn bộ nguồn theo thứ tự chung (C10); 2 mutant bị phát hiện |
| DB-41 | 🟠 | Chốt hóa đơn chỉ cộng tổng, không đối chiếu từng dòng hóa đơn với dòng SERVED (QĐ-4) | Đọc mã baseline | `rc_invoice_total_checked` + `trg_invoice_items_guard`; bất biến I-4a, I-4b, I-4c |
| DB-42 | 🟠 | Quota `NOT NULL > 0` không biểu diễn "không giới hạn" hay "hết suất"; hủy sau COOKING sẽ hoàn suất nếu chỉ đếm theo trạng thái hiện tại (QĐ-1, QĐ-2, QĐ-8) | Đọc mã + ca QĐ-1, QĐ-2, QĐ-8; mutation "hoàn suất sau COOKING" bị phát hiện | `quota_total` NULL được, CHECK ≥ 0; `rc_quota_used` suy ra, tính cả CANCELLED sau COOKING |
| DB-43 | 🟠 | Khách bỏ về chưa có biểu diễn: bàn kẹt OCCUPIED, hóa đơn treo (QĐ-3) | Ca QĐ-3, C8 | Trạng thái UNCOLLECTED + 3 cột + `rc_abandon_session` |
| DB-44 | 🟠 | Sự kiện đầu của dòng món không bị ép là `NULL → PENDING`; lý do toàn khoảng trắng lọt qua CHECK `IS NOT NULL` (QĐ-2) | Đọc mã | `ck_order_item_event_first`, `ck_order_item_event_reason` (`btrim`) |
| DB-45 | 🟠 | Kiểm đầu vào thiếu: lý do trắng (từ chối món, mở lại hóa đơn, hủy request/phiếu); xuất DIRECT cho nguyên liệu chưa có loại bếp DIRECT chỉ lỗi ở bước bếp nhận, khi RAW đã bị trừ và phiếu kẹt ISSUED; nhận vào kho lô đã quá hạn hoặc nguyên liệu ngừng dùng; đối soát ngày tương lai hoặc thiếu số liệu | Đọc mã baseline; ca FNC | RC422 ngay đầu hàm |
| DB-46 | 🟠 | "Chỉ Manager" cần dữ liệu vai trò nhưng baseline không seed `roles` | Đọc mã | Seed 4 vai trò + `rc_require_role` (RC403); ca RBAC (đúng vai trò, sai vai trò, nhân viên nghỉ việc, tài khoản khóa) |
| DB-47 | 🟢 | Database locale `C`: `lower()` không hạ chữ hoa có dấu nên `UNIQUE (lower(name))` (DB-08) hở; PGlite không lộ ra. `attendances.worked_minutes` có thể vượt khoảng có mặt | Phát hiện khi chuyển sang server thật | File 04 từ chối nạp khi `lower('ẮÒÚĐ') <> 'ắòúđ'`; `ck_attendance_worked_le_span`; README nêu cách tạo database |
| T-01 | 🟢 | Khoảng trống kiểm thử do mutation lộ ra: xuất từ lô hết hạn hoặc không dùng được, FEFO với NULL expiry, lô bếp hết hạn hoặc không dùng được, RBAC nhân viên nghỉ việc/tài khoản khóa, hủy hàng và kiểm kê trên đủ ba nguồn, ACL | Mutant sống sót ở vòng chạy đầu | Thêm ca FNC, FNC-EXP, FNC-FEFO, ACL; chạy lại, 40/40 mutant bị phát hiện |
| T-02 | 🟢 | Quan sát: sau khi Manager miễn phí toàn bộ món trên hóa đơn DRAFT, phiên vẫn có hóa đơn (0 đồng) nên `rc_close_empty_session` từ chối | Ca QĐ-2/3 | Đóng bằng `rc_abandon_session` (trả NULL, đơn CANCELLED, hóa đơn DRAFT giữ nguyên); có test, ghi ở mục 4 |

## 8. Giới hạn chưa kiểm chứng và điểm cần nhóm xác nhận

**Giới hạn chưa kiểm chứng**

1. Đã chạy trên PostgreSQL 15.18, 16.14, 17.5 và 18.4 (bản nhúng cho Windows, `fsync = off`); chưa chạy trên Linux hoặc bản đóng gói khác, chưa chạy các bản vá cũ hơn. Schema cần 15+ (`UNIQUE NULLS NOT DISTINCT`). Mutation chỉ chạy trên 17.5.
2. Không đo hiệu năng, không `EXPLAIN ANALYZE`, không tải lớn. Ca đồng thời dùng 2 đến 4 kết nối đua cùng lúc (cộng một kết nối quan sát), 15 vòng cho ca đua tự do; không phủ mọi cặp hàm.
3. Chỉ kiểm mức cô lập mặc định READ COMMITTED.
4. Tầng Spring Boot chưa có trong repository: ánh xạ JWT → `p_actor`, `@PreAuthorize`, thử lại `40001/40P01`, ánh xạ mã lỗi → HTTP chưa được kiểm. DB tin `p_actor`.
5. Mô hình GRANT/REVOKE mới được kiểm bằng hai role thử; chưa có script cấp quyền chính thức cho môi trường thật (đoạn gợi ý ở cuối file 04).
6. Thời gian trôi qua (hết hạn lô, chuyển ngày 04:00) mô phỏng bằng sửa dữ liệu, chưa kiểm tại thời điểm chuyển ngày thật.
7. Truy vấn báo cáo (5.2, 5.4, 5.5, danh sách UNCOLLECTED) chỉ được mô tả, chưa có ca kiểm số liệu.
8. Chưa kiểm di chuyển dữ liệu v3 → v4/v4.1 (spec M10).
9. Không so sánh được mã v4 (mục 1).

**Quyết định còn cần nhóm xác nhận**

| # | Vấn đề | Hiện trạng trong v4.1 | Khuyến nghị |
| --- | --- | --- | --- |
| 1 | **QĐ-5** hủy một phần số lượng của một dòng | Chưa hỗ trợ; hủy cả dòng rồi gửi lại số đúng | Giữ hiện trạng: không đổi snapshot, chuỗi sự kiện và quy tắc suất đơn giản; chỉ làm tách dòng nếu nghiệp vụ thật sự cần |
| 2 | Cưỡng chế vai trò ở DB cho các hàm nhạy cảm còn lại (duyệt kho, hoàn trả, kiểm kê, đánh dấu lô hỏng — spec G7.8 gắn với Manager; chốt hóa đơn/thu tiền — Cashier) | Chỉ hai hàm Manager-only được ép ở DB | Nếu muốn phòng thủ chiều sâu, thêm `rc_require_role` cho từng hàm; chi phí nhỏ, cần chốt ma trận vai trò |
| 3 | Vòng đời hóa đơn UNCOLLECTED: thu bù, xóa nợ, báo cáo công nợ | Trạng thái cuối, chỉ ghi nhận; không có luồng thu bù (ngoài Core, spec M6) | Giữ ngoài Core; nếu cần, thiết kế module riêng có phê duyệt |
| 4 | Kiểm suất khi **gửi món** | `rc_submit_batch` chặn khi suất đã chiếm không còn đủ (đề xuất ban đầu chỉ cảnh báo); kiểm cứng ở lúc nhận | Giữ chặn mềm (tránh tạo dòng chắc chắn bị từ chối); có thể chuyển thành cảnh báo ở UI |
| 5 | `request_key` cho phiếu nhập/xuất/kiểm kê | Chỉ đợt gọi món, sơ chế, hoàn trả, hủy hàng, khoản chi có; hai lần bấm "Tạo" ra hai phiếu nháp, nhưng xác nhận mỗi phiếu một lần và không ghi sổ thừa | Thêm cột nếu giao diện tạo phiếu bằng một request |
| 6 | Mốc cắt ngày kinh doanh 04:00 giờ Việt Nam (DB-33) | Cố định trong `rc_business_date` | Nhóm xác nhận giờ cắt thật của nhà hàng |
| 7 | Hóa đơn DRAFT 0 đồng còn lại sau khi miễn phí toàn bộ | Giữ nguyên, đơn CANCELLED | Chấp nhận, hoặc cho phép hủy hóa đơn DRAFT rỗng |

## 9. Kết quả kiểm thử

Chạy lại bằng các lệnh ở [README](README.md). Số liệu của lần chạy chốt (25/10/2026). Bộ 05, 06 và 08 được chạy nguyên vẹn, trên database mới, ở **15.18, 16.14, 17.5 và 18.4** với cùng kết quả (mỗi phiên bản: schema nạp được với 48 bảng, 101 FK, 105 CHECK, 47 trigger, 40 hàm `rc_*` không phải trigger). Mutation chạy trên 17.5.

| Bộ kiểm thử | Kết quả (mỗi phiên bản PostgreSQL) |
| --- | --- |
| `05-test-v4.1.sql` (một kết nối) | **198 kiểm tra dương + 246 kiểm tra âm — đạt hết**, gồm 19 truy vấn bất biến (nhãn I-1…I-14) cuối file và khối ACL |
| `06-test-concurrency-v4.1.mjs` (nhiều kết nối, ITER = 15) | **67/67 đạt**, không có deadlock `40P01`, không có lỗi ngoài dự kiến |
| `08-vi-du-xuyen-suot.sql` (ví dụ xuyên suốt của [01 §11](01-luong-chinh.md), database trống riêng) | **32 phép so sánh đạt** |
| Mutation trên SQL (40 lỗi cài cố ý vào schema) | **40/40** bị bộ 05 phát hiện |
| Mutation trên khóa (15 lỗi cài cố ý: gỡ/đảo khóa, gỡ ON CONFLICT, ...) | **12/15** bị bộ 06 phát hiện |

Ba mutant khóa còn sống đều là **mutant tương đương** (hành vi quan sát được không đổi vì có hàng rào thứ hai), không phải lỗ hổng kiểm thử:

| Mutant | Vì sao không đổi hành vi |
| --- | --- |
| `rc_start_cooking` bỏ kiểm ACCEPTED sau khi khóa dòng | `rc_item_transition` kiểm lại trạng thái nguồn `ACCEPTED` dưới khóa dòng; lần gọi thứ hai không còn reservation ACTIVE nên không trừ kho, rồi bị RC409 |
| `rc_lock_invoice` bỏ `FOR UPDATE` trên `invoices` | Mọi đường qua `rc_lock_invoice` đã giữ khóa `orders` (mức 1) nên đã tuần tự; khóa invoices chỉ còn là lớp thứ hai cho ghi trực tiếp của service |
| `rc_confirm_receipt` bỏ khóa header phiếu nhập | UPDATE thứ hai chờ khóa dòng rồi bị `rc_guard_doc_header` từ chối (CONFIRMED bất biến); `inventory_batches.stock_receipt_item_id` và `event_key` UNIQUE chặn lô và sổ trùng |
