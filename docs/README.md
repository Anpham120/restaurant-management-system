# RMS — Restaurant Core v4.1 (bản chốt nghiệp vụ): luồng nghiệp vụ, CSDL, kiểm thử và ERD

Bộ tài liệu dựng theo **Restaurant Core Master Specification v4** (48 bảng · 5 phân hệ · 27 nhóm chức năng · 4 actor + SePay): rà soát thiết kế v4, sửa toàn bộ phát hiện thành **schema v4.1** chạy được, chốt các quyết định nghiệp vụ QĐ-1…QĐ-8, kiểm thử trên **PostgreSQL server thật** và 5 ERD vẽ bằng draw.io. Các ký hiệu `§D`, `G7`, `M3`, `I3`… trỏ về mục của Master Spec. File spec **không nằm trong repository**; nội dung được trích trong 02, 03 và 07 khi cần.

| File | Nội dung | Dùng khi |
| --- | --- | --- |
| [01-luong-chinh.md](01-luong-chinh.md) | 7 luồng chính L1–L7, sơ đồ ngữ cảnh, DFD mức 1, quy trình, trạng thái, tuần tự QR/SePay, ví dụ xuyên suốt có số liệu | Nắm bức tranh tổng, thuyết trình, viết báo cáo |
| [02-danh-gia-database.md](02-danh-gia-database.md) | Rà soát theo phân hệ: liên kết, 3NF, index; 47 phát hiện (DB-01…DB-47) và cách đã xử lý; ERD Mermaid; 19 truy vấn bất biến; quyết định QĐ-1…QĐ-8; kiểm toán 48 bảng và luồng dữ liệu | Hiểu vì sao schema như vậy |
| [03-luong-du-lieu-chuc-nang.md](03-luong-du-lieu-chuc-nang.md) | 27 thẻ chức năng: Actor → Điều kiện → Đọc → Ghi → Transaction/khóa → Trạng thái → Ngoại lệ → Test; ma trận CRUD; DFD mức 2 | Lập trình service/API, phân công |
| [04-schema-v4.1.sql](04-schema-v4.1.sql) | Schema đầy đủ: 48 bảng, 366 cột, 101 FK, 105 CHECK, 51 index, 47 trigger, **28 hàm nghiệp vụ `rc_*`** + 12 hàm hỗ trợ + 22 hàm trigger | Tạo database |
| [05-test-v4.1.sql](05-test-v4.1.sql) | 444 kiểm tra một kết nối: ma trận L1 của spec, 5 quyết định nghiệp vụ, guard trigger, ràng buộc, từng hàm, ACL, 19 truy vấn bất biến | Sau khi tạo database |
| [06-test-concurrency-v4.1.mjs](06-test-concurrency-v4.1.mjs) | 67 kiểm tra nhiều kết nối (Node + `pg`): hai Kitchen cùng suất cuối, tiền mặt ↔ webhook QR, webhook trùng, hủy ↔ nấu, bỏ về ↔ thanh toán, deadlock | Chứng minh khóa và thứ tự khóa |
| [07-kiem-toan-ham-rc.md](07-kiem-toan-ham-rc.md) | Kiểm toán 28 hàm × 12 tiêu chí, hàm hỗ trợ và trigger, đối chiếu hợp đồng Master Spec, phát hiện có bằng chứng, giới hạn và điểm chờ nhóm xác nhận | Nghiệm thu, review code |
| [08-vi-du-xuyen-suot.sql](08-vi-du-xuyen-suot.sql) | Ví dụ xuyên suốt của 01 §11 chạy thật qua các hàm `rc_*`, 32 phép so sánh | Xem hệ thống chạy từ nhập kho tới đối soát |
| [erd/](erd/) | 5 ERD theo 5 phân hệ: `.drawio` (XML gốc) + `.png` xem nhanh | Báo cáo, chỉnh sửa trực quan |

## Quyết định nghiệp vụ đã áp dụng

| QĐ | Nội dung đã cài | Trạng thái |
| --- | --- | --- |
| QĐ-1 | `quota_total` NULL = không giới hạn suất, 0 = hết suất; không giới hạn suất vẫn kiểm nguyên liệu | nhóm chốt |
| QĐ-2 | Hủy trước COOKING theo quyền; từ COOKING chỉ Manager (`rc_cancel_item_exception`), bắt buộc lý do, lưu người + lịch sử, không hoàn nguyên liệu/suất, không tính tiền | nhóm chốt |
| QĐ-3 | Khách bỏ về: Manager (`rc_abandon_session`), lý do + người; hóa đơn `UNCOLLECTED`, đóng phiên, trả bàn; không `payments`, không PAID, không vào doanh thu | nhóm chốt |
| QĐ-4 | Cashier chốt hóa đơn: đủ `invoice_items`, tự tính tổng, đối chiếu đơn, PAYMENT_PENDING; Waiter không gửi thêm; khóa orders → invoices | nhóm chốt |
| QĐ-6, QĐ-7 | Mọi loại bếp tính theo `base_unit` nguyên liệu gốc; giữ bảng `orders` (48 bảng) | áp dụng (đề xuất rõ, không mâu thuẫn) |
| QĐ-8 | Suất chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING trả đúng một lần; từ COOKING không hoàn; kiểm suất + giữ nguyên liệu nguyên tử | nhóm chốt |
| **QĐ-5** | Hủy một phần số lượng một dòng — hai phương án nên **giữ hiện trạng** (hủy cả dòng, gửi lại số đúng) | **chờ nhóm xác nhận** |

Các điểm khác cần nhóm xác nhận nằm ở [07 §8](07-kiem-toan-ham-rc.md).

## Chạy

