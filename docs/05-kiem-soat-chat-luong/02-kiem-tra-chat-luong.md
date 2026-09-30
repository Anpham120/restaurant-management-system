# Báo cáo kiểm tra chất lượng và đối chiếu persona

> Phương pháp: skill `requirements-quality-check`, `deliverable-consistency-check`, `requirements-gap-auditor`, `model-consistency-checker`.

## 1. Kiểm tra tự động (script `check-docs.mjs`, `build-mermaid-check.mjs`)

| Kiểm tra | Kết quả |
|---|---|
| Mọi mã tham chiếu (FR, BR, UC, NFR, TR, AC, OI, DEC, ASM, CON, RSK, INV, ADR, SD, ST) đều tồn tại | ✅ 0 tham chiếu hỏng |
| Cột UC và BR trong bảng FR trỏ tới mã có thật; khớp với danh sách FR của từng UC | ✅ 0 lệch (sau khi sửa FR-MNU-10, FR-TBL-06) |
| Mỗi quy tắc nghiệp vụ có ít nhất một FR thực thi | ✅ 60/60 (sau khi gắn BR-60 vào FR-GST-15) |
| FR Must của thí điểm có tiêu chí chấp nhận trực tiếp | ⚠️ 59/76. 17 FR còn lại là cấu hình, CRUD, hiển thị cơ bản (ADM, MNU cơ bản, TBL cơ bản, BIL-01, RPT-02, INT-05), đã được use case bao phủ. Rủi ro thấp |
| *(CR-01)* FR của QR-1 (FR-GST-01 … 21) có tiêu chí chấp nhận | ✅ 21/21 (AC-43 … 63). Ba FR của QR-2 (Could) chưa cần AC |
| Sơ đồ Mermaid parse và render (mermaid v11, trình duyệt) | ✅ 58/58 |
| Số lượng (sau CR-01) | 163 FR · 46 NFR · 16 TR · 60 BR · 40 UC · 63 AC |

## 2. Rà soát thủ công

| Hạng mục | Kết quả |
|---|---|
| Từ ngữ mơ hồ ("nhanh", "dễ dùng") | ✅ Đã thay bằng số đo (NFR-01…06). N1 được ghi rõ là **mục tiêu thử nghiệm**, chưa phải ngưỡng nghiệm thu (khách yêu cầu) |
| Mâu thuẫn | ✅ 5 xung đột đã giải quyết và ghi lại ([sổ quy tắc §8](../02-phan-tich-nghiep-vu/03-quy-tac-nghiep-vu.md)), gồm cả mâu thuẫn trong chính lời khách (MoSCoW Buổi 4 ↔ Buổi 5) |
| Tách sự kiện, suy luận, đề xuất | ✅ Có ký hiệu [F]/[I] trong tóm tắt; ✅/🟡/🔵 trong sổ quy tắc và NFR |
| Phủ bên liên quan | ✅ Sau Buổi 8, đã phỏng vấn đủ quản lý 3 quán |
| Nhất quán mô hình | ✅, với 2 lưu ý (UPS chưa báo giá; phụ thuộc kết quả S1, S7) — xem [tương tác và trạng thái §C](../04-thiet-ke-he-thong/04-tuong-tac-trang-thai.md) |
| **Kiểm soát thay đổi** *(CR-01)* | ✅ Có quy trình đầy đủ: khảo sát → hội thảo tác động → xác nhận → hồ sơ [CR-01](03-yeu-cau-thay-doi-CR01.md) → cập nhật mọi tài liệu bị ảnh hưởng → kiểm tra tự động lại. Mọi mục mới được đánh dấu *(CR-01)* |
| **Công nghệ** | ✅ Kiến trúc chuyển sang **Spring Boot + React + PostgreSQL + CI/CD** (ADR-11, 12; [mã nguồn và CI/CD](../04-thiet-ke-he-thong/07-ma-nguon-va-cicd.md)); pattern lấy từ [11 repo tham khảo](../04-thiet-ke-he-thong/06-tham-khao-repo.md) |
| **Mức sẵn sàng** | **Sẵn sàng cho bước kiểm tra kỹ thuật 2 tuần (S1–S12).** **Chưa** sẵn sàng xây dựng cho tới khi đóng các vấn đề mở mức Cao: OI-05 (điều khoản cọc), OI-06 (thuế suất 2027), OI-08 (ngân hàng), OI-13 (thử phần mềm có sẵn), *(CR-01)* OI-24 (tuyến xác nhận tự thanh toán) |

