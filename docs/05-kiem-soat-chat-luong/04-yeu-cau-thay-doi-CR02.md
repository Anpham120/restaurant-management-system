# Yêu cầu thay đổi CR-02: Chuyển bối cảnh sang Hà Nội và nhân viên quét cùng QR bàn với khách

| Mục | Nội dung |
|---|---|
| Mã | CR-02 |
| Người yêu cầu | Nhóm dự án (30/09/2026): (1) dự án đặt tại **Hà Nội**; (2) **hai cách gọi món** (nhân viên gọi hộ và khách tự gọi) **đều đi qua QR trên bàn**, nhân viên quét bằng **app nhân viên** |
| Trạng thái | ✅ **Được duyệt đưa vào thiết kế, có sửa V7 và V10** (Buổi 12). ⏳ Vẫn chờ kiểm tra kỹ thuật có chi phí và buổi thử lúc đông khách trước khi duyệt xây dựng |
| Khảo sát | [Khảo sát Hà Nội của Codex](../01-thu-thap-yeu-cau/05-codex-khao-sat-ha-noi.en.md) · Buổi 11 (hội thảo) · Buổi 12 (xác nhận) trong [biên bản](../01-thu-thap-yeu-cau/02-bien-ban-phong-van.en.md) |

## 1. Ánh xạ quán (TP.HCM → Hà Nội)

Buổi 1–10 được ghi với tên địa bàn TP.HCM. **Từ CR-02, mọi tài liệu tiếng Việt dùng tên Hà Nội.** Biên bản gốc tiếng Anh giữ nguyên làm hồ sơ lịch sử.

| Trước (Buổi 1–10) | Sau CR-02 | Mã | Phường hiện hành (sau 01/07/2025); tên quận cũ quen gọi | Quản lý | Đặc điểm giữ nguyên | Đặc điểm chỉnh theo Hà Nội |
|---|---|---|---|---|---|---|
| Bình Thạnh (BT) | **Khói Bếp Đống Đa** | **DDA** | Phường Đống Đa; quận Đống Đa cũ | Lan | Quán cũ nhất, đông nhất; 24 bàn (~96 chỗ); **bếp sơ chế chung** ở phòng sau; **quán thí điểm** | 6 bàn ngoài trời nằm ở **sân trong có mái, trong khuôn viên quán**, không ở vỉa hè |
| Phú Nhuận (PN) | **Khói Bếp Cầu Giấy** | **CGY** | Phường Cầu Giấy (khu Dịch Vọng cũ); quận Cầu Giấy cũ | Thanh | 20 bàn (~80 chỗ); khu quây 4 bàn; **mạng không ổn định**; tỷ trọng giao hàng lớn hơn | 4 bàn phía trước nằm ở **khoảng lùi trong khuôn viên**, dời vào trong khi nóng hoặc mưa |
| Quận 3 (Q3) | **Khói Bếp Hai Bà Trưng** | **HBT** | Phường Hai Bà Trưng; quận Hai Bà Trưng cũ | Quyên | 22 bàn (~88 chỗ); **phòng riêng 3 bàn**; 4 bàn ngoài trời | 4 bàn trên **sân thượng**; mưa và lạnh thì chuyển cả nhóm vào trong |

**Giữ nguyên:**
- mô hình kinh doanh, số bàn, nhân sự, giờ mở cửa, số khách, chi tiêu bình quân;
- công cụ hiện tại, app giao hàng, hình thức công ty, tài khoản thu Vietcombank;
- ngân sách, lịch thí điểm ~22/02/2027, **mọi quyết định đã xác nhận** ở baseline và CR-01.

## 2. Khảo sát thực tế Hà Nội (tóm tắt)

