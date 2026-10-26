# 07 · Kiểm toán hàm `rc_*` — schema v4.1 bản chốt nghiệp vụ

> **Phạm vi:** 32 hàm nghiệp vụ `rc_*` trong [04-schema-v4.1.sql](04-schema-v4.1.sql) (20 tên của v4 + 6 hàm DB-34 + 2 hàm của QĐ-2/QĐ-3 + 4 hàm của vòng đóng băng: `rc_waive_invoice`, `rc_create_stock_receipt`, `rc_create_stock_issue`, `rc_create_stock_count`), 16 hàm hỗ trợ, 22 hàm trigger (47 trigger) và phân quyền role ở [09-acl-v4.1.sql](09-acl-v4.1.sql).
> **Cách kiểm:** mỗi hàm được đọc theo 12 tiêu chí (vào/ra, điều kiện, actor, bảng đọc-ghi, trạng thái, khóa, idempotency, ràng buộc, rollback, tương thích v4, ngoại lệ, kiểm thử), rồi đối chiếu với kết quả chạy thật.
> **Mức xác minh (vòng đóng băng, 26/10/2026):** PostgreSQL **15.19, 16.15, 17.11, 18.6** chạy trong container Docker trên Linux (server thật, không phải PGlite, `fsync` mặc định): schema, 05, 06, 08 và báo cáo 10/11 đạt trên cả bốn phiên bản; mutation chạy trên 17.11. Số liệu ở mục 9; giới hạn ở mục 8.
> **Không tuyên bố tương đương với mã v4:** repository không chứa SQL v4 gốc — xem mục 1.

## 0. Kết luận

| Mục | Kết quả |
| --- | --- |
| Hàm nghiệp vụ | **32** = 20 tên của v4 + 6 hàm DB-34 + 2 hàm QĐ-2/QĐ-3 + 4 hàm vòng đóng băng. Cộng 16 hàm hỗ trợ + 22 hàm trigger = 70 hàm `rc_*` |
| Vòng đóng băng (so với bản 25/10) | 4 hàm mới · 31 hàm có `p_actor` thêm kiểm vai trò ở dòng đầu · `rc_submit_batch` đổi hợp đồng (JSONB + cảnh báo, không chặn quota) · `rc_abandon_session`, `rc_checkout_invoice`, `rc_close_empty_session`, `rc_confirm_receipt` đổi hành vi · `rc_guard_invoice`, `rc_guard_order`, `rc_guard_doc_header` siết |
| Lỗ hổng tìm và vá trong vòng này | Đặt trạng thái chứng từ kho trực tiếp tạo/xóa tồn mà không qua sổ (DB-48) · `p_actor` không được kiểm vai trò ở 29/31 hàm (DB-49) · hóa đơn DRAFT treo sau khi đơn kết thúc (DB-50) · không có cách đóng hóa đơn 0 đồng / miễn thu (DB-51) · phiếu nháp nhập/xuất/kiểm kê nhân đôi khi bấm lặp (DB-52) · khách bỏ về với hóa đơn 0 đồng vi phạm CHECK (DB-53). Mục 7 |
| Kiểm thử | Mỗi phiên bản 15/16/17/18: 05 **308 ca dương + 342 ca âm** · 06 **78/78** · 08 **34/34** · báo cáo 11 **43/43** · mutation **27/27** (24 mutant SQL/ACL/báo cáo + 3 mutant khóa) |
| Quyền | Mọi hàm có `p_actor` cưỡng chế vai trò bằng `rc_authorize` (ma trận mục 10); role PostgreSQL theo [09-acl](09-acl-v4.1.sql): `rc_app` chỉ ghi nghiệp vụ qua hàm, `rc_webhook` một hàm, `rc_readonly` đọc (trừ mật khẩu băm); 25 đường ghi tắt/leo thang được thử và đều bị `42501` |
| Đủ điều kiện đóng băng | **Có, cho tầng database** — kèm các giới hạn ở mục 8 (hiệu năng, tầng Spring Boot chưa có, chưa migration v3). Lý do ở mục 12 |

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
| `RC403` | `p_actor` không phải nhân viên ACTIVE có tài khoản ACTIVE mang vai trò được phép của hàm (mục 10) | HTTP 403; ghi log bảo mật (đây là lỗi ánh xạ quyền ở backend hoặc tài khoản vừa bị khóa) |
| `RC409` | Xung đột trạng thái: gọi lặp, sai bước, đối tượng đã đổi | HTTP 409; với gọi lặp coi như đã xử lý |
| `RC422` | Dữ liệu vào không hợp lệ (thiếu lý do, phiếu rỗng, không tồn tại) | HTTP 422 |
| `RC500` | Bất biến bị phá (số dư lệch sổ, trạng thái không có sự kiện) — chỉ phát ra từ constraint trigger lúc COMMIT | HTTP 500 + cảnh báo vận hành |
| `23505` `23503` `23514` | UNIQUE / FK / CHECK | Lỗi lập trình hoặc trùng yêu cầu |
| `40001` `40P01` | Serialization / deadlock | Thử lại giao dịch (không còn xảy ra trong các ca đã kiểm) |

**Thứ tự khóa dùng chung** (header file 04) — mọi hàm khóa theo đúng thứ tự này, trong cùng một mức thì theo `id` tăng dần (lô bếp theo loại rồi `id`), chứng từ kho (header) khóa trước tài nguyên nó chạm:

| Mức | Đối tượng | Hàm giữ khóa này |
| :-: | --- | --- |
| 0 | khóa tư vấn `(bảng, request_key)` | `rc_create_stock_receipt/_issue/_count` (trước mọi khóa hàng) |
| 1 | `orders` | gửi món, chốt hóa đơn, thanh toán, hủy ngoại lệ, bỏ về, miễn thu, đóng phiên trống |
| 2 | `invoices` | như trên (qua `rc_lock_invoice` hoặc trực tiếp) |
| 3 | `payment_requests` đang mở | thanh toán, QR, mở lại, bỏ về, miễn thu |
| 4 | `table_sessions`, `dining_tables` | mở bàn, đóng phiên, thanh toán, bỏ về |
| 5 | `order_items` | mọi bước của món |
| 6 | `daily_menu_items` | `rc_accept_item` (tuần tự hóa quota) |
| 7 | `inventory_batches` | nhập/xuất/hủy phiếu xuất, hoàn trả, hủy hàng, kiểm kê |
| 8 | `stock_issue_items` (WIP) | sơ chế, hoàn trả, hủy hàng, kiểm kê |
| 9 | `kitchen_batches` theo (loại, id) | cấp phát, nấu, hủy, cấp lại, hoàn trả, hủy hàng, kiểm kê |

**Idempotency** có ba lớp: (A) khóa theo mã yêu cầu — `rc_submit_batch` (`request_key`), `rc_create_stock_receipt/_issue/_count` (`request_key` + `request_hash`: cùng payload trả chứng từ cũ, khác payload RC409), `rc_process_verified_qr` (`provider_transaction_code`) trả lại kết quả cũ; (B) một lần theo trạng thái — mọi hàm chuyển trạng thái/xác nhận chứng từ: lần hai gặp RC409, sổ kho có `event_key` UNIQUE làm hàng rào thứ hai; (C) không idempotent theo thiết kế — `rc_open_session` (RC409 khi bàn có khách), `rc_reconcile_cash` (mỗi lần một phiên bản, bấm lặp không lý do bị CHECK chặn).

