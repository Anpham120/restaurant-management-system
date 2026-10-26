# RMS — Restaurant Core v4.1 (bản đóng băng thiết kế database): luồng nghiệp vụ, CSDL, kiểm thử và ERD

Bộ tài liệu dựng theo **Restaurant Core Master Specification v4** (48 bảng · 5 phân hệ · 27 nhóm chức năng · 4 actor + SePay): rà soát thiết kế v4, sửa toàn bộ phát hiện thành **schema v4.1** chạy được, chốt các quyết định nghiệp vụ QĐ-1…QĐ-8 và vòng đóng băng thiết kế (miễn thu, phân quyền, chứng từ idempotent, ngày kinh doanh, báo cáo), kiểm thử trên **PostgreSQL server thật** và 5 ERD vẽ bằng draw.io. Các ký hiệu `§D`, `G7`, `M3`, `I3`… trỏ về mục của Master Spec. File spec **không nằm trong repository**; nội dung được trích trong 02, 03 và 07 khi cần.

| File | Nội dung | Dùng khi |
| --- | --- | --- |
| [01-luong-chinh.md](01-luong-chinh.md) | 7 luồng chính L1–L7, sơ đồ ngữ cảnh, DFD mức 1, quy trình, trạng thái (gồm UNCOLLECTED/WAIVED), tuần tự QR/SePay, ví dụ xuyên suốt có số liệu | Nắm bức tranh tổng, thuyết trình, viết báo cáo |
| [02-danh-gia-database.md](02-danh-gia-database.md) | Rà soát theo phân hệ: liên kết, 3NF, index; 53 phát hiện (DB-01…DB-53) và cách đã xử lý; ERD Mermaid; 22 truy vấn bất biến; quyết định nghiệp vụ; kiểm toán 48 bảng | Hiểu vì sao schema như vậy |
| [03-luong-du-lieu-chuc-nang.md](03-luong-du-lieu-chuc-nang.md) | 27 thẻ chức năng: Actor → Điều kiện → Đọc → Ghi → Transaction/khóa → Trạng thái → Ngoại lệ → Test; ma trận CRUD; DFD mức 2 | Lập trình service/API, phân công |
| [04-schema-v4.1.sql](04-schema-v4.1.sql) | Schema: 48 bảng, 375 cột, 102 FK, 106 CHECK, 52 index, 47 trigger (6 hoãn tới COMMIT), **32 hàm nghiệp vụ `rc_*`** + 16 hàm hỗ trợ + 22 hàm trigger | Tạo database |
| [05-test-v4.1.sql](05-test-v4.1.sql) | 650 kiểm tra một kết nối: ma trận L1 của spec, mọi quyết định nghiệp vụ, ma trận quyền 31 hàm, guard trigger, ràng buộc, từng hàm, ACL/leo thang đặc quyền (nạp 09), 22 truy vấn bất biến | Sau khi tạo database |
| [06-test-concurrency-v4.1.mjs](06-test-concurrency-v4.1.mjs) | 78 kiểm tra nhiều kết nối (Node + `pg`): suất cuối, nguyên liệu cuối, tiền mặt ↔ webhook, webhook trùng, hủy ↔ nấu, bỏ về/miễn thu ↔ thanh toán và webhook, `request_key` đồng thời, hai Manager cùng xác nhận, thứ tự khóa, deadlock | Chứng minh khóa và thứ tự khóa |
| [07-kiem-toan-ham-rc.md](07-kiem-toan-ham-rc.md) | Kiểm toán 32 hàm × 12 tiêu chí; phân loại ràng buộc/trigger/hàm/Spring Boot; rà soát 47 trigger; **ma trận quyền và ranh giới tin cậy** (§10); kết quả kiểm thử; **đánh giá đóng băng** (§12) | Nghiệm thu, review code |
| [08-vi-du-xuyen-suot.sql](08-vi-du-xuyen-suot.sql) | Ví dụ xuyên suốt của 01 §11 chạy thật qua các hàm `rc_*`, 34 phép so sánh | Xem hệ thống chạy từ nhập kho tới đối soát |
| [09-acl-v4.1.sql](09-acl-v4.1.sql) | **Phân quyền chính thức**: `rc_owner` sở hữu, `rc_app` (backend), `rc_webhook` (SePay), `rc_readonly` (báo cáo); GRANT/REVOKE theo bảng và cột; tự kiểm cấu hình | Triển khai thật, sau 04 |
| [10-bao-cao-v4.1.sql](10-bao-cao-v4.1.sql) | 10 hàm báo cáo `rpt_*`: doanh thu theo ngày/món, bỏ về/miễn thu, tồn RAW, đang bàn giao/WIP, tồn bếp khả dụng, lô hỏng/hết hạn, lịch sử kho, hao hụt sơ chế, đối soát tiền mặt | Màn hình báo cáo |
| [11-test-bao-cao-v4.1.sql](11-test-bao-cao-v4.1.sql) | Bộ dữ liệu có kết quả tính trước cho 10 báo cáo, 43 phép so sánh | Sau 10, database riêng |
| [12-mutation-v4.1.py](12-mutation-v4.1.py) | Kiểm thử đột biến: 27 lỗi cài cố ý vào 04/09/10 phải bị 05/06/11 phát hiện | Chứng minh bộ test đủ nhạy |
| [erd/](erd/) | 5 ERD theo 5 phân hệ: `.drawio` (XML gốc) + `.png`; [kiem-erd-schema.py](erd/kiem-erd-schema.py) đối chiếu ERD với catalog | Báo cáo, chỉnh sửa trực quan |