| Chủ đề | Phát hiện | Tác động |
|---|---|---|
| Mùa vụ | Mùa lạnh hút khách ăn lẩu và nướng; **lẩu chiếm từ 1/3 đơn tối lạnh trở lên** (ước lượng của bếp trưởng). Tất niên tháng 1 rất đông. Mùa hè lẩu giảm; sân trong kém hấp dẫn khi nắng nóng hoặc mưa to | Thí điểm ~22/02 **vẫn trong mùa lạnh**. Buổi thử lúc đông phải có nhóm ăn lẩu (RSK-17) |
| Vỉa hè | **Không được coi bàn trên vỉa hè là hợp pháp** chỉ vì quán khác làm. Hà Nội đang xử lý lấn chiếm. Phí vỉa hè (NQ 10/2026/NQ-HĐND, hiệu lực 21/05/2026) chủ yếu áp cho **trông giữ xe**, không phải giấy phép đặt bàn | Mọi chỗ ngồi đều **trong khuôn viên** (CON-13) |
| Gửi xe | Chỗ trong khuôn viên ít; xe thừa gửi ở **bãi gần được cấp phép**; người đón khách đóng dấu **vé gửi xe**; bãi tính tiền quán theo tháng | **Ngoài hệ thống**. Kế toán xử lý như chi phí; **không** thêm bước thanh toán (DEC-30) |
| Đơn vị hành chính | Từ **01/07/2025**, chính quyền 2 cấp, bỏ cấp quận. Địa chỉ chính thức dùng **phường**; tên quận cũ vẫn dùng khi chỉ đường | Địa chỉ trên HĐĐT và hồ sơ nhà cung cấp dùng **phường hiện hành** (FR-ADM-01) |
| Thanh toán | VietQR phổ biến toàn quốc (NAPAS: ~90 triệu tài khoản quét VietQR tính tới 10/2025). Tỷ lệ thanh toán của chuỗi **giữ theo ước lượng** cho tới khi có dữ liệu | Không đổi |
| Rượu bia | NĐ 168/2024 tăng mức phạt nồng độ cồn. Khách có thể uống ít hơn hoặc gọi xe. Tác động lên chuỗi **chưa kiểm chứng** | Giữ quy tắc kiểm tra tuổi (BR-43) |
| Nhân viên quét cùng QR | **Không tìm được** nhà cung cấp nào cho nhân viên quét **cùng thẻ QR của khách** để mở bàn (iPOS có quét QR đăng nhập, là mã khác) | **Tính năng phải tự xây**, cũng là điểm khác biệt của hệ thống |
| Nhân sự | Hà Nội tuyển sinh viên làm part-time; lương tối thiểu vùng I (NĐ 293/2025) | Giữ đào tạo theo vai trò, PIN cá nhân (TR-05) |

## 3. Quyết định của khách (Buổi 11 và 12)

| Chủ đề | Quyết định | Nguồn | Quy tắc |
|---|---|---|---|
| Thẻ QR | **Mọi bàn** có thẻ chống bóc dán ghi **tên bàn và khu đúng như app** (ví dụ "Sân trong C3"); có thẻ dự phòng. Đống Đa gắn **đủ 24 bàn trước khi tập quét**; Cầu Giấy và Hai Bà Trưng gắn khi go-live. Phòng riêng: **mỗi bàn một thẻ**. Thẻ ở bàn không thí điểm **không quảng cáo** việc khách tự gọi món | S11-L6, Q1, S12-V3, V9 | BR-61 |
| Hai chế độ | Quét bằng **app nhân viên** (đã đăng nhập, thiết bị của quán) là **chế độ nhân viên**. Quét bằng **camera hoặc Zalo** là **chế độ khách** | CR-02 | BR-62 |
| Quét là cách mở bàn thông thường | Nhưng **không bắt buộc**. **Sơ đồ bàn và chọn tay** luôn còn; chọn tay được ghi là *chọn tay* | S11-L1, L5, S12-V4 | BR-63 |
| Màn hình khi quét | Tên bàn, khu, trạng thái **chữ lớn**. **Trống**: mở bàn (bàn thí điểm thì hiện mã ngồi bàn). **Có khách**: hiện nhóm, bill, đơn QR chờ, **không mở nhóm thứ hai**. **Đã đặt**: hiện giờ đặt, **chỉ quản lý** quyết cho khách vãng lai ngồi. **Đang dọn**: chờ | S11-L2, S12-V5 | BR-64 |
| Xác nhận tại bàn | Quét xong thì xác nhận đơn QR đang chờ ngay tại bàn. **Không** có nút tắt bỏ qua việc kiểm tra tuổi hoặc trao đổi về dị ứng | S11-L3, S12-V6 | BR-43, 64 |
| Chuyển nhóm | Quét bàn cũ rồi bàn mới; **xem mọi bàn bị ảnh hưởng** trước khi xác nhận. Bàn đích phải **trống hoặc đã thuộc chính nhóm đó**; **không bao giờ gộp nhầm** nhóm khác. Bàn đã đặt thì quản lý xử lý booking trước. Thẻ cũ ngừng nhận đơn của nhóm | S11-L4, S12-V7 | BR-65 |
| Thu ngân quét | Mở **bill hiện tại của nhóm** (gồm bàn ghép, bàn chuyển, cọc, giảm giá). Thu ngân **đọc lại số bàn và số tiền**. Quét **không bao giờ** đánh dấu đã trả | S11-C1, S12-V8 | BR-66 |
| Bàn không thí điểm | Khách quét thì chỉ **xem thực đơn** kèm lời nhắn nhờ nhân viên gọi món. **Gọi nhân viên** chỉ có ở 6 bàn thí điểm cho tới khi quản lý chắc các yêu cầu được đáp ứng | S12-V9 | BR-67 |
| Mất liên lạc với máy chủ tại quán | Máy đó **không mở được bàn mới** và **không bao giờ tự gửi món** khi có kết nối lại. Món chỉ nằm ở dạng **bản nháp CHƯA GỬI**; nhân viên chuyển sang phiếu giấy đánh số rồi đối chiếu. **Buổi thử kỹ thuật phải chứng minh** không có phiếu bếp "bất ngờ" sau khi khôi phục | S11-Z1, S12-V10 | BR-68 |
| Nguồn đơn ở bếp | Phiếu và màn hình bếp ghi **ai gửi** (tên phục vụ) và **ai xác nhận** đơn QR. Đơn chưa xác nhận **không bao giờ in ở bếp** | S11-Z1, S12-V11 | BR-69 |
| Mùa đông | Giữ thí điểm ~22/02 và 6 bàn thử khách. Buổi thử lúc đông **có nhóm ăn lẩu**. Set lẩu chỉ báo **"Xong" khi đã ráp đủ**. Điện thoại khách **không gọi ra món "ra sau"**. Giờ gọi món ăn cuối vẫn 21:45 | S11-O1, K1, S12-V12 | BR-70 |