**Quyền (QĐ-BM).** `p_actor` là `employees.id` của principal mà Spring Security đã xác thực (JWT); backend **không bao giờ** lấy `p_actor` từ body/query. DB không tự định danh người dùng cuối, nhưng: (1) mọi hàm có `p_actor` gọi `rc_authorize(p_actor, vai trò…)` ở dòng đầu — nhân viên ACTIVE, tài khoản ACTIVE, có ít nhất một vai trò của ma trận mục 10, nếu không RC403 trước khi đọc dữ liệu nghiệp vụ; (2) role PostgreSQL của backend (`rc_app`) chỉ ghi dữ liệu nhạy cảm qua hàm `SECURITY DEFINER` (chủ `rc_owner`, `search_path` cố định), không gọi được hàm nội bộ, không có cột trạng thái nào để `UPDATE`; (3) webhook có role riêng chỉ chạy `rc_process_verified_qr`. Không dùng biến phiên (`set_config`) để định danh vì chính user DB tự đặt được.

**Rollback.** Mỗi lời gọi hàm là một đơn vị: lỗi ở bất kỳ bước nào hoàn tác toàn bộ lời gọi. Ngoại lệ có chủ đích duy nhất là savepoint trong `rc_accept_item`: thiếu suất hoặc nguyên liệu → hoàn tác phần đã giữ rồi *ghi* REJECTED kèm lý do (kết quả nghiệp vụ hợp lệ).

## 3. Quyết định nghiệp vụ → hàm

Vòng đóng băng dùng nhãn yêu cầu riêng; hai nhãn trùng số với quyết định cũ nên được đặt mã mới để không lẫn: "QĐ-7 miễn thu" → **QĐ-MT** (QĐ-7 vẫn là "giữ bảng `orders`"); "QĐ-4 gửi món khi hết quota" → bổ sung vào **QĐ-4** (mục quota) cùng QĐ-8; QĐ-BẢO MẬT → **QĐ-BM**; QĐ-IDEMPOTENCY → **QĐ-ID**; QĐ-NGÀY KINH DOANH → **QĐ-NG**.

| Quyết định | Hàm / đối tượng DB | Ca kiểm thử |
| --- | --- | --- |
| **QĐ-1** quota NULL = không giới hạn, 0 = hết suất; vẫn kiểm nguyên liệu | `rc_accept_item`, `rc_submit_batch`, `rc_quota_used`, `ck_daily_menu_quota_nonneg`, `rc_guard_daily_menu_item` | QĐ-1, C1, C2 |
| **QĐ-2** hủy trước COOKING theo quyền; từ COOKING chỉ Manager, có lý do, không hoàn nguyên liệu/quota, không tính tiền | `rc_cancel_item`, `rc_cancel_item_exception`, `rc_authorize`, `rc_quota_used`, `rc_guard_order_item`, `rc_guard_invoice_items` | QĐ-2, QĐ-5, I-14, C4, C8b |
| **QĐ-3** khách bỏ về: Manager, lý do; UNCOLLECTED là **trạng thái cuối** của Core (không công nợ/thu bù/xóa nợ); không payments/PAID/doanh thu; trả bàn; webhook QR đến sau chỉ lưu AFTER_CANCEL (sự cố OPEN) | `rc_abandon_session`, `invoices.uncollected_*`, `rc_guard_invoice`, `rc_guard_order`, `rc_process_verified_qr` | QĐ-3 (2 khối), I-11, I-12, C8, C8b, C11 |
| **QĐ-4** Cashier chốt hóa đơn (đủ dòng, tự tính tổng, đối chiếu, PAYMENT_PENDING, khóa gọi món). **Gửi món khi hết quota: không chặn, trả cảnh báo `QUOTA_EXCEEDED`**; Kitchen chỉ ACCEPTED khi đủ suất **và** nguyên liệu | `rc_checkout_invoice`, `rc_invoice_total_checked`, `rc_reopen_invoice`, `rc_submit_batch` + `rc_batch_result`, `rc_accept_item` | QĐ-4 (2 khối), MENU-02, QĐ-1, C1, C5 |
| **QĐ-5** không hủy một phần: hủy cả dòng còn hủy được, gửi **dòng mới** với số lượng thay thế; dòng CANCELLED là cuối, không dùng lại; không hoàn/trừ hai lần; quota theo QĐ-8 | `rc_cancel_item`, `rc_cancel_item_exception`, `rc_guard_order_item` (snapshot `quantity` bất biến, CANCELLED cuối), `rc_release_item_reservations` | QĐ-5, M15, M16 |
| **QĐ-6** đầu ra sơ chế cùng đơn vị | Quy ước `base_unit` của nguyên liệu gốc; `rc_confirm_preparation` kiểm Σ đầu ra ≤ đầu vào | PREP-02, PREP-03 |
| **QĐ-7** giữ bảng `orders` | Không đổi số bảng (48) | — |
| **QĐ-8** suất chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING trả đúng một lần; từ COOKING không hoàn; nguyên tử; đồng thời không vượt | `rc_accept_item`, `rc_quota_used`, `rc_cancel_item`, `rc_allocate_item`, `rc_guard_daily_menu_item` | QĐ-8, QĐ-4, C1, C3, C4 |
| **QĐ-MT** miễn thu: chỉ Manager, lý do + người + thời điểm; WAIVED ≠ PAID ≠ UNCOLLECTED; không payments giả, không doanh thu; giữ `invoice_items`/giá; không áp cho PAID; hóa đơn 0 đồng đóng dứt điểm bằng WAIVED | **`rc_waive_invoice`**, `invoices.waived_*`, `ck_invoice_waived_fields`, `rc_guard_invoice`, `rc_guard_order`, `rc_abandon_session` (0 đồng), `rc_checkout_invoice` (từ chối 0 đồng) | QĐ-MT, QĐ-2/3/MT, I-15, I-16, C11, M03–M08 |
| **QĐ-ID** chứng từ nhập/xuất/kiểm kê idempotent theo `request_key` | **`rc_create_stock_receipt/_issue/_count`**, `rc_request_lookup`, `rc_request_hash`, `request_key` UNIQUE + `request_hash` | QĐ-ID, C11, I-18, M11, L01 |
| **QĐ-NG** ngày kinh doanh 04:00 Asia/Ho_Chi_Minh, `TIMESTAMPTZ`, không phụ thuộc TimeZone phiên | `rc_business_date`, `rc_usable_until`, `rc_guard_payment`, `rc_guard_expense` | QĐ-NG (10 mốc × 5 TimeZone), M13, M14 |
| **QĐ-BM** phân quyền hàm + ACL | `rc_authorize` (31 hàm), 09-acl (`rc_owner`, `rc_app`, `rc_webhook`, `rc_readonly`), `rc_doc_effect_missing` | QĐ-BM, ACL, M01, M02, M18–M20 |

## 4. Kiểm toán từng hàm nghiệp vụ

Bảng tổng hợp. Cột "ca đúng / ca bị chặn" đếm tự động số lời gọi trong mã file 05 (không tính lời gọi gián tiếp qua hàm phụ `rc_test.*`); "bị chặn" gồm cả 31 ca của ma trận quyền QĐ-BM.