## Quyết định nghiệp vụ (đã chốt toàn bộ — vòng đóng băng 26/10/2026)

| QĐ | Nội dung đã cài |
| --- | --- |
| QĐ-1 | `quota_total` NULL = không giới hạn suất, 0 = hết suất; không giới hạn suất vẫn kiểm nguyên liệu |
| QĐ-2 | Hủy trước COOKING theo quyền; từ COOKING chỉ Manager (`rc_cancel_item_exception`), bắt buộc lý do, không hoàn nguyên liệu/suất, không tính tiền |
| QĐ-3 | Khách bỏ về: Manager (`rc_abandon_session`), lý do + người; hóa đơn `UNCOLLECTED` là **trạng thái cuối** (không công nợ/thu bù); không `payments`, không doanh thu; trả bàn; QR đến sau chỉ lưu sự cố AFTER_CANCEL |
| QĐ-4 | Cashier chốt hóa đơn (đủ dòng, tự tính tổng, đối chiếu, PAYMENT_PENDING, khóa gọi món). **Gửi món khi hết suất: không chặn, trả cảnh báo `QUOTA_EXCEEDED`**; Kitchen chỉ ACCEPTED khi đủ suất và nguyên liệu |
| QĐ-5 | **Không hủy một phần**: hủy cả dòng, gửi dòng mới; dòng CANCELLED là cuối, không dùng lại; không hoàn/trừ hai lần |
| QĐ-6, QĐ-7 | Bếp tính theo `base_unit` nguyên liệu gốc; giữ bảng `orders` (48 bảng) |
| QĐ-8 | Suất chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING trả đúng một lần; từ COOKING không hoàn; nguyên tử, đồng thời không vượt |
| QĐ-MT | **Miễn thu** (yêu cầu gọi "QĐ-7 miễn thu"): `WAIVED` — Manager, lý do, người, thời điểm; giữ `invoice_items`/giá; không payments, không doanh thu; không áp cho PAID; hóa đơn 0 đồng đóng bằng WAIVED |
| QĐ-ID | Phiếu nhập/xuất/kiểm kê tạo bằng `rc_create_*` với `request_key`: cùng khóa + payload → phiếu cũ; khác payload → RC409 |
| QĐ-NG | Ngày kinh doanh 04:00 `Asia/Ho_Chi_Minh`, `TIMESTAMPTZ`, không phụ thuộc TimeZone của phiên |
| QĐ-BM | Mọi hàm có `p_actor` kiểm vai trò trong DB (ma trận [07 §10](07-kiem-toan-ham-rc.md)); `p_actor` chỉ lấy từ principal Spring Security đã xác thực; ACL chính thức ở 09 |

## Chạy

Cần PostgreSQL 15+ (`UNIQUE NULLS NOT DISTINCT`), extension `btree_gist` và **database hạ được chữ hoa có dấu tiếng Việt** — locale `C` làm `UNIQUE (lower(name))` hở nên file 04 từ chối nạp:

```sql
CREATE DATABASE restaurant_core TEMPLATE template0 ENCODING 'UTF8'
       LOCALE_PROVIDER icu ICU_LOCALE 'vi-VN';      -- hoặc LC_COLLATE/LC_CTYPE 'en_US.UTF-8'
```

```bash
cd docs
psql -v ON_ERROR_STOP=1 -d restaurant_core -f 04-schema-v4.1.sql
psql -v ON_ERROR_STOP=1 -d restaurant_core -f 05-test-v4.1.sql    # tự \ir 09-acl; in 'TEST SUMMARY'; không lỗi = mọi ca đạt

npm i pg                                                          # chỉ cần cho file 06
node 06-test-concurrency-v4.1.mjs postgresql://user:pass@host:5432/restaurant_core   # ITER=15 vòng đua, đổi bằng biến ITER

# ví dụ xuyên suốt và báo cáo: mỗi bộ một database trống RIÊNG (dùng id/số cố định)
psql -v ON_ERROR_STOP=1 -d restaurant_core_example -f 04-schema-v4.1.sql -f 08-vi-du-xuyen-suot.sql
psql -v ON_ERROR_STOP=1 -d restaurant_core_report  -f 04-schema-v4.1.sql -f 10-bao-cao-v4.1.sql -f 11-test-bao-cao-v4.1.sql

# đột biến (tạo/xóa database rc_mutation) và đối chiếu ERD ↔ schema
python3 12-mutation-v4.1.py --psql "psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres" \
        --url "postgresql://postgres:pass@127.0.0.1:5432/{db}" --node-dir /đường/dẫn/có/node_modules
python3 erd/kiem-erd-schema.py --psql "psql -X -At -h 127.0.0.1 -U postgres -d restaurant_core"
```

