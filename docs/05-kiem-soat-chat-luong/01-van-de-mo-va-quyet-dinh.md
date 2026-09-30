# Sổ quyết định và vấn đề mở

> Phương pháp: skill `assumptions-constraints-log`, `requirements-packager` (bước 6: *flag unresolved contradictions and decisions pending*).

## 1. Quyết định đã chốt với khách

| Mã | Quyết định | Ai quyết | Nguồn | Ảnh hưởng |
|---|---|---|---|---|
| DEC-01 | Chọn **PA2 (hệ thống riêng) có điều kiện**: kiểm tra kỹ thuật và giá 2 tuần, **thử phần mềm có sẵn cho phần sảnh**; chỉ duyệt xây khi có kế hoạch có chi phí | Chủ | S5-B, D5 | TR-03, [kiến trúc §9](../04-thiet-ke-he-thong/01-kien-truc.md) |
| DEC-02 | Hạn mức giảm giá của quản lý: **≤ 10% bill, trần 150.000đ/bill**; xoá món tính nhầm không tính hạn mức | Chủ | S5-BR1 | BR-01, BR-04 |
| DEC-03 | Trần theo ca **600.000đ/quản lý**; xem lại sau thí điểm | Chủ | S5-D1, S6-R1 | BR-02 |
| DEC-04 | Dự phòng: **3 phút**, ≤ 300.000đ, người xác nhận thứ hai là thu ngân hoặc bếp trưởng, cảnh báo ngay, kế toán rà mọi trường hợp | Chủ, kế toán đồng ý cho thí điểm | S5-D2, S6-R3 | BR-03 |
| DEC-05 | Cọc: ≥ 24 giờ hoàn 100%; < 24 giờ hoặc không tới giữ 50% (trừ khi dời trong 7 ngày); không có bằng chứng điều khoản thì hoàn 100% | Chủ (kế toán duyệt câu chữ) | S5-D3, S6-R4 | BR-11 |
| DEC-06 | Chi tiền mặt từ két: ≤ 300.000đ; vượt mức gọi chủ trước; ngoại lệ khẩn cấp phải ghi lý do | Chủ | S5-D4, S6-R5 | BR-24 |
| DEC-07 | QR theo bill là **Should** cho thí điểm (tuỳ ngân hàng cho truy cập) | Chủ, kế toán | S5-D6, C6 | FR-BIL-09 |
| DEC-08 | Báo giá **UPS và 4G dự phòng** cho từng quán; có thể mua cho thí điểm trước | Chủ | S5-D7 | NFR-09, NFR-10 |
| DEC-09 | Khảo sát bếp cùng bếp trưởng; **chưa chốt** số máy in và màn hình | Chủ, bếp trưởng | S5-D8 | OI-10 |
| DEC-10 | Dữ liệu thí điểm xong **15/12/2026**; dữ liệu Cầu Giấy và Hai Bà Trưng xong **31/01/2027** | Chủ | S5-D9 | TR-02 |
| DEC-11 | Đo baseline từ **tháng 10**: Lan (lỗi món), Minh và Thảo (kiểm kê, luân chuyển), Hạnh (bấm giờ khớp số) | Chủ | S5-D10 | TR-01 |
| DEC-12 | **p, u, v, z vẫn là Must cho tháng 3** (phạm vi thu hẹp như đã mô tả) | Chủ | S6-R8 | FR-INV-03/04, FR-RPT-05, FR-MNU-06–09, FR-INT-04 |
| DEC-13 | Ghi **nhiệt độ** khi nhận thịt lạnh và hải sản | Bếp trưởng | S6-R10 | BR-33 (ngưỡng: OI-01) |
| DEC-14 | Go-live thí điểm **~22/02/2027**; tháng 1 chỉ tập dượt; không ép go-live Cầu Giấy và Hai Bà Trưng nếu thí điểm lỗi nghiêm trọng | Chủ | S7-Q1 | CON-03, TR-08 |
| DEC-15 | Làm tròn **chỉ tiền mặt**, xuống 1.000đ, ghi dòng riêng trước khi xuất HĐ | Chủ, kế toán | S7-Q3 | BR-21 |
| DEC-16 | Phỏng vấn quản lý Cầu Giấy và Hai Bà Trưng (**đã thực hiện** ở Buổi 8) | Chủ | S7-Q2 | FR-TBL-04/05, FR-RSV-07, FR-OFF-04 |
| DEC-17 | Lương và công nợ nhà cung cấp **ngoài phạm vi**; chỉ xuất dữ liệu | Kế toán, chủ | S4-F7, F9, S5-C5 | CON-10 |
| DEC-18 | Thí điểm **tiếp tục xuất HĐĐT trên MISA meInvoice**; hệ thống cấp dữ liệu tin cậy | Kế toán | S5-C3, A1 | FR-BIL-15, FR-INT-02 |
| DEC-19 | **Giữ phiếu bếp in** trong thí điểm; màn hình chỉ hỗ trợ | Bếp trưởng | S5-C1 | CON-05 |
| DEC-20 | **CR-01 được duyệt đưa vào thiết kế, có sửa.** **Chưa** duyệt mua dịch vụ hay xây phần mở rộng | Chủ | S10 | [CR-01](03-yeu-cau-thay-doi-CR01.md) |
| DEC-21 | **QR-1**: 6 bàn Đống Đa; nhân viên mở bàn và nhận order đầu; khách gọi **thêm** qua QR; **nhân viên xác nhận mọi đơn**; **mã ngồi bàn bắt buộc** | Chủ, Lan | S9-O2, S10-Q1, R2 | BR-41, 42, 57 |
| DEC-22 | Khách chỉ thấy **trạng thái trung thực**; "Xong" chỉ hiện nếu pass bấm đều | Đức | S9-K1, S10-Q1 | BR-47 |
| DEC-23 | **Tự thanh toán cả bill đã chốt**, chỉ bật sau khi thử tuyến ngân hàng thật; tách tự thanh toán thuộc QR-2 | Huy, Hạnh, chủ | S9-C1, S10-Q2 | BR-48, 59 |
| DEC-24 | Giờ gọi cuối qua QR: **món ăn 21:45**, **đồ uống chai ~22:15** (lệnh gọi cuối của quản lý) | Đức, Lan | S9-K4, S10-R5 | BR-46 |
| DEC-25 | Ngân sách: **trần dự án 600 triệu**; CR-01 thêm **50–80 triệu một lần** và **1–2 triệu/tháng**; phí giao dịch báo riêng; **hoãn QR-2 trước tiên** nếu thiếu | Chủ | S9-O4, S10-C1 | CON-01, CON-12 |
| DEC-26 | **Công nghệ bắt buộc**: backend Java Spring Boot, frontend React, CSDL PostgreSQL, **CI/CD đầy đủ** | Nhóm dự án | Yêu cầu 30/09/2026 | CON-11, ADR-11, 12, NFR-43…46 |