| Hàm | Nhóm | Nguồn | Vòng đóng băng | Ca đúng / ca bị chặn (05) | Ca đồng thời (06) |
| --- | :-: | --- | --- | :-: | --- |
| `rc_open_session` | 3.1 | v4 | + vai trò | 7 / 6 | C9 |
| `rc_close_empty_session` | 3.1 | v4 | + vai trò; thông báo chỉ tới miễn thu | 19 / 4 | — |
| `rc_submit_batch` | 3.2 | DB-34 | **đổi hợp đồng**: JSONB + cảnh báo quota, không chặn | 11 / 8 | C5, C8b |
| `rc_accept_item` | 3.3 · 3.4 | v4 | + vai trò | 37 / 4 | C1, C2, C3, C4, C5 |
| `rc_reject_item` | 3.3 | DB-34 | + vai trò | 4 / 5 | — |
| `rc_start_cooking` | 3.3 · 3.4 | v4 | + vai trò | 12 / 6 | C3, C4, C5 |
| `rc_finish_dish` | 3.3 | v4 | + vai trò | 5 / 2 | C5 |
| `rc_serve_item` | 3.3 | DB-34 | + vai trò | 4 / 2 | C5 |
| `rc_cancel_item` | 3.3 · 3.4 | v4 | + vai trò (Waiter/Kitchen/Manager) | 15 / 8 | C4 |
| `rc_cancel_item_exception` | 3.3 | QĐ-2/3 | vai trò qua `rc_authorize` | 8 / 10 | C8, C8b |
| `rc_mark_batch_unusable` | 4.1 · 4.5 | DB-34 | + vai trò **Manager** (spec G7.8) | 4 / 5 | — |
| `rc_reallocate_item` | 3.4 | DB-34 | + vai trò | 1 / 3 | — |
| `rc_checkout_invoice` | 3.5 | v4 | + vai trò; 0 đồng → chỉ tới miễn thu | 14 / 7 | C5, C8, C8b |
| `rc_reopen_invoice` | 3.5 | v4 | + vai trò | 5 / 4 | C8 |
| `rc_pay_cash` | 3.6 | v4 | + vai trò | 6 / 7 | C6, C8, C11 |
| `rc_start_qr` | 3.7 | v4 | + vai trò | 7 / 3 | C6, C7, C8, C11 |
| `rc_process_verified_qr` | 3.7 | v4 | giữ nguyên (role `rc_webhook`) | 13 / 1 | C6, C7, C8, C11 |
| `rc_cancel_payment_request` | 3.6 · 3.7 | v4 | + vai trò | 1 / 4 | C6 |
| `rc_abandon_session` | 3.1 | QĐ-2/3 | 0 đồng → WAIVED; luôn trả id hóa đơn | 6 / 8 | C8, C8b, C11 |
| `rc_waive_invoice` | 3.5 | QĐ-MT | **mới** | 3 / 9 | C8 (thứ tự khóa), C11 |
| `rc_create_stock_receipt` | 4.2 | QĐ-ID | **mới** | 5 / 5 | C11 |
| `rc_create_stock_issue` | 4.3 | QĐ-ID | **mới** | 2 / 2 | C11 |
| `rc_create_stock_count` | 4.7 | QĐ-ID | **mới** | 3 / 2 | — |
| `rc_confirm_receipt` | 4.2 | v4 | + vai trò; ghi sổ trước rồi đổi trạng thái | 8 / 8 | C9, C10, C11 |
| `rc_issue_stock` | 4.3 | v4 | + vai trò | 10 / 10 | C9, C10, C11 |
| `rc_receive_issue` | 4.3 | v4 | + vai trò | 6 / 4 | C9, C10 |
| `rc_cancel_issue` | 4.3 | v4 | + vai trò | 3 / 5 | — |
| `rc_confirm_preparation` | 4.4 | v4 | + vai trò | 2 / 5 | C10 |
| `rc_confirm_return` | 4.3 | v4 | + vai trò (Manager) | 1 / 3 | C10 |
| `rc_confirm_adjustment` | 4.7 | v4 | + vai trò (Manager) | 3 / 6 | C10 |
| `rc_confirm_count` | 4.7 | v4 | + vai trò (Manager) | 4 / 6 | C10 |
| `rc_reconcile_cash` | 5.3 | DB-34 | + vai trò (Manager) | 2 / 4 | — |

Thẻ chi tiết bên dưới giữ nguyên 12 tiêu chí của vòng chốt nghiệp vụ; với các hàm chỉ "+ vai trò", tiêu chí **3 · Actor và quyền** được thay bằng ma trận ở mục 10 (RC403 trước mọi khóa). Thẻ đã đổi hành vi được viết lại; 4 hàm mới có thẻ ở mục 4.7.

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

#### `rc_abandon_session` — nhóm 3.1 · hàm bổ sung vòng chốt (QĐ-2/QĐ-3), sửa ở vòng đóng băng (QĐ-MT)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_abandon_session(p_session, p_actor, p_reason)` → BIGINT: id hóa đơn UNCOLLECTED, hoặc WAIVED khi tổng 0 đồng; NULL khi không có món phải thu và chưa có hóa đơn |
| **2 · Điều kiện trước** | Vai trò MANAGER đang hoạt động; lý do không rỗng; đơn và phiên OPEN. |
| **3 · Actor và quyền** | MANAGER (`rc_authorize`); người và lý do lưu ở `invoices.uncollected_*` (hoặc `waived_*`), `table_sessions.closed_by_id`, `order_item_events`. |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `payment_requests`, `order_items`, `table_sessions` · W U `order_items` → CANCELLED (+ `order_item_events`), U `stock_reservations`/`kitchen_batches` (món chưa nấu), C `invoices` + `invoice_items` (nếu cần), U `payment_requests` → CANCELLED, U `invoices` → UNCOLLECTED/WAIVED, U `orders`, `table_sessions`, `dining_tables`. |
| **5 · Trạng thái trước → sau** | Dòng PENDING/ACCEPTED → CANCELLED (giải phóng phần giữ); COOKING/READY → CANCELLED (không hoàn); SERVED giữ nguyên và vào hóa đơn. Tổng > 0 → UNCOLLECTED; tổng = 0 (đã có hóa đơn, hoặc món SERVED giá 0) → WAIVED với lý do "Khách bỏ về: … (hóa đơn 0 đồng)"; đơn CLOSED. Không món SERVED và chưa có hóa đơn → đơn CANCELLED. Phiên CLOSED; bàn AVAILABLE. |
| **6 · Khóa hàng và transaction** | `orders` (1) → `invoices` (2) → `payment_requests` (3) → `table_sessions` và `dining_tables` (4) → `order_items` (5). |
| **7 · Idempotency** | Lần hai → RC409 (đơn không còn OPEN). Song song với thanh toán / webhook: đúng một bên thắng (C8, C11). |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_invoice_uncollected_fields` (đủ ba trường, total > 0), `ck_invoice_waived_fields`, `trg_invoice_guard` (UNCOLLECTED/WAIVED cuối, không payments), `trg_orders_guard` (CLOSED chỉ khi hóa đơn ở trạng thái cuối; CANCELLED chỉ khi không có hóa đơn), bất biến I-11, I-12, I-15, I-16. |
| **9 · Lỗi và rollback** | RC403, RC422, RC409. Rollback toàn bộ — bàn không bao giờ được trả mà thiếu bản ghi nghĩa vụ. |
| **10 · Tương thích nghiệp vụ v4** | Không có trong v4: spec M6 để ngỏ và G3.6 cấm tự trả bàn khi chưa PAID. QĐ-3 cho phép đóng phiên sau khi ghi nhận nghĩa vụ — chỉ qua hàm này. |
| **11 · Ngoại lệ** | Không `payments`, không PAID, không doanh thu, không xóa dữ liệu, không công nợ/thu bù (ngoài Core). QR đến muộn → AFTER_CANCEL + sự cố OPEN, hóa đơn không đổi. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 6 / 8 (khối QĐ-3, QĐ-3 vòng đời, QĐ-2/3/MT, QĐ-BM). File 06: C8, C8b, C11 (bỏ về ↔ webhook). |
| **Vòng đóng băng** | Trước: hóa đơn DRAFT 0 đồng bị bỏ lại khi đơn CANCELLED (DB-50) và món SERVED giá 0 làm UNCOLLECTED vi phạm CHECK (DB-53). Nay đóng bằng WAIVED. |

### 4.2 Gửi món (3.2)

#### `rc_submit_batch` — nhóm 3.2 · hàm bổ sung DB-34, đổi hợp đồng ở vòng đóng băng (QĐ-4)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_submit_batch(p_order, p_actor, p_request_key, p_items JSONB)` → **JSONB** `{"batch_id", "replayed", "warnings": [{"code": "QUOTA_EXCEEDED", "order_item_id", "daily_menu_item_id", "item_name", "quantity", "quota_total", "quota_used", "remaining"}]}` |
| **2 · Điều kiện trước** | `request_key` không rỗng; `p_items` là mảng không rỗng; đơn OPEN; hóa đơn chưa PAYMENT_PENDING/PAID/UNCOLLECTED/WAIVED; mỗi món: số lượng > 0, có trong thực đơn PUBLISHED hôm nay, còn kinh doanh, không tạm dừng. **Không kiểm quota** (QĐ-4). |
| **3 · Actor và quyền** | WAITER hoặc MANAGER (`rc_authorize`); `waiter_id = p_actor`. |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `daily_menu_items`, `daily_menus`, `menu_items`, `order_items` (đếm suất cho cảnh báo) · W C `order_batches`, C `order_items` (PENDING, chụp tên/giá), C `order_item_events` (NULL → PENDING). |
| **5 · Trạng thái trước → sau** | Món mới ở PENDING (chưa chiếm suất). |
| **6 · Khóa hàng và transaction** | `orders` FOR UPDATE (mức 1) — tuần tự với chốt hóa đơn, hủy ngoại lệ, bỏ về, miễn thu; kiểm lại `request_key` sau khi giữ khóa. |
| **7 · Idempotency** | `order_batches.request_key` UNIQUE. Gửi lặp cùng key và cùng đơn → trả đợt cũ, `replayed = true`, cảnh báo tính lại theo số suất hiện tại. Cùng key khác đơn → RC409. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `order_batches_request_key_key`, `ck_order_items_quantity_positive`, `ck_order_items_unit_price_nonneg`, `trg_order_item_events_chain`, `trg_order_items_event_sync`. |
| **9 · Lỗi và rollback** | Một dòng sai (RC422/RC409) → rollback cả đợt. |
| **10 · Tương thích nghiệp vụ v4** | Khớp spec G3.2 và G9.3. Cảnh báo quota là quyết định của nhóm (đề xuất gốc), thay cho chặn mềm của bản 25/10. |
| **11 · Ngoại lệ** | Hết quota (kể cả quota 0) → vẫn ghi, kèm cảnh báo; Kitchen sẽ nhận REJECTED "Vượt hạn mức…" ở `rc_accept_item`. quota NULL → không cảnh báo. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 11 / 8 (MENU-02, ORDER-01, ORDER-04, QĐ-1, QĐ-3, QĐ-4, QĐ-BM). File 06: C5, C8b. Mutant M09 (sai biên cảnh báo) bị phát hiện. |
| **Vòng đóng băng** | Đổi kiểu trả về BIGINT → JSONB (backend đọc `batch_id`); bỏ chặn quota, thêm `rc_batch_result`. |

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