- Chạy 05 bằng superuser **trong thư mục `docs/`** (05 nạp `09-acl-v4.1.sql` bằng `\ir`): tạo các role nhóm `rc_owner`, `rc_app`, `rc_webhook`, `rc_readonly` (của cả cụm, giữ lại) và ba role thử (xóa cuối khối).
- Chạy 04 và 05 **trên database trống, dùng thử nghiệm**; 06 tự tạo dữ liệu mang tiền tố riêng nên chạy lại được.
- **Triển khai thật:** 04 → 09 → 10, rồi tạo tài khoản đăng nhập trong role nhóm (`CREATE ROLE rms_api LOGIN … IN ROLE rc_app`, `CREATE ROLE rms_sepay LOGIN … IN ROLE rc_webhook`). Backend truyền `p_actor` từ JWT đã xác thực, đọc `batch_id`/`warnings` từ `rc_submit_batch`, sinh `request_key` một lần cho mỗi thao tác tạo phiếu, thử lại `40001/40P01`.

## Mở ERD bằng draw.io

- **app.diagrams.net** hoặc **draw.io Desktop**: *File → Open from → Device*, chọn file `.drawio` (hoặc kéo thả vào cửa sổ).
- Muốn dán dạng mã: *Extras → Edit Diagram*, dán toàn bộ nội dung file `.drawio`, bấm *OK*.
- **VS Code**: cài extension *Draw.io Integration* (`hediet.vscode-drawio`) rồi mở file `.drawio` trực tiếp.

Mỗi bảng là shape *Table* của thư viện Entity Relation (sửa cột, kéo bảng thoải mái). Quan hệ dùng ký hiệu chân gà gắn đúng dòng FK → PK nên đi theo bảng khi di chuyển; nếu đường nối bị rối sau khi sắp lại, chọn hết các cạnh rồi dùng *Arrange → Waypoints → Clear*. Dưới mỗi sơ đồ có khung chú thích ký hiệu, quyết định nghiệp vụ, UNIQUE/EXCLUDE đặc biệt và miền giá trị trạng thái. Giao diện sáng, nền trắng, chữ đen.

## Mức xác minh

Chạy ngày 26/10/2026 trên **PostgreSQL 15.19, 16.15, 17.11 và 18.6** (image Docker `postgres:<bản>` chính thức trên Linux, cấu hình mặc định, `fsync = on`); mỗi bộ trên database mới; kết quả giống nhau ở cả bốn phiên bản; mutation chạy trên 17.11:

| Bộ kiểm | Kết quả |
| --- | --- |
| 05 — SQL một kết nối (gồm ACL) | 308 kiểm tra dương + 342 kiểm tra âm, **đạt hết** |
| 06 — nhiều kết nối | **78/78** đạt, không có deadlock `40P01` |
| 08 — ví dụ xuyên suốt | **34/34** phép so sánh đạt |
| 10 + 11 — báo cáo | **43/43** phép so sánh với số tính trước |
| 12 — mutation | **27/27** lỗi cài cố ý bị phát hiện (20 SQL/ACL, 4 báo cáo, 3 khóa) |
| ERD ↔ schema | 5 file `.drawio` khớp catalog: **1 231 kiểm tra, 0 lỗi** (bảng, cột, NULL, PK, FK); không khối nào chồng nhau sau khi thêm cột |

**Chưa kiểm chứng** (chi tiết [07 §8](07-kiem-toan-ham-rc.md)): hiệu năng và tải lớn; mức cô lập khác READ COMMITTED; tầng Spring Boot (JWT → `p_actor`, `@PreAuthorize`, retry, ánh xạ lỗi, chữ ký SePay); chuyển ngày 04:00 và hết hạn lô theo thời gian thật (kiểm bằng mốc cố định và dữ liệu); báo cáo nhiều ngày; migration v3 → v4.1. Repository **không chứa SQL v4 gốc** nên đối chiếu theo hợp đồng của Master Spec ([07 §1, §6](07-kiem-toan-ham-rc.md)). **Kết luận đóng băng database:** [07 §12](07-kiem-toan-ham-rc.md).

## Xem sơ đồ Mermaid

Sơ đồ trong 01–03 viết bằng Mermaid: GitHub hiển thị trực tiếp; trong VS Code dùng extension **Mermaid Chart** hoặc *Markdown Preview Mermaid Support*.
