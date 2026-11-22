# Báo cáo Lập trình nâng cao — Atlas Restaurant

File nộp: [BaoCao_LapTrinhNangCao_AtlasRestaurant.docx](BaoCao_LapTrinhNangCao_AtlasRestaurant.docx).
Bìa, header, footer và style lấy từ báo cáo Triển khai phần mềm; Times New Roman 13, giãn dòng 1,3, gạch đầu dòng 3 cấp
"-", "+", "✓"; mọi sơ đồ vẽ bằng PlantUML.

Mọi số liệu, sơ đồ và ảnh trong báo cáo đều sinh bằng code từ hệ thống thật. Không có dữ liệu chèn bằng tay.

| Thư mục | Nội dung | Dùng ở mục |
| --- | --- | --- |
| `seed/` | `seed_baocao.py`: sinh dữ liệu bằng cách gọi REST API như người dùng thật (105 lượt khách, đủ trạng thái) | 3.1.6 |
| `erd/` | `catalog.sql` đọc cấu trúc DB từ `pg_catalog` → `catalog.json` → `gen_erd.py` sinh `.puml` → ảnh ERD | 3.1.1–3.1.5 |
| `du-lieu-mau/` | `gen_sample_sql.py` sinh câu SQL lấy 5 dòng mẫu mỗi bảng (`mau.sql` → `mau.json`) và thống kê trạng thái | 3.1.6–3.1.11 |
| `uml/` | Use case, activity, class (kiến trúc Modular Monolith, các tầng module order, thanh toán Strategy), deployment | 3.2 |
| `code-anh/` | Ảnh code có số dòng, chụp trực tiếp từ mã nguồn (`snippets-tv.json`: đoạn code của từng thành viên) | 3.1, 4.2 |
| `test/` | `tc_runner.py` chạy 40 test case qua API, kết quả `ket-qua.json`, `test-case.csv` (bản đầy đủ ở Google Sheet) | 4.3 |
| `giao-dien/` | `chup_giao_dien.py` chụp màn hình giao diện thật bằng Playwright | 4.3, 5.1 |
| `build/` | Script dựng file docx từ mẫu `build/mau/mau-bia.docx` | toàn bộ |

Test case đầy đủ (40 ca, kết quả lần 1, lần 2): https://docs.google.com/spreadsheets/d/1VI1XD4DCVjGwWdVOkKhn3D6lPUqS4fjdb_ct4YBF5H8/edit

## Môi trường sinh dữ liệu

Chạy trên một bản riêng của hệ thống (Docker Compose, chỉ nghe `127.0.0.1`), không đụng production. Mật khẩu quản lý
và khóa SePay truyền qua biến môi trường, không ghi trong repo; `TamThoi@2026`, `NhanVien@2026` là mật khẩu của tài
khoản nhân viên mẫu do chính script tạo ra trong bản chạy riêng này.

```bash
BASE=http://127.0.0.1:28080 MANAGER_PASSWORD=... SEPAY_API_KEY=... python3 seed/seed_baocao.py
BASE=http://127.0.0.1:28080 MANAGER_PASSWORD=... SEPAY_API_KEY=... python3 test/tc_runner.py > test/ket-qua.json
```

## Dựng lại báo cáo

Cần Python 3 và Docker (LibreOffice chạy trong container để render, lấy số trang cho mục lục và danh sách hình, bảng).

```bash
docs/bao-cao-ltnc/build/lam.sh        # dựng → render → cập nhật số trang → dựng lại
```

- Nội dung từng chương: `build/ch12.py` (chương 1, 2), `build/ch3.py` (chương 3), `build/ch45.py` (chương 4, 5, tài liệu tham khảo).
- Đoạn code của thành viên ở mục 4.2: sửa khoảng dòng trong `code-anh/snippets-tv.json`, chụp lại bằng
  `code-anh/render_code_tv.py` (container `mcr.microsoft.com/playwright/python`, mount thư mục này vào `/w`, repo vào `/repo`);
  lời giải thích nằm trong danh sách `CODE` của `build/ch45.py`.
- Sơ đồ: sửa `.puml` rồi render bằng `plantuml/plantuml:1.2025.7 -tpng -charset UTF-8`.

## Còn phải điền

- Bảng 4.1: phần trăm hoàn thành và mức đánh giá (Khá, Tốt) của từng thành viên.
- Bìa: tên giảng viên hướng dẫn.
- Mục 4.2.2–4.2.5: mỗi thành viên tự viết lại phần giải thích đoạn code của mình.