### 4.7 Hàm mới của vòng đóng băng

#### `rc_waive_invoice` — nhóm 3.5 · hàm mới (QĐ-MT)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `rc_waive_invoice(p_order, p_actor, p_reason)` → BIGINT (id hóa đơn WAIVED) |
| **2 · Điều kiện trước** | Vai trò MANAGER; lý do không rỗng; đơn OPEN; hóa đơn (nếu có) DRAFT hoặc PAYMENT_PENDING; mọi dòng món đã kết thúc (SERVED/CANCELLED/REJECTED); có ít nhất một dòng SERVED hoặc đã có hóa đơn. |
| **3 · Actor và quyền** | MANAGER (`rc_authorize`); người, lý do, thời điểm vào `invoices.waived_*`. |
| **4 · Bảng đọc / ghi** | R `orders`, `invoices`, `payment_requests`, `table_sessions`, `order_items` · W C `invoices` + `invoice_items` (nếu thiếu), U `payment_requests` → CANCELLED ("Miễn thu: …"), U `invoices` → WAIVED, U `orders` → CLOSED, `table_sessions` → CLOSED, `dining_tables` → AVAILABLE. |
| **5 · Trạng thái trước → sau** | Hóa đơn DRAFT/PAYMENT_PENDING → WAIVED (cuối); `total` = Σ `line_total` = giá trị được miễn (≥ 0); `invoice_items` giữ nguyên giá bán. |
| **6 · Khóa hàng và transaction** | `orders` (1) → `invoices` (2) → `payment_requests` (3) → `table_sessions`, `dining_tables` (4) — cùng thứ tự với thanh toán nên miễn thu và thu tiền không cùng thành công (C11) và không deadlock (probe C8). |
| **7 · Idempotency** | Lần hai → RC409 "đã WAIVED". |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `ck_invoice_waived_fields` (ba trường + lý do không trắng), `invoices_waived_by_id_fkey`, `trg_invoice_guard` (PAID không thành WAIVED; WAIVED bất biến; WAIVED không có payments), `trg_orders_guard`, bất biến I-15, I-16. |
| **9 · Lỗi và rollback** | RC403, RC422, RC409 (đã PAID/UNCOLLECTED/WAIVED, còn món chưa kết thúc, phiên trống chưa có hóa đơn), RC500 nếu tổng đã chốt lệch tổng dòng. Rollback toàn bộ. |
| **10 · Tương thích nghiệp vụ v4** | Spec M5 ghi "hóa đơn miễn phí chưa hỗ trợ"; QĐ-MT bổ sung có chủ đích, không thêm bảng, không khuyến mãi/giảm giá nhiều cấp. |
| **11 · Ngoại lệ** | Không tạo `payments` 0 đồng; không vào doanh thu (báo cáo `rpt_unpaid_closures` liệt kê riêng); QR đến sau → AFTER_CANCEL, sự cố OPEN. Món giá 0 (`rc_checkout_invoice` từ chối 0 đồng) đóng bằng hàm này. |
| **12 · Kiểm thử thành công / thất bại** | File 05: 3 / 9 (QĐ-MT, QĐ-2/3/MT, QĐ-3, QĐ-BM). File 06: C11 (miễn thu ↔ thu tiền 2×ITER vòng, ↔ webhook ITER vòng, giữ khóa xác định), probe thứ tự khóa C8. Mutant M03–M07, L02, L03 bị phát hiện. |

#### `rc_create_stock_receipt` · `rc_create_stock_issue` · `rc_create_stock_count` — nhóm 4.2 · 4.3 · 4.7 · hàm mới (QĐ-ID)