## 2. Vấn đề mở

| Mã | Vấn đề | Người trả lời | Hạn | Yêu cầu bị ảnh hưởng | Mức độ |
|---|---|---|---|---|---|
| OI-01 | **Ngưỡng nhiệt độ** chấp nhận khi nhận thịt và hải sản; xử lý hàng đáng ngờ | Minh, Đức | Trước go-live Đống Đa | BR-33, FR-PUR-04 | Trung bình |
| OI-02 | Tiệc hoặc phòng riêng **không đạt mức chi tối thiểu** thì xử lý thế nào (thu phần còn thiếu? bỏ qua? ghi nhận?) | Quyên, chủ | Trước go-live Hai Bà Trưng | BR-13, FR-BIL-16 | Trung bình |
| OI-03 | Cách tính **phần vụn tái sử dụng** (nước lẩu, bữa nhân viên) trong tỷ lệ thành phẩm | Đức, Hạnh | Trước đợt 2 (01/2027) | BR-35, FR-PRP-02 | Trung bình |
| OI-04 | Phương pháp **phân bổ lương và chi phí sơ chế** theo tháng | Hạnh | 03/2027 | FR-PRP-07 | Thấp |
| OI-05 | **Câu chữ điều khoản cọc** và cách hạch toán phần giữ lại | Hạnh | Trước khi áp dụng BR-11 | FR-RSV-02, FR-RSV-05 | **Cao** (chặn tính năng cọc) |
| OI-06 | **Thuế suất từng loại món**, đặc biệt **từ 01/01/2027** khi mức 8% hết hạn; phân loại bia | Hạnh, tư vấn thuế | 15/12/2026 | NFR-23, FR-MNU-09 | **Cao** |
| OI-07 | **Thời hạn lưu trữ** chính xác theo luật | Tư vấn | Trước M1 | BR-31, NFR-20 | Thấp |
| OI-08 | Nhận **thông báo giao dịch** của tài khoản thu Vietcombank (API ngân hàng hay dịch vụ trung gian)? Chi phí, độ trễ | Tech lead | S1 (16/10/2026) | FR-INT-01, FR-BIL-09, G2 | **Cao** |
| OI-09 | **Kết nối app giao hàng**: quyền truy cập, chi phí | Chủ, tech lead | Trước 03/2027 | FR-DLV-05, FR-DLV-06 | Thấp |
| OI-10 | Số lượng và vị trí **máy in khu, màn hình bếp**; in tiếng Việt có dấu | Đức, tech lead | S3, S5 | FR-KIT-02, FR-KIT-03 | Trung bình |
| OI-11 | **Wi-Fi** phủ bàn mặt tiền Cầu Giấy | Tech lead, Thanh | S6 | FR-OFF-06 | Trung bình |
| OI-12 | **Hosting** tại Việt Nam so với phương án khác: báo giá | Tech lead | S9 | NFR-32 | Thấp |
| OI-13 | Kết quả **thử phần mềm có sẵn cho phần sảnh**: xây hay tích hợp | Chủ | S8, M1 | Toàn bộ Outlet Ops | **Cao** |
| OI-14 | **Tỷ lệ kênh bán và phương thức thanh toán thực tế** (để định cỡ và ước tính chi phí biến đổi) | Hạnh | TR-01 | NFR-26, NFR-31 | Trung bình |
| OI-15 | Xác nhận **BR-38**: nhập bù sau sự cố mặc định "không gửi bếp" | Lan, Thanh, Đức | Tối thử | FR-OFF-04 | Trung bình |
| OI-16 | **Ngày go-live** Cầu Giấy và Hai Bà Trưng | Chủ | Sau 2 tuần thí điểm | TR-08 | Trung bình |
| OI-17 | Quy trình **chỉ dẫn xuất HĐ cho tiệc nhiều bên**: ai quyết, trước bao lâu | Hạnh, Quyên | Trước go-live Hai Bà Trưng | BR-26, FR-RSV-07 | Trung bình |
| OI-18 | Ngưỡng **"món chờ quá lâu"** theo nhóm món hoặc khu; số phút nhắc bếp xác nhận thay đổi | Đức, Lan | Tối thử | FR-KIT-08, FR-KIT-05 | Thấp |
| OI-19 | **Phí thẻ**, lịch đối soát thẻ và app theo hợp đồng | Hạnh | TR-01 | FR-RPT-05 | Thấp |
| OI-20 | **Danh mục chất gây dị ứng** chuẩn và cách hiển thị | Đức | Trước go-live Đống Đa | FR-ORD-03 | Trung bình |
| OI-21 | Có cho **chốt ca khi còn bill "khách nợ"** không? | Hạnh | Trước go-live Đống Đa | FR-BIL-17, UC-13 | Thấp |
| OI-22 | ~~**Giờ đóng bếp 22:00** và ngoại lệ cho tiệc lớn~~. ✅ **Đã giải quyết ở Buổi 9–10**: món ăn dừng 21:45, đồ uống chai ~22:15, ngoại lệ do quản lý và bếp trưởng quyết (BR-46). Còn lại: có áp giờ gọi cuối cho **máy của phục vụ** không, hay chỉ cho QR? | Đức, Lan | Trước go-live Đống Đa | FR-ORD-01, FR-GST-04 | Thấp |
| OI-23 | **Ngưỡng chênh lệch kho được chấp nhận** theo mặt hàng; vượt ngưỡng mới yêu cầu giải trình *(phát hiện khi đối chiếu persona)* | Chủ, Minh, Hạnh | Trước đợt 2 (01/2027) | FR-INV-06, G4 | Trung bình |
| OI-24 | *(CR-01)* **Tuyến xác nhận tự thanh toán**: Vietcombank có dịch vụ cho tài khoản thu không (có văn bản)? Nếu không, chọn payOS, SePay hay Casso: phạm vi tài khoản, độ trễ, dữ liệu, thu hồi quyền, chữ ký, chi phí tháng | Hạnh, tech lead | S12 (16/10/2026) | FR-GST-14, 15, BR-60 | **Cao** |
| OI-25 | *(CR-01)* **Cách đưa mã ngồi bàn** cho khách (thẻ viết tay, phiếu in ở quầy đón, thẻ xoay vòng) và khối lượng việc khi đón, dọn bàn | Lan | Tối thử QR-1 (TR-13) | FR-GST-02, BR-57 | Trung bình |
| OI-26 | *(CR-01)* **Ngưỡng "số lượng bất thường"** của đơn QR, theo nhóm món | Lan, Đức | Trước QR-1 | FR-GST-07, BR-43 | Thấp |
| OI-27 | *(CR-01)* Có **bật trạng thái "Xong"** cho khách không (tuỳ độ đều tay của người ở pass trong buổi thử) | Đức | Sau buổi thử QR-1 | FR-GST-10, BR-47 | Thấp |
| OI-28 | *(CR-01)* **Deeplink** chạy được với những app ngân hàng nào, kể cả khi mở từ Zalo; tỷ lệ khách trả thành công trên cùng điện thoại | Tech lead | S11, S12 | FR-GST-14, RSK-16 | Trung bình |
| OI-29 | *(CR-01)* **Tên miền trang khách** (ví dụ `order.bnn.vn`), cách hiển thị tên tài khoản công ty, mẫu **thẻ QR chống bóc dán** | Chủ, tech lead | Trước QR-1 | BR-53, NFR-39 | Trung bình |
| OI-30 | *(CR-01)* **Tiêu chí mở QR-2**: tự nhận món thêm thông thường, tự gọi từ món đầu, tách tự thanh toán, thêm bàn | Chủ | Sau khi lõi ổn định (sau tháng 4) | FR-GST-22…24 | Thấp |

## 3. Việc cần làm tiếp theo (theo thứ tự)

1. **05–16/10/2026:** kiểm tra kỹ thuật S1–S12, gồm:
   - thử phần mềm có sẵn cho phần sảnh;
   - *(CR-01)* thử trang khách (S11) và tuyến ngân hàng thật (S12).

   Trả lời OI-08, 10, 11, 12, 13, 24, 28.
2. **~20/10/2026:** trình **kế hoạch có chi phí** để chủ duyệt xây dựng (M1).
3. **Song song từ tháng 10:** đo baseline (TR-01). Kế toán trả lời OI-06 (thuế suất 2027) trước 15/12.
4. **Trước go-live Đống Đa:**
   - chốt OI-01, 05, 15, 18, 20, 21;
   - viết văn bản chính sách (TR-09);
   - tối thử có diễn tập sự cố (TR-06).