## 3. Đối chiếu với persona giấu của khách giả lập

Sau khi khách ký baseline (Buổi 6), BA mới mở file `.client-sim/persona-private.md` do Codex tạo ở lượt 1. Mục đích là đo xem phỏng vấn **khai thác được bao nhiêu**.

### 3.1 Ba "sự thật giấu"

| Sự thật giấu | Khám phá ở | Kết quả |
|---|---|---|
| H1 Cọc (đôi khi vào tài khoản cá nhân chủ), cấn trừ, tiệc công ty nhiều cách trả tiền, cọc bị tính trùng | S2-M1, S4-F4, F5, S8-Q3 | ✅ BR-10…12, BR-26, FR-RSV-03…07 |
| H2 Bếp sơ chế chung, chuyển kho ghi sổ tay, hao hụt và hàng trả thiếu ghi, chi phí phân bổ theo doanh thu | S1-Q8, S3-P1…P4, S4-F8 | ✅ FR-PRP, BR-34, BR-35 |
| H3 Cầu Giấy mất mạng, nhập bù gây trùng phiếu (tháng 8) | S1-Q5, S2-M7, S8-T1 | ✅ FR-OFF-01…06, BR-38 |

### 3.2 Điểm khai thác chưa đủ

Tổng hợp theo checklist 41 mục rút từ persona: 31 ✅, 8 🟡, 2 ❌, tương đương **độ phủ khoảng 85%**.

| Hạng mục | Mức | Hành động đề xuất |
|---|---|---|
| **Bếp đóng lúc 22:00**, riêng tiệc lớn được làm muộn hơn nếu quản lý duyệt | ❌ → ✅ | Ghi thành OI-22; **đã giải quyết ở Buổi 9–10** (BR-46: món ăn 21:45, đồ uống chai ~22:15) |
| **Theo dõi đánh giá trên Google Maps và Facebook** | ❌ | Chưa hỏi. Đề xuất đưa vào danh sách việc tương lai, ngoài phạm vi hiện tại |
| **Trần ngân sách có thể nới tới ~600 triệu** nếu hợp lý và chia giai đoạn | 🟡 → ✅ | Khách chỉ nói "có thể bàn thêm". **Đã khai thác được ở Buổi 9** khi hỏi thẳng chi phí của CR-01 (CON-01) |
| Chủ muốn một **ngưỡng chênh lệch kho được duyệt** | 🟡 | Thêm vấn đề mở: ngưỡng chênh lệch chấp nhận theo mặt hàng (chủ quyết định) |
| Mục tiêu khớp số: persona ghi < 45 phút, khách nói và xác nhận < 1 giờ | 🟡 | Giữ theo lời khách đã xác nhận (G2) |
| Giờ cao điểm cụ thể; số đơn giao/mang về mỗi ngày; kênh beFood trong tương lai; hệ thống tài khoản kế toán; cách thay thế nguyên liệu | 🟡 | Được bao phủ gián tiếp qua OI-14, S7, OI-03. Kênh bán là dữ liệu cấu hình, nên thêm beFood không cần sửa thiết kế |

### 3.3 BA phát hiện thêm, persona **không** có

- **Tết 2027 và cao điểm tất niên** trùng lịch thí điểm → go-live dời sang ~22/02/2027.
- **VAT 8% hết hạn 31/12/2026** → thuế suất cấu hình theo ngày hiệu lực (NFR-23, OI-06).
- **Quy tắc làm tròn tiền mặt**; **nhập bù mặc định không gửi bếp**; **Wi-Fi yếu ở bàn mặt tiền** Cầu Giấy.
- **Văn bản pháp lý mới:** NĐ 254/2026 (HĐĐT) và NĐ 68/2026 (thuế hộ kinh doanh) đã được kiểm chứng trên cổng Chính phủ.

**Bài học phỏng vấn:** nên hỏi thẳng **"trần ngân sách tối đa"** và dùng câu hỏi kết buổi **"còn điều gì tôi chưa hỏi?"** ngay từ Buổi 1. Câu này ở Buổi 4 đã làm lộ ra rủi ro dữ liệu gốc bẩn và rủi ro nhân viên không chịu dùng thiết bị.