| Tiêu chí | Kết quả kiểm toán |
| --- | --- |
| **1 · Vào / ra** | `(p_actor, p_request_key, p_payload JSONB)` → BIGINT (id chứng từ). Payload: nhập `{"supplier_name", "note", "items": [{"ingredient_id", "input_quantity", "input_unit", "conversion_factor", "unit_price_per_input_unit", "lot_code", "expiry_date"}]}`; xuất `{"note", "items": [{"inventory_batch_id", "quantity", "fulfillment_mode"}]}`; kiểm kê `{"note", "items": [{"raw_batch_id" \| "wip_issue_item_id" \| "kitchen_batch_id", "actual_quantity", "reason"}]}` |
| **2 · Điều kiện trước** | Vai trò MANAGER; payload là object có `items` là mảng không rỗng; `request_key` 1–64 ký tự. |
| **3 · Actor và quyền** | MANAGER (`rc_authorize`); `created_by_id`/`issued_by_id` = `p_actor`. |
| **4 · Bảng đọc / ghi** | C header (DRAFT, `request_key`, `request_hash` = sha256 payload) + C dòng. Kiểm kê chụp `book_quantity` từ số sổ hiện tại. |
| **5 · Trạng thái trước → sau** | Không có → DRAFT. Xác nhận vẫn bằng `rc_confirm_receipt` / `rc_issue_stock` / `rc_confirm_count`. |
| **6 · Khóa hàng và transaction** | Khóa tư vấn `(bảng, request_key)` trước khi tra cứu (mức 0) → hai lần gửi đồng thời cùng khóa: lần sau chờ rồi thấy chứng từ của lần trước. |
| **7 · Idempotency** | Cùng khóa + cùng payload (jsonb chuẩn hóa thứ tự khóa) → trả id cũ, kể cả khi đã xác nhận. Cùng khóa khác payload → RC409. Khác khóa → chứng từ mới. UNIQUE `request_key` là hàng rào thứ hai. |
| **8 · UNIQUE / FK / CHECK / trigger đỡ lưng** | `stock_receipts_request_key_key` (và tương tự), CHECK của dòng, `trg_*_lines` (dòng chỉ sửa khi DRAFT), I-18. |
| **9 · Lỗi và rollback** | RC403, RC422, RC409; lỗi một dòng (FK/CHECK) → không tạo header. |
| **10 · Tương thích nghiệp vụ v4** | Spec để ngỏ cách tạo phiếu; header + dòng nay tạo nguyên tử, client sinh `request_key` một lần cho mỗi thao tác "Tạo". |
| **11 · Ngoại lệ** | Phiếu nháp bỏ đi bằng `DELETE` (guard chỉ cho DRAFT) — khóa yêu cầu được giải phóng. Hoàn trả, hủy hàng, sơ chế, khoản chi giữ `request_key` UNIQUE sẵn có (gửi lặp nhận 23505, không có so khớp payload). |
| **12 · Kiểm thử thành công / thất bại** | File 05: khối QĐ-ID (lặp, khác payload, khác khóa, thiếu khóa, rỗng, sai vai trò, gửi lại sau xác nhận). File 06: C11 (đua cùng khóa cùng/khác payload, giữ khóa xác định). Mutant M11, L01 bị phát hiện. |

## 5. Hàm hỗ trợ, trigger và phân loại thực thi

**16 hàm hỗ trợ** (không cấp EXECUTE cho role ứng dụng; chỉ chạy bên trong hàm nghiệp vụ hoặc trigger):

| Hàm | Việc làm | Dùng bởi |
| --- | --- | --- |
| `rc_raise(code, message)` | Ném lỗi với SQLSTATE riêng | mọi hàm |
| `rc_authorize(actor, VARIADIC roles)` | Nhân viên ACTIVE + tài khoản ACTIVE + một trong các vai trò, nếu không RC403 (thay `rc_require_role`) | 31 hàm có `p_actor` |
| `rc_business_date(ts)` | Ngày kinh doanh, 04:00 Asia/Ho_Chi_Minh; được cấp cho `rc_app`/`rc_readonly` | thanh toán, chi phí, thực đơn ngày, đối soát, nhập/xuất kho, báo cáo |
| `rc_usable_until(expiry, shelf_hours)` | Hạn dùng tại bếp | `rc_receive_issue`, `rc_confirm_preparation` |
| `rc_quota_used(dmi)` | Số suất đã chiếm, suy ra từ trạng thái; được cấp cho `rc_app` (hiển thị) | nhận món, cảnh báo gửi món, trigger quota |
| `rc_batch_result(batch, replayed)` | Kết quả JSONB của gửi món kèm cảnh báo `QUOTA_EXCEEDED` | `rc_submit_batch` |
| `rc_request_hash(payload)` | sha256 của jsonb chuẩn hóa | `rc_create_*` |
| `rc_request_lookup(table, key, hash)` | Khóa tư vấn theo khóa yêu cầu; trả id cũ / NULL / RC409 khi khác payload | `rc_create_*` |
| `rc_doc_effect_missing(table, id, from, to)` | Chứng từ chuyển sang trạng thái có hiệu lực kho mà còn dòng thiếu bút toán | `rc_guard_doc_header`, bất biến I-17 |
| `rc_check_balance(kind, id)` | Số dư lưu = tổng sổ kho / tổng reservation ACTIVE (RC500 nếu lệch) | 5 constraint trigger hoãn |
| `rc_item_transition(item, from[], to, actor, reason)` | Khóa dòng, kiểm trạng thái nguồn, đổi trạng thái, ghi sự kiện | mọi bước của món |
| `rc_allocate_item(item)` | Cấp FEFO phần nguyên liệu còn thiếu; thiếu → RC001 | `rc_accept_item`, `rc_reallocate_item` |
| `rc_release_item_reservations(item, reason)` | ACTIVE → RELEASED, giảm reserved; không biến động vật lý | `rc_cancel_item`, `rc_abandon_session` |
| `rc_lock_invoice(invoice)` | Khóa orders → invoices → payment_requests đang mở | thanh toán, QR, mở lại, hủy request, webhook |
| `rc_invoice_total_checked(invoice)` | Đối chiếu dòng hóa đơn với đúng tập dòng SERVED; trả tổng | chốt hóa đơn, bỏ về, miễn thu |
| `rc_settle_invoice(invoice, actor)` | PAID → đóng đơn, phiên, trả bàn | `rc_pay_cash`, `rc_process_verified_qr` |

**22 hàm trigger (47 trigger)** giữ bất biến cho mọi đường ghi, kể cả DML trực tiếp của superuser/migration:

| Nhóm | Hàm trigger | Bảo vệ |
| --- | --- | --- |
| Chỉ-thêm | `rc_forbid_change` | `stock_movements`, `payments`, `order_item_events`, `attendance_adjustments` không UPDATE/DELETE |
| Không xóa | `rc_forbid_delete` | 9 bảng giao dịch |
| Thực đơn | `rc_guard_used_recipe`, `rc_guard_daily_menu_item`, `rc_guard_daily_menu` | công thức đã dùng bất biến; không hạ quota dưới số đã chiếm; đồ thị trạng thái thực đơn |
| Bán hàng | `rc_guard_dining_table`, `rc_guard_table_session`, `rc_guard_order`, `rc_guard_order_item`, `rc_guard_order_item_event`, `rc_check_item_event_sync` | bàn OCCUPIED ⇔ phiên OPEN; đơn CLOSED chỉ khi hóa đơn PAID/UNCOLLECTED/WAIVED, CANCELLED chỉ khi không có hóa đơn; snapshot dòng món (cả `quantity` — QĐ-5); CANCELLED là cuối; chuỗi sự kiện; đổi trạng thái không có sự kiện bị bắt lúc COMMIT |
| Hóa đơn, tiền | `rc_guard_invoice`, `rc_guard_invoice_items`, `rc_guard_payment_request`, `rc_guard_payment`, `rc_guard_webhook_event` | DRAFT → PAYMENT_PENDING → PAID; → UNCOLLECTED/WAIVED; ba trạng thái cuối bất biến; dòng hóa đơn là bản chụp đúng; payments chỉ khi PAYMENT_PENDING, đúng số và đúng ngày kinh doanh; chứng cứ webhook bất biến |
| Kho | `rc_trg_balance`, `rc_guard_reservation`, `rc_guard_doc_header`, `rc_guard_doc_lines` | số dư = sổ kho tại COMMIT; reservation một chiều; chứng từ: đồ thị trạng thái, **chuyển sang trạng thái có hiệu lực kho chỉ khi mọi dòng đã có bút toán** (`rc_doc_effect_missing`), trạng thái cuối bất biến, dòng bị khóa |
| Tài chính | `rc_guard_expense`, `rc_guard_cash_recon` | khoản chi đúng ngày kinh doanh, chỉ PAID → VOIDED; đối soát chỉ CURRENT → SUPERSEDED |

### 5.1 Phân loại: cái gì do ai thực thi

