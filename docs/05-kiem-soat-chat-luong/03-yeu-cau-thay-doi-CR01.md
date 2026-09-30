# Yêu cầu thay đổi CR-01: Khách gọi món qua QR, xem trạng thái món realtime, tự thanh toán chuyển khoản

| Mục | Nội dung |
|---|---|
| Mã | CR-01 |
| Người yêu cầu | Chị Mai Anh (chủ), sau khi baseline đã chốt (Buổi 6) |
| Ngày | 30/09/2026 |
| Trạng thái | ✅ **Được duyệt đưa vào thiết kế, có sửa** (Buổi 10). ⏳ **Chưa** duyệt mua dịch vụ hay xây phần mở rộng: còn chờ kiểm tra kỹ thuật 2 tuần và **báo giá tổng mới** |
| Khảo sát | [Khảo sát của Codex](../01-thu-thap-yeu-cau/04-codex-khao-sat-qr-thanh-toan.en.md) · Buổi 9 (hội thảo tác động) · Buổi 10 (xác nhận) trong [biên bản](../01-thu-thap-yeu-cau/02-bien-ban-phong-van.en.md) |
| Ràng buộc mới của nhóm dự án | **Spring Boot + React + PostgreSQL + CI/CD đầy đủ** (DEC-26) |

## 1. Nội dung thay đổi

Trước CR-01, baseline xếp **"khách tự gọi món bằng QR" là Won't** (S4-O10), còn **"QR theo bill tự xác nhận" là Should** (S5-D6).

CR-01 bổ sung ba năng lực:
1. **Khách gọi món bằng QR tại bàn.**
2. **Khách xem trạng thái món realtime** trên điện thoại.
3. **Khách tự thanh toán bằng chuyển khoản, được xác nhận tự động.**

## 2. Kết quả khảo sát thực tế (tóm tắt)

| Chủ đề | Phát hiện | Tác động thiết kế |
|---|---|---|
| Luồng đơn QR | iPOS, KiotViet, Sapo, MISA đều cho **nhân viên xác nhận** hoặc **tự nhận** tuỳ cấu hình. MISA cho thu ngân xác nhận để chống đơn từ người ngoài quán | Có hàng chờ xác nhận (FR-GST-06) |
| Bảo vệ QR | QR in cố định **có thể bị chụp lại và dùng từ xa**. KiotViet có kiểm tra vị trí điện thoại (tuỳ chọn) | Bàn phải đang mở, có mã ngồi bàn, nhân viên xác nhận (BR-41, BR-57, BR-42) |
| Trạng thái cho khách | KiotViet chỉ hiện *chưa phục vụ / đã phục vụ / đã huỷ*, do nhân viên đánh dấu. **Trạng thái chỉ đúng khi nhân viên thực sự bấm** | Chỉ hiện trạng thái trung thực (BR-47) |
| Tự thanh toán | VietQR động điền sẵn số tiền và nội dung, nhưng **tạo QR không đồng nghĩa với xác nhận**. TT 64/2024 (hiệu lực 01/03/2025) là khung Open API, **không** tự cho quán quyền nhận thông báo giao dịch. payOS, SePay, Casso có webhook; giá theo gói | Adapter nhà cung cấp (ADR-10); thử thật trước khi bật (BR-59) |
| Cùng một điện thoại | **Không quét được QR hiện trên chính màn hình mình**, cần deeplink tới app ngân hàng hoặc lưu ảnh QR | FR-GST-14; thử ở S12 |
| Pháp lý | HĐĐT vẫn là nghĩa vụ (NĐ 254/2026). Có NĐ 52/2024 về thanh toán không dùng tiền mặt. Luật BVDLCN và NĐ 356/2025 áp dụng cho dữ liệu khách. **Cấm bán bia rượu cho người dưới 18** (Luật 2019) | BR-43, BR-54 |
| Lỗi hay gặp | Thông báo trễ, trả trùng, sai số tiền, khách rời đi khi chưa xác nhận. MoMo khuyến cáo chỉ tin **thông báo từ máy chủ**, không tin trang trả về | BR-49…51 |

## 3. Quyết định của khách (Buổi 9 và 10)