## 4. Phân tích tác động theo tài liệu

| Tài liệu | Thay đổi |
|---|---|
| Mọi tài liệu tiếng Việt | Đổi tên quán, mã (DDA, CGY, HBT), "ngoài hiên" thành "sân trong", TP.HCM thành Hà Nội (bằng script có kiểm tra ngữ cảnh) |
| BRD 1 | Bối cảnh Hà Nội; CON-13; RSK-17, 18, 19; phạm vi thêm "nhân viên quét QR" |
| BRD 2 | Thêm **P14** (quét QR bàn hai chế độ) |
| BRD 3 | Thêm **BR-61 … BR-70** |
| SRS | FR mới: **FR-TBL-07, 08, 09**, **FR-GST-25, 26**, **FR-BIL-18**, **FR-KIT-10**. Sửa **FR-GST-01** (mọi bàn, M/M), **FR-OFF-06** (bản nháp, không tự gửi), **FR-ADM-01** (địa chỉ theo phường). Thêm **NFR-47, 48**, **TR-17 … 19**, **UC-41**, **AC-64 … 74**. Sửa **AC-02** |
| Thiết kế | Kiến trúc (edge giải mã QR khi mất Internet; HTTPS trong LAN cho camera; S13; ADR-13, 14); lớp và CSDL (tên hiển thị bàn, khu, cách mở bàn, **mỗi bàn tối đa một nhóm đang ngồi**); **SD-11**; sửa SD-01; màn hình **PV-10** |

## 5. Rủi ro mới

| ID | Rủi ro | Giảm thiểu |
|---|---|---|
| RSK-17 | Thí điểm ~22/02 rơi vào **mùa lạnh cao điểm lẩu**; bếp chịu tải cao đúng lúc hệ thống còn mới | Buổi thử có giám sát và có nhóm ăn lẩu; điều nhịp; 6 bàn thử khách |
| RSK-18 | **Quét nhầm bàn**, hoặc **thẻ bị tráo** khi dọn | Tên bàn và khu chữ lớn, kiểm tra thẻ mỗi lần mở bàn, xem trạng thái trước khi xác nhận (BR-61, 64) |
| RSK-19 | Camera **không đọc được thẻ** (tối, ướt, bị dĩa che) | Thử ban đêm ở bàn phía trước, sân trong, sân thượng (TR-17); luôn có chọn tay (BR-63) |