| Lớp | Thực thi bởi | Nội dung |
| --- | --- | --- |
| **Ràng buộc khai báo** (PK/FK/UNIQUE/CHECK/EXCLUDE) | PostgreSQL, mọi đường ghi | khóa, quan hệ (101 FK); miền giá trị trạng thái; cặp trường "đủ hoặc không" (hủy, khóa lô, UNCOLLECTED, WAIVED, đóng phiên…); một yêu cầu một chứng từ (`request_key` UNIQUE); một hóa đơn một payment; một phiên OPEN/bàn; không chồng ca (EXCLUDE) |
| **Trigger bảo vệ toàn vẹn** (47) | PostgreSQL, mọi đường ghi kể cả DML trực tiếp | đồ thị trạng thái (spec E2), snapshot bất biến, chỉ-thêm/không xóa, số dư = sổ kho (hoãn tới COMMIT), chứng từ ↔ sổ kho, payments ↔ hóa đơn. Trigger **không** tự thay đổi số dư hay sinh bút toán — chúng chỉ chặn |
| **Hàm nghiệp vụ** (32 `rc_*`) | PostgreSQL, gọi bởi `rc_app`/`rc_webhook` | mọi thay đổi số dư, bút toán, đặt giữ, quota, hóa đơn, thanh toán, đóng phiên; khóa theo thứ tự chung; vai trò của `p_actor` |
| **Spring Boot** (chưa có trong repo) | backend | xác thực JWT → `p_actor`; `@PreAuthorize` theo `permissions` (hạt mịn hơn vai trò); kiểm chữ ký/IP webhook SePay trước khi gọi `rc_process_verified_qr`; sinh `request_key` một lần mỗi thao tác; thử lại `40001/40P01`; ánh xạ SQLSTATE → HTTP; hiển thị cảnh báo `QUOTA_EXCEEDED`; CRUD danh mục/nhân sự/thực đơn và nháp chứng từ (cột được cấp ở 09) |

### 5.2 Rà soát 47 trigger và 6 trigger hoãn

Kết quả rà soát vòng đóng băng (đọc mã + ca kiểm + mutation):

- **Không trigger nào ghi dữ liệu nghiệp vụ** (ngoại lệ duy nhất: `rc_guard_webhook_event` tự đặt `resolution_status = 'OPEN'` cho sự cố mới trên chính dòng được chèn). Vì vậy không thể có "hai trigger cùng thay đổi một số dư", trừ hai lần hay hoàn hai lần do trigger; trừ/hoàn nằm trong hàm và được chặn trùng bởi `event_key` UNIQUE + trạng thái reservation một chiều (I-13, mutant M15).
- **Không trigger thừa.** Các cặp trông giống nhau bảo vệ đường ghi khác nhau: `trg_kitchen_batches_balance` (đổi số dư lô) và `trg_stock_reservations_balance` (đổi reservation mà không đổi lô — ca III "đặt giữ không cập nhật reserved_quantity") và `trg_stock_movements_balance` (chèn bút toán mà không đổi số dư — ca III "COOK thừa"); `trg_order_item_events_chain` (sự kiện chèn sai chuỗi, ngay lập tức) và `trg_order_items_event_sync` (đổi trạng thái không có sự kiện, lúc COMMIT). Bỏ bất kỳ trigger nào trong các cặp này đều làm một ca GRD/III thất bại. Số trigger giữ 47.
- **6 trigger hoãn** (`trg_inventory_batches_balance`, `trg_stock_issue_items_balance`, `trg_kitchen_batches_balance`, `trg_stock_reservations_balance`, `trg_stock_movements_balance`, `trg_order_items_event_sync`): (1) so **số dư cuối transaction** vì chạy lúc COMMIT và đọc trạng thái hiện tại; (2) **không lỗi giả** khi nhiều thay đổi hợp lệ trên cùng lô trong một transaction — ca III ép `SET CONSTRAINTS ALL IMMEDIATE` sau 2 lần nhận + nấu + hủy và đạt; (3) **không khóa**: chỉ `SELECT` không `FOR UPDATE`, không mở rộng tập khóa của hàm; (4) **không tạo deadlock mới** — 06 chạy toàn bộ ca đua (ITER = 15, 78 kiểm tra) không gặp `40P01`; (5) có ca riêng làm sai cố ý: sửa tay số dư RAW/WIP/bếp, reserved lệch, reservation chèn không qua lô, bút toán thừa, đổi trạng thái món không sự kiện — đều RC500 lúc COMMIT.
- **Không bypass trạng thái**: DML trực tiếp đổi trạng thái chứng từ kho sang ISSUED/RECEIVED/CONFIRMED mà không có bút toán nay bị chặn (DB-48); đổi trạng thái hóa đơn/đơn/món/phiên bị chặn theo đồ thị; cột trạng thái không được cấp `UPDATE` cho `rc_app`.
- **Không ghi đè lịch sử**: `stock_movements`, `payments`, `order_item_events`, `attendance_adjustments` chỉ-thêm; chứng từ, hóa đơn, phiên, đối soát ở trạng thái cuối bất biến; 9 bảng giao dịch không xóa.

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
| M7 | Áp ACL và test không cho DML trực tiếp | Script chính thức [09-acl](09-acl-v4.1.sql) + khối ACL (25 đường ghi tắt/leo thang) + GRD/CON | ACL, GRD, M18–M20 |
| M5 | Hóa đơn miễn phí | **Bổ sung có chủ đích bởi QĐ-MT**: WAIVED (Manager, lý do), không payments | QĐ-MT |
| 5.2–5.5 | Báo cáo doanh thu, kho, hao hụt, đối soát | 10 hàm `rpt_*` ([10](10-bao-cao-v4.1.sql)), kiểm số liệu tính trước ([11](11-test-bao-cao-v4.1.sql)) | 43 phép so sánh, M21–M24 |

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
| DB-48 | 🔴 | **Đặt trạng thái chứng từ kho trực tiếp không qua sổ**: `UPDATE stock_issues SET status = 'ISSUED'` được guard chấp nhận (đồ thị hợp lệ) nên RAW không bị trừ, rồi `rc_receive_issue` nhập kho bếp → tồn sinh "từ không khí". Tương tự RECEIVED, CONFIRMED (nhập, sơ chế, hoàn trả, hủy hàng, kiểm kê) | Ca GRD-FZ (trước khi vá: lệnh đi qua) | `rc_guard_doc_header` gọi `rc_doc_effect_missing`: chuyển sang trạng thái có hiệu lực kho chỉ khi mọi dòng đã có bút toán `event_key`; `rc_confirm_receipt` ghi sổ trước rồi đổi trạng thái; ACL không cấp `UPDATE(status)`; I-17; mutant M12, M18 |
| DB-49 | 🔴 | **`p_actor` không được kiểm vai trò** ở 29/31 hàm: lỗi ánh xạ endpoint ở backend đủ để Waiter thu tiền, Kitchen duyệt kho; tài khoản bị khóa vẫn thao tác được | Ma trận QĐ-BM (trước khi vá: 29 lời gọi đi qua) | `rc_authorize` ở dòng đầu mọi hàm; ma trận mục 10; mutant M01, M02 |
| DB-50 | 🟠 | Hóa đơn DRAFT treo vĩnh viễn: bỏ về / đóng phiên sau khi hủy hết món để lại hóa đơn DRAFT, đơn CANCELLED | Ca QĐ-2/3 (bản 25/10 khẳng định hành vi này) | `rc_guard_order`: CANCELLED chỉ khi không có hóa đơn; đóng bằng WAIVED; I-16; mutant M17 |
| DB-51 | 🟠 | Không có cách đóng hóa đơn không thu tiền (miễn thu, món giá 0): chốt hóa đơn 0 đồng bị chặn, bàn kẹt | Ca QĐ-MT | Trạng thái WAIVED + 3 cột + `rc_waive_invoice` |
| DB-52 | 🟠 | Bấm "Tạo" hai lần tạo hai phiếu nháp nhập/xuất/kiểm kê | Đọc mã | `request_key` UNIQUE + `request_hash` + `rc_create_*` (QĐ-ID); C11 |
| DB-53 | 🟠 | Khách bỏ về khi món SERVED đều giá 0 → UNCOLLECTED với total 0 vi phạm `ck_invoice_uncollected_fields` (23514) | Mutant M08 làm lộ | Tổng 0 → WAIVED |
| T-03 | 🟢 | Ca kiểm gộp nhiều điều kiện trong một biểu thức có hàm ghi dữ liệu (`rc_accept_item(...) = 'X' AND (SELECT …)`) phụ thuộc thứ tự ước lượng của planner (subquery chạy trước) | Ca mới thất bại giả khi viết | Tách mỗi lời gọi có tác dụng phụ thành một kiểm tra riêng |