| Chủ đề | Quyết định | Nguồn |
|---|---|---|
| **QR-1: thí điểm** | **6 bàn ở Đống Đa** (trộn trong nhà và sân trong có mái), thử buổi trưa rồi một tối thứ 6 hoặc 7 có giám sát. Nhân viên đón khách, mở bàn, nhận order đầu và giới thiệu QR; **khách dùng QR để gọi thêm món** | S9-O2, S10-Q1 |
| Xác nhận | **Mọi đơn QR chờ nhân viên xác nhận** (phục vụ khu vực; thu ngân dự phòng). Mục tiêu 1 phút là để **đo**, không hứa với khách | S9-L2, S10-Q1 |
| Luôn chờ người | Bia và đồ có cồn, ghi chú dị ứng, số lượng bất thường, set lẩu bị đổi, món "ra sau". **Không bao giờ tự động**, kể cả ở QR-2 | S10-Q4 |
| Mã ngồi bàn | **Bắt buộc ngay từ QR-1**. Đưa cho khách trên thẻ khi ngồi, đổi cho nhóm sau, không in lên phiếu bếp. Cần thử xem có làm tăng việc cho nhân viên không | S10-R2 |
| Trạng thái | "Chờ nhân viên xác nhận", "Bếp đã nhận", "Đã phục vụ". **"Xong" chỉ hiện nếu người ở pass bấm đều** trong buổi thử. **Không** có "đang nấu", **không** hứa giờ | S9-K1, S10-Q1 |
| Tự thanh toán | **Chỉ cả bill đã chốt**. Tách bill vẫn qua thu ngân. **Chỉ bật khi thu ngân và kế toán đã thử xác nhận ngân hàng thật** | S9-C1, S10-Q2 |
| Giờ gọi cuối | Món ăn tới **21:45**. Đồ uống chai tới **~22:15** theo lệnh gọi cuối của quản lý. Đóng cửa 22:30. Ngoại lệ tiệc do quản lý và bếp trưởng quyết | S9-K4, S10-R5 |
| Tạm dừng | Quản lý **tạm dừng QR mà không đóng bill**. Khi mất kết nối: tạm dừng, báo khách, **đơn gửi trong lúc mất kết nối không bao giờ được giao sau** | S10-Q1, R12 |
| Tiệc phòng riêng | Chỉ gọi **thêm** sau khi quản lý xác nhận set đặt trước. Thanh toán tiệc lớn hoặc nhiều công ty **qua thu ngân** | S9-Q1, S10-Q3 |
| QR-2 | Tự nhận món thêm thông thường, thêm bàn, tự gọi từ món đầu, tách tự thanh toán: **cần chủ quyết riêng sau khi hệ thống lõi ổn định** | S10-Q3 |
| Mục tiêu | **G6:** ~1/4 món gọi thêm ở 6 bàn thí điểm đến từ QR sau 1 tháng (mục tiêu học hỏi). **G7:** từ "tính tiền" tới xác nhận ~3 phút (đo baseline trước) | S9-O1, S10-G6, G7 |
| Ngân sách | **+50–80 triệu một lần**, tổng dự án **không quá 600 triệu**. **+1–2 triệu/tháng** cố định, tổng ~12 triệu. Phí giao dịch báo riêng. Nếu thiếu tiền thì **hoãn QR-2 trước tiên**, rồi tới danh sách chờ, tích điểm, kết nối app giao hàng | S9-O4, S10-C1 |
| Tuyến ngân hàng | Hỏi **Vietcombank trước** (có văn bản); sau đó so với payOS, SePay, Casso: phạm vi tài khoản, độ trễ, quyền truy cập dữ liệu, thu hồi quyền, xác thực thông báo, chi phí tháng. **Không ai đưa mật khẩu ngân hàng** | S9-F1, S10-C2 |

## 4. Phân tích tác động theo tài liệu