Cần PostgreSQL 15+ (`UNIQUE NULLS NOT DISTINCT`), extension `btree_gist` (có sẵn trong bản cài chuẩn) và **database hạ được chữ hoa có dấu tiếng Việt** — locale `C` làm `UNIQUE (lower(name))` hở, nên file 04 từ chối nạp ngay:

```sql
CREATE DATABASE restaurant_core TEMPLATE template0 ENCODING 'UTF8'
       LOCALE_PROVIDER icu ICU_LOCALE 'vi-VN';      -- hoặc LC_COLLATE/LC_CTYPE 'en_US.UTF-8'
```

```bash
psql -v ON_ERROR_STOP=1 -d restaurant_core -f 04-schema-v4.1.sql
psql -v ON_ERROR_STOP=1 -d restaurant_core -f 05-test-v4.1.sql    # in 'TEST SUMMARY'; không lỗi = mọi ca đạt

npm i pg                                                          # chỉ cần cho file 06
node 06-test-concurrency-v4.1.mjs postgresql://user:pass@host:5432/restaurant_core   # ITER=15 vòng đua, đổi bằng biến ITER

# ví dụ xuyên suốt: database trống RIÊNG (dùng id cố định, không trộn với 05)
psql -v ON_ERROR_STOP=1 -d restaurant_core_example -f 04-schema-v4.1.sql
psql -v ON_ERROR_STOP=1 -d restaurant_core_example -f 08-vi-du-xuyen-suot.sql
```

- Chạy 05 bằng superuser: khối ACL cuối file tạo rồi xóa hai role thử (`rc_app_probe`, `rc_webhook_probe`) để chứng minh role ứng dụng chỉ ghi được qua hàm `rc_*`.
- Chạy 04 và 05 **trên database trống, dùng thử nghiệm**; 06 tự tạo dữ liệu mang tiền tố riêng nên chạy lại được.
- Triển khai thật: cấp quyền theo đoạn gợi ý cuối file 04 (role ứng dụng chỉ `EXECUTE` các hàm `rc_*` + `SELECT`; role webhook chỉ `EXECUTE rc_process_verified_qr`).

## Mở ERD bằng draw.io

- **app.diagrams.net** hoặc **draw.io Desktop**: *File → Open from → Device*, chọn file `.drawio` (hoặc kéo thả vào cửa sổ).
- Muốn dán dạng mã: *Extras → Edit Diagram*, dán toàn bộ nội dung file `.drawio`, bấm *OK*.
- **VS Code**: cài extension *Draw.io Integration* (`hediet.vscode-drawio`) rồi mở file `.drawio` trực tiếp.

Mỗi bảng là shape *Table* của thư viện Entity Relation (sửa cột, kéo bảng thoải mái). Quan hệ dùng ký hiệu chân gà gắn đúng dòng FK → PK nên đi theo bảng khi di chuyển; nếu đường nối bị rối sau khi sắp lại, chọn hết các cạnh rồi dùng *Arrange → Waypoints → Clear*. Dưới mỗi sơ đồ có khung chú thích ký hiệu, quyết định nghiệp vụ, UNIQUE/EXCLUDE đặc biệt và miền giá trị trạng thái. Giao diện sáng, nền trắng, chữ đen.

## Mức xác minh

Chạy ngày 25/10/2026 trên **PostgreSQL 15.18, 16.14, 17.5 và 18.4** (server thật, bản nhúng trên Windows, `fsync = off`); schema, 05, 06 và 08 cho cùng kết quả ở cả bốn phiên bản, mutation chỉ chạy trên 17.5:

| Bộ kiểm | Kết quả |
| --- | --- |
| 05 — SQL một kết nối | 198 kiểm tra dương + 246 kiểm tra âm, **đạt hết** |
| 06 — nhiều kết nối | **67/67** đạt, không có deadlock `40P01` |
| 08 — ví dụ xuyên suốt | **32/32** phép so sánh đạt |
| Mutation SQL | **40/40** lỗi cài cố ý vào schema bị bộ 05 phát hiện |
| Mutation khóa | **12/15** bị bộ 06 phát hiện; 3 mutant còn lại là mutant tương đương ([07 §9](07-kiem-toan-ham-rc.md)) |
| ERD ↔ schema | 5 file `.drawio` khớp danh mục schema (1.865 kiểm tra: bảng, cột, PK/FK/UK, quan hệ, bản số chân gà, bảng không chồng/cắt); hiển thị bằng chính bộ hiển thị của draw.io không lỗi |
| Ma trận CRUD, DFD, ERD Mermaid | Sinh bằng script từ schema đã nạp và từ các thẻ chức năng nên khớp tên bảng/cột |

**Chưa kiểm chứng:** chạy trên Linux hoặc bản PostgreSQL đóng gói khác; đo hiệu năng và tải lớn; mức cô lập khác READ COMMITTED; tầng Spring Boot (JWT → `p_actor`, `@PreAuthorize`, thử lại `40001/40P01`); script GRANT chính thức; chuyển ngày kinh doanh 04:00 và hết hạn lô theo thời gian thật (đang mô phỏng bằng sửa dữ liệu); báo cáo số liệu (5.2, 5.4, 5.5); migration v3 → v4/v4.1. Repository **không chứa SQL v4 gốc** nên không so sánh được mã v4 ↔ v4.1 và không có tuyên bố tương đương; đối chiếu được thực hiện theo hợp đồng của Master Spec ([07 §1, §6](07-kiem-toan-ham-rc.md)).

## Xem sơ đồ Mermaid

Sơ đồ trong 01–03 viết bằng Mermaid: GitHub hiển thị trực tiếp; trong VS Code dùng extension **Mermaid Chart** hoặc *Markdown Preview Mermaid Support*.