## 8. Giới hạn chưa kiểm chứng và quyết định đã chốt

**Giới hạn chưa kiểm chứng**

1. Đã chạy trên PostgreSQL 15.19, 16.15, 17.11 và 18.6 (image Docker chính thức, Linux, cấu hình mặc định). Chưa chạy bản đóng gói khác hay bản vá cũ hơn; schema cần 15+. Mutation chỉ chạy trên 17.11.
2. Không đo hiệu năng, không `EXPLAIN ANALYZE`, không tải lớn. Ca đồng thời dùng 2–4 kết nối, 15 vòng mỗi ca đua; không phủ mọi cặp hàm.
3. Chỉ kiểm READ COMMITTED.
4. Tầng Spring Boot chưa có: ánh xạ JWT → `p_actor`, `@PreAuthorize` theo `permissions`, thử lại `40001/40P01`, ánh xạ mã lỗi → HTTP, xác thực chữ ký SePay chưa được kiểm. DB chỉ chặn được lỗi ánh xạ vai trò, không chặn được backend truyền **nhầm danh tính** của một nhân viên khác cùng vai trò (mục 10).
5. Role `rc_app` vẫn ghi trực tiếp được danh mục và nhân sự (kể cả `account_roles`) — đó là chức năng quản trị của ứng dụng; một backend bị chiếm quyền có thể tự gán vai trò. Muốn tách, cần role quản trị riêng (ngoài phạm vi Core).
6. Thời gian trôi (hết hạn lô, chuyển ngày 04:00) kiểm bằng hàm thuần với mốc cố định và bằng dữ liệu; chưa kiểm tại thời điểm chuyển ngày thật. Hạn lô RAW so theo ngày kinh doanh, hạn dùng bếp theo hết ngày lịch (`rc_usable_until`) — từ 00:00 đến 04:00 lô hết hạn hôm qua vẫn xuất được nhưng lô bếp sinh ra đã hết hạn dùng (an toàn, có thể gây REJECTED).
7. Báo cáo kiểm trên một ngày kinh doanh (dữ liệu tạo trong lúc chạy); lọc khoảng nhiều ngày chỉ kiểm phần "ngày khác rỗng".
8. Chưa kiểm di chuyển dữ liệu v3 → v4/v4.1 (spec M10). Không so sánh được mã v4 (mục 1).
9. Hoàn trả, hủy hàng, sơ chế, khoản chi dùng `request_key` UNIQUE sẵn có: gửi lặp nhận 23505 thay vì trả chứng từ cũ (QĐ-ID chỉ yêu cầu nhập/xuất/kiểm kê).

**Quyết định đã chốt ở vòng đóng băng** (thay bảng "cần nhóm xác nhận" của bản 25/10)

| # | Vấn đề cũ | Chốt |
| --- | --- | --- |
| 1 | QĐ-5 hủy một phần | Không hỗ trợ: hủy cả dòng, gửi dòng mới; dòng CANCELLED cuối |
| 2 | Ép vai trò ở DB cho các hàm còn lại | Có: `rc_authorize` cho 31 hàm, ma trận mục 10; lô hỏng (`rc_mark_batch_unusable`) thuộc Manager theo G7.8 |
| 3 | Vòng đời UNCOLLECTED | Trạng thái cuối của Core; không công nợ/thu bù; webhook sau đó chỉ lưu sự cố |
| 4 | Kiểm suất khi gửi món | Không chặn, trả cảnh báo; Kitchen kiểm cứng |
| 5 | `request_key` cho phiếu nhập/xuất/kiểm kê | Có (QĐ-ID) |
| 6 | Mốc 04:00 | Chốt Asia/Ho_Chi_Minh 04:00 (QĐ-NG) |
| 7 | Hóa đơn DRAFT 0 đồng | Đóng bằng WAIVED; không còn hóa đơn treo |

## 9. Kết quả kiểm thử

Lần chạy chốt 26/10/2026, mỗi bộ trên database mới, ở **PostgreSQL 15.19, 16.15, 17.11 và 18.6** (container `postgres:<bản>` trên Linux) với cùng kết quả. Catalog sau khi nạp 04: 48 bảng, 375 cột, 102 FK, 106 CHECK, 46 UNIQUE, 1 EXCLUDE, 52 index khai báo, 47 trigger (6 hoãn), 70 hàm `rc_*` (32 nghiệp vụ + 16 hỗ trợ + 22 trigger) và 10 hàm `rpt_*` khi nạp 10.

| Bộ kiểm thử | Kết quả (mỗi phiên bản PostgreSQL) |
| --- | --- |
| `05-test-v4.1.sql` (một kết nối, gồm `\ir 09-acl`) | **308 kiểm tra dương + 342 kiểm tra âm — đạt hết**; gồm 7 khối chính sách vòng đóng băng, ma trận quyền 31 hàm, ACL 25 đường ghi tắt, 22 truy vấn bất biến (nhãn I-1…I-17) |
| `06-test-concurrency-v4.1.mjs` (nhiều kết nối, ITER = 15) | **78/78 đạt**, không `40P01`, không kết quả ngoài dự kiến; nhóm C11 mới: miễn thu ↔ thu tiền, miễn thu/bỏ về ↔ webhook, `request_key` cùng/khác payload, hai Manager cùng xác nhận phiếu xuất |
| `08-vi-du-xuyen-suot.sql` (database trống riêng) | **34 phép so sánh đạt** |
| `10` + `11-test-bao-cao-v4.1.sql` (database trống riêng) | **43 phép so sánh đạt** (doanh thu, theo món, bỏ về/miễn thu, tồn RAW/bàn giao/WIP/bếp, lô hỏng, sổ kho, hao hụt, đối soát) |
| `12-mutation-v4.1.py` (17.11) | **27/27 mutant bị phát hiện**: 20 SQL/ACL (bộ 05), 4 báo cáo (bộ 11), 3 khóa (bộ 06). Vòng chạy đầu còn 1 mutant sống (M17 — đơn CLOSED khi hóa đơn DRAFT) → bổ sung ca GRD-FZ, chạy lại phát hiện |

Bộ mutation của bản 25/10 (40 SQL + 15 khóa, chạy trên Windows) **không có trong repository** nên không chạy lại được; `12-mutation-v4.1.py` thay thế, tập trung vào chính sách mới và các hàng rào cũ có liên quan.

## 10. Ma trận quyền và ranh giới tin cậy (QĐ-BM)