| Tài liệu | Thay đổi |
|---|---|
| [BRD 1 – Tổng quan](../02-phan-tich-nghiep-vu/01-tong-quan-du-an.md) | Thêm G6, G7. Thêm phạm vi QR-1 và QR-2 (bỏ "QR tại bàn" khỏi Won't). CON-01 ghi trần 600 triệu; thêm CON-11 (công nghệ). Thực khách thành người dùng. Thêm RSK-11…16. Lộ trình có mốc QR-1 |
| [BRD 2 – Quy trình](../02-phan-tich-nghiep-vu/02-quy-trinh-as-is-to-be.md) | Thêm **P12** (khách gọi thêm món qua QR) và **P13** (khách tự thanh toán) |
| [BRD 3 – Quy tắc](../02-phan-tich-nghiep-vu/03-quy-tac-nghiep-vu.md) | Thêm **BR-41 … BR-60** |
| [Từ điển](../02-phan-tich-nghiep-vu/04-thuat-ngu.md) | Thêm thuật ngữ kênh QR và thanh toán tự động |
| [SRS – Chức năng](../03-dac-ta-yeu-cau/01-yeu-cau-chuc-nang.md) | Module mới **GST** (FR-GST-01 … 24); thêm dòng vào ma trận phân quyền |
| [SRS – Phi chức năng](../03-dac-ta-yeu-cau/02-yeu-cau-phi-chuc-nang.md) | **NFR-35 … 46** (trang khách, realtime, bảo mật kênh khách, chống lừa đảo, công nghệ, CI/CD); **TR-11 … 16** |
| [SRS – Use case](../03-dac-ta-yeu-cau/03-use-case.md) | Tác nhân **A11 Khách**; **UC-33 … 40**; gói sơ đồ 2.4; đặc tả UC-34, 35, 38 |
| [SRS – AC](../03-dac-ta-yeu-cau/04-tieu-chi-chap-nhan.md) | **AC-43 … 60** |
| [Kiến trúc](../04-thiet-ke-he-thong/01-kien-truc.md) | Kênh khách qua cloud (edge vẫn là nơi ghi duy nhất); **Spring Boot + React + PostgreSQL**; ADR-09 … 12; S11, S12 trong kiểm tra kỹ thuật |
| [Mô hình lớp](../04-thiet-ke-he-thong/02-mo-hinh-lop.md), [CSDL](../04-thiet-ke-he-thong/03-co-so-du-lieu.md) | TableQrCode, GuestSession, GuestOrderRequest, ServiceRequest, PaymentIntent, ItemStatusHistory; INV-13 … 17 |
| [Tương tác và trạng thái](../04-thiet-ke-he-thong/04-tuong-tac-trang-thai.md) | SD-08 … 10, ST-09 … 10 |
| [Giao diện](../04-thiet-ke-he-thong/05-giao-dien.md) | App khách G-01 … G-08; màn hình nhân viên PV-09, TN-07, QL-06 |
| **Mới:** [Tham khảo repo](../04-thiet-ke-he-thong/06-tham-khao-repo.md), [Mã nguồn và CI/CD](../04-thiet-ke-he-thong/07-ma-nguon-va-cicd.md) | Chuyển pattern từ repo tham khảo về stack của dự án; pipeline CI/CD |

## 5. Rủi ro mới

| ID | Rủi ro | Giảm thiểu |
|---|---|---|
| RSK-11 | **QR giả dán đè** dẫn khách tới trang thanh toán lừa đảo | Thẻ chống bóc dán, kiểm tra mỗi lần mở bàn; chỉ thanh toán trên tên miền của quán, hiện tên tài khoản công ty (BR-53) |
| RSK-12 | Ảnh món hấp dẫn khiến khách gọi nhanh hơn sức bếp | Nhân viên xác nhận và điều nhịp (BR-42, BR-43) |
| RSK-13 | Xác nhận chậm khiến khách trả lần 2 | Trang khách **không** gợi ý trả lại; thu ngân kiểm tra tài khoản trước (BR-51) |
| RSK-14 | Thí điểm tháng 2 gánh quá nhiều thay đổi | Chỉ 6 bàn; công tắc tắt QR tức thì (BR-56); QR-2 hoãn |
| RSK-15 | Nhân viên bỏ bê bàn dùng QR | Hàng chờ xác nhận và gọi nhân viên có đồng hồ; quản lý theo dõi (FR-GST-06, 11) |
| RSK-16 | Mở QR bằng Zalo hoặc trình duyệt trong app thì deeplink không chạy | Thử ở S12; luôn có "lưu ảnh QR" và "nhờ nhân viên" (FR-GST-14) |

## 6. Việc tiếp theo

1. Thêm vào kiểm tra kỹ thuật 2 tuần:
   - **S11** (thử khả dụng trang khách, gồm trình duyệt trong Zalo);
   - **S12** (thử tuyến ngân hàng thật).
2. Kế toán xin **văn bản của Vietcombank** (OI-24).
3. Trình **báo giá tổng mới** (≤ 600 triệu) để chủ quyết mua và xây.