**Ranh giới tin cậy.** Spring Security xác thực JWT → danh tính thực (`employees.id`). Backend chỉ truyền `p_actor` lấy từ authentication context — không bao giờ từ tham số client. PostgreSQL không có principal riêng cho từng nhân viên nên **không tự xác thực** `p_actor`; nó cưỡng chế (a) role DB nào được gọi hàm nào, ghi bảng/cột nào ([09-acl](09-acl-v4.1.sql)) và (b) `p_actor` phải là nhân viên ACTIVE, tài khoản ACTIVE, mang vai trò được phép (`rc_authorize`). DB **không** dùng biến phiên tự đặt (`set_config`) để định danh — role ứng dụng tự đặt được nên không đáng tin. Muốn DB tự xác minh danh tính phải có principal DB theo từng người (ví dụ `SET ROLE` sang role đăng nhập riêng từng nhân viên) — không chọn cho Core vì mất connection pooling.

| Hàm | MANAGER | WAITER | KITCHEN | CASHIER | Ghi chú tài nguyên/trạng thái |
| --- | :-: | :-: | :-: | :-: | --- |
| `rc_open_session`, `rc_close_empty_session` | ✓ | ✓ | | | bàn đang dùng, phiên OPEN, không món/hóa đơn |
| `rc_submit_batch` | ✓ | ✓ | | | đơn OPEN, hóa đơn chưa chờ thu |
| `rc_serve_item` | ✓ | ✓ | | | READY → SERVED |
| `rc_cancel_item` | ✓ | ✓ | ✓ | | chỉ PENDING/ACCEPTED |
| `rc_accept_item`, `rc_reject_item`, `rc_start_cooking`, `rc_finish_dish`, `rc_reallocate_item` | ✓ | | ✓ | | đúng bước của đồ thị món |
| `rc_receive_issue`, `rc_confirm_preparation` | ✓ | | ✓ | | phiếu ISSUED; WIP đã nhận |
| `rc_checkout_invoice`, `rc_reopen_invoice`, `rc_pay_cash`, `rc_start_qr`, `rc_cancel_payment_request` | ✓ | | | ✓ | hóa đơn DRAFT/PAYMENT_PENDING |
| `rc_cancel_item_exception`, `rc_abandon_session`, `rc_waive_invoice`, `rc_mark_batch_unusable` | ✓ | | | | chỉ Manager (QĐ-2, QĐ-3, QĐ-MT, G7.8) |
| `rc_create_stock_receipt/_issue/_count`, `rc_confirm_receipt`, `rc_issue_stock`, `rc_cancel_issue`, `rc_confirm_return`, `rc_confirm_adjustment`, `rc_confirm_count` | ✓ | | | | chỉ Manager (kho) |
| `rc_reconcile_cash` | ✓ | | | | chỉ Manager |
| `rc_process_verified_qr` | — | — | — | — | không có `p_actor`; chỉ role `rc_webhook` sau khi backend xác thực chữ ký |

Manager có mọi thao tác vận hành (giám sát, thay ca). Core không phân bàn/khu vực theo nhân viên nên quyền "đúng tài nguyên" là đúng **trạng thái** của tài nguyên (cột phải), không phải "tài nguyên của tôi". Từ chối luôn là RC403, kiểm **trước** mọi khóa và đọc dữ liệu nghiệp vụ (ca QĐ-BM dùng id 0 vẫn nhận RC403).

**Role PostgreSQL** ([09-acl](09-acl-v4.1.sql), chạy lại được, tự kiểm cuối script):

| Role | Quyền |
| --- | --- |
| `rc_owner` (NOLOGIN, không superuser/BYPASSRLS) | sở hữu bảng và 70 hàm `rc_*`; hàm `SECURITY DEFINER` chạy với quyền này; `search_path = public, pg_temp` |
| `rc_app` | `SELECT` mọi bảng; `EXECUTE` 32 hàm nghiệp vụ + `rc_business_date`, `rc_quota_used`, `rpt_*`; DML danh mục/nhân sự/thực đơn; nháp chứng từ theo **cột** (không cột trạng thái/xác nhận); không DML bảng bán hàng, tiền, sổ kho, số dư, đối soát, `roles` |
| `rc_webhook` | chỉ `EXECUTE rc_process_verified_qr`; không đọc bảng |
| `rc_readonly` | `SELECT` mọi bảng trừ `accounts.password_hash`; `rpt_*` |
| `PUBLIC` | không quyền trên schema, bảng, hàm `rc_*`/`rpt_*` |

Các đường leo thang đã thử (đều `42501`): DML vào 15 bảng/cột nhạy cảm, gọi 6 hàm nội bộ (`rc_item_transition`, `rc_settle_invoice`, `rc_allocate_item`, …), `GRANT rc_owner`, `ALTER FUNCTION … SECURITY INVOKER`, tạo hàm/bảng trong schema (chiếm `search_path`). Waiter gọi hàm Manager qua `rc_app` → RC403.

## 11. Ngày kinh doanh (QĐ-NG)

`rc_business_date(ts) = ((ts AT TIME ZONE 'Asia/Ho_Chi_Minh') − 4 giờ)::date`: kết quả chỉ phụ thuộc thời điểm tuyệt đối (`TIMESTAMPTZ`), không phụ thuộc `TimeZone` của phiên. Mọi `business_date` lưu (payments, khoản chi, thực đơn ngày, đối soát) bị guard ép bằng đúng hàm này; backend nên gọi hàm thay vì tự tính. Ca QĐ-NG kiểm 10 mốc (03:59:59 → hôm trước; 04:00 và 04:01 → hôm nay; chuyển tháng 31/10 → 1/11; chuyển năm 31/12 → 1/1; 29/02 năm nhuận; mốc UTC 20:59:59Z/21:00Z) dưới 5 `TimeZone` phiên (UTC, Asia/Ho_Chi_Minh, America/Los_Angeles, Asia/Tokyo, Pacific/Kiritimati) và khoản chi 03:59 không ghi được vào ngày mới.

## 12. Đánh giá đóng băng thiết kế database

**Đủ điều kiện Database Design Freeze cho tầng PostgreSQL**, với các giới hạn ở mục 8. Đối chiếu 10 tiêu chí:

| # | Tiêu chí | Trạng thái | Bằng chứng |
| --- | --- | :-: | --- |
| 1 | Chính sách nghiệp vụ phản ánh nhất quán | ✅ | Mục 3; 04 header; 01/03 cập nhật; ca theo từng QĐ |
| 2 | 48 bảng có trách nhiệm rõ, ≥ 3NF, snapshot có giải thích | ✅ | [02 §12](02-danh-gia-database.md); không thêm bảng; 9 cột mới đều phụ thuộc khóa của bảng chứa nó |
| 3 | Không còn lỗ hổng đã biết gây trùng thanh toán, sai tồn, sai doanh thu | ✅ | DB-48…DB-53 đã vá; bất biến I-1…I-17 (05) và I-18 (06); báo cáo chỉ từ payments |
| 4 | Phân quyền dữ liệu và hàm được kiểm thử | ✅ | Mục 10; ma trận 31 hàm; ACL 25 đường; M01/M02/M18–M20 |
| 5 | Transaction quan trọng nguyên tử | ✅ | Mỗi hàm một đơn vị; ca rollback; trigger hoãn ở COMMIT |
| 6 | Không deadlock tái hiện được | ✅ | 06: 0 lần `40P01` trên 4 phiên bản; probe thứ tự khóa cho 8 hàm |
| 7 | SQL, concurrency, mutation, báo cáo đạt | ✅ | Mục 9 |
| 8 | ERD khớp DDL | ✅ | 5 file `.drawio` cập nhật cột mới; kiểm tự động bảng/cột/khóa ([README](README.md)) |
| 9 | Tài liệu đúng trạng thái triển khai | ✅ | 01, 02, 03, 07, README |
| 10 | Điểm chưa kiểm chứng ghi rõ | ✅ | Mục 8 |

Việc còn lại thuộc tầng ứng dụng (không chặn đóng băng DB): hiện thực Spring Security/`@PreAuthorize`, xác thực SePay, retry, ánh xạ lỗi; đo hiệu năng; script migration v3 → v4.1.
