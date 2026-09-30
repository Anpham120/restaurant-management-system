# SRS — Phần 4: Tiêu chí chấp nhận (Acceptance Criteria)

> Phương pháp: skill `acceptance-criteria-writer`, template `acceptance-criteria-template.md`.
> Dạng **Cho trước / Khi / Thì** (Given / When / Then). Ưu tiên các yêu cầu **Must của thí điểm** và các quy tắc có tham số.
> Số liệu trong ví dụ là **dữ liệu kiểm thử**, không phải số liệu thật của quán.

## 1. Gọi món và bếp

**AC-01 · FR-ORD-01, FR-ORD-02, FR-KIT-01, FR-KIT-02, NFR-02: gửi món thêm**
- Cho trước bàn 12 đang phục vụ và đã có order đầu.
- Khi phục vụ thêm 1 "Heo nướng – lớn" và bấm Gửi.
- Thì phiếu in ở **khu Nướng** trong ≤ 3 giây, ghi rõ **BÀN 12 · MÓN THÊM · size lớn**; bill bàn 12 tăng đúng giá của size lớn; nhật ký ghi tên phục vụ và thời điểm.

**AC-02 · FR-ORD-08, FR-OFF-06, BR-68: bản nháp khi mất liên lạc** *(sửa theo CR-02)*
- Cho trước máy cầm tay mất liên lạc với máy chủ tại quán.
- Khi phục vụ bấm gửi 2 món cho bàn 5.
- Thì màn hình hiện **BẢN NHÁP CHƯA GỬI** cho 2 món; không có phiếu bếp nào được in.
- Khi Wi-Fi có lại, **không có gì được gửi tự động**. Phục vụ phải chọn:
  - **Gửi**: bếp nhận **đúng 1 phiếu**, bill tăng **đúng 1 lần**, kể cả khi bấm gửi nhiều lần.
  - **Huỷ bản nháp**: dùng khi món đã được làm bằng phiếu giấy. Không có gì tới bếp.
- Máy đang mất liên lạc **không mở được bàn mới**.

**AC-03 · FR-ORD-03, BR-37: dị ứng**
- Cho trước phục vụ chọn "Đậu phộng" và ghi nguyên văn "khách dị ứng nặng với đậu phộng".
- Khi gửi order.
- Thì phiếu và màn hình bếp hiện dị ứng **ở đầu phiếu, nổi bật**. Bếp trưởng phải bấm **Đã xem** thì trạng thái dị ứng mới thành "đã xem xét". **Không màn hình nào** hiện nhãn "an toàn" hay "không chứa chất gây dị ứng".

**AC-04 · FR-ORD-05, BR-06: sửa trước khi gửi**
- Cho trước món chưa gửi bếp.
- Khi phục vụ xoá món.
- Thì món bị xoá **không cần duyệt** và **không in** gì ở bếp.

**AC-05 · FR-ORD-06, FR-KIT-05, BR-07: huỷ sau khi tới bếp**
- Cho trước phiếu "Lẩu set A" đã tới khu Lẩu và chưa bắt đầu làm.
- Khi phục vụ yêu cầu huỷ với lý do "khách đổi ý trước khi nấu".
- Thì hệ thống **đòi quản lý duyệt**. Sau khi duyệt: in **phiếu HUỶ** ở khu Lẩu; thông báo **ở lại màn hình** cho tới khi bếp bấm Đã thấy; quản lý thấy "Bếp đã xác nhận lúc hh:mm".
- Nếu quá N phút bếp chưa xác nhận thì hệ thống nhắc lại và báo quản lý.

**AC-06 · FR-ORD-07, FR-KIT-06, BR-18: hết món**
- Cho trước bếp trưởng báo hết "Mực nướng sa tế".
- Khi phục vụ mở menu trên bất kỳ máy cầm tay nào trong quán.
- Thì món hiện **HẾT** và **không chọn được** (≤ 3 giây sau khi báo). Hệ thống tạo **2 nhắc việc** "Tắt món trên GrabFood" và "Tắt món trên ShopeeFood". Nhắc việc chỉ đóng khi có người xác nhận từng app.

**AC-07 · FR-TBL-04, FR-TBL-05: chuyển 2 bàn sang 3 bàn**
- Cho trước nhóm 10 khách ở bàn T1, T2 (sân thượng) có bill B-100.
- Khi quản lý chuyển nhóm sang bàn 7, 8, 9 (trong nhà).
- Thì B-100 **vẫn là bill duy nhất** của nhóm. Món gọi sau ở bàn 8 vào B-100. Phiếu bếp và màn hình chạy món hiện **bàn mới**. Lịch sử chuyển được lưu.

## 2. Bill, giảm giá, thanh toán

**AC-08 · FR-BIL-02, BR-21: tách bill chính xác từng đồng**
- Cho trước bill 1.000.000đ.
- Khi thu ngân chia đều cho 3 người.
- Thì 3 bill con là **333.334đ + 333.333đ + 333.333đ**, tổng **đúng 1.000.000đ**. Không bill con nào bị làm tròn tới 1.000đ.

**AC-09 · FR-BIL-08, BR-21: làm tròn tiền mặt**
- Cho trước bill con còn phải trả 452.500đ.
- Khi khách trả **tiền mặt**.
- Thì số phải thu là **452.000đ**, và có **dòng "làm tròn tiền mặt −500đ"** trước khi đóng bill. Nếu khách trả **thẻ** thì thu **đúng 452.500đ**.

**AC-10 · FR-BIL-04, BR-01: hạn mức theo bill**
- Cho trước bill 1.200.000đ (hạn mức = min(10% × 1.200.000; 150.000) = **120.000đ**).
- Khi quản lý giảm 100.000đ với lý do "chờ lâu".
- Thì giảm giá được áp **ngay**.
- Khi quản lý giảm 150.000đ.
- Thì hệ thống **tạo yêu cầu duyệt** gửi chủ.

**AC-11 · FR-BIL-04, BR-02: hạn mức theo ca**
- Cho trước quản lý Lan đã giảm tổng 550.000đ trong ca (trong hạn mức từng bill).
- Khi Lan giảm thêm 80.000đ trên một bill khác.
- Thì tổng trong ca thành 630.000đ, vượt 600.000đ, nên hệ thống **đòi chủ duyệt**.

**AC-12 · FR-BIL-05, FR-AUD-03, BR-03: dự phòng khi chủ không trả lời**
- Cho trước yêu cầu duyệt 250.000đ đã gửi chủ, **3 phút** không phản hồi.
- Khi quản lý chọn "Lỗi phục vụ thật".
- Thì hệ thống đòi **PIN của thu ngân hoặc bếp trưởng**. Sau đó áp giảm giá, **gửi cảnh báo ngay cho chủ**, đưa vào **danh sách rà soát của kế toán**.
- Với mức 350.000đ, nhánh dự phòng **không mở**.

**AC-13 · FR-BIL-06, BR-04: xoá món tính trùng**
- Cho trước bill có 2 dòng "Bia chai" nhưng khách chỉ uống 1.
- Khi xoá 1 dòng với lý do "tính trùng".
- Thì số đã dùng trong **hạn mức giảm giá không tăng**. Nhật ký ghi lý do và người duyệt (nếu món đã tới quầy hoặc bếp).

**AC-14 · FR-BIL-10, BR-19: xác nhận chuyển khoản thủ công**
- Cho trước không có thông báo tự động từ ngân hàng.
- Khi thu ngân xác nhận chuyển khoản.
- Thì hệ thống **bắt buộc nhập số tiền và mã tham chiếu**. **Không có** nút "xác nhận theo ảnh chụp".
- Khi thu ngân đánh dấu "không chắc chắn".
- Thì khoản thanh toán chờ **quản lý duyệt**.

**AC-15 · FR-BIL-09, FR-INT-01: QR động tự xác nhận**
- Cho trước bill B-205 của quán Đống Đa, phải trả 780.000đ.
- Khi ngân hàng gửi thông báo giao dịch 780.000đ với nội dung "BNN DDA B205".
- Thì khoản thanh toán **tự chuyển sang Đã xác nhận**.
- Khi số tiền là 700.000đ.
- Thì **không** tự xác nhận; giao dịch vào **hàng chờ xử lý tay**.

**AC-16 · FR-BIL-11, BR-20: offline, thẻ chưa xác nhận**
- Cho trước quán đang mất Internet.
- Khi khách trả thẻ 500.000đ.
- Thì khoản được ghi **"dự định, chưa xác nhận"**, **không** cộng vào tiền đã nhận, và **hiện trong danh sách ở màn hình chốt ca**.

**AC-17 · FR-BIL-12, FR-BIL-15, FR-INT-02, NFR-33: dữ liệu HĐĐT**
- Cho trước bill có món thuộc 2 loại thuế khác nhau và khách công ty nhập đủ tên, MST, địa chỉ, email.
- Khi đóng bill.
- Thì hàng chờ HĐĐT có **1 bản ghi** gồm đủ các trường bắt buộc theo NĐ 254/2026, Điều 10: người mua, dòng hàng (tên, đơn vị, số lượng, đơn giá), thành tiền chưa thuế, **thuế suất và tiền thuế theo từng thuế suất**, tổng thanh toán.

**AC-18 · NFR-23: đổi thuế suất bằng cấu hình**
- Cho trước loại thuế "Dịch vụ ăn uống" có thuế suất 8% tới 31/12/2026, và một thuế suất mới được cấu hình từ 01/01/2027 (giá trị do kế toán xác nhận).
- Khi đóng một bill ngày 31/12/2026 và một bill ngày 01/01/2027.
- Thì bill thứ nhất dùng 8%, bill thứ hai dùng thuế suất mới. **Không cần phát hành phần mềm mới.**

## 3. Ca và két

**AC-19 · FR-SHF-03, BR-23: giao ca**
- Cho trước ca trưa kết thúc.
- Khi giao ca.
- Thì hệ thống **đòi PIN của cả hai người**. Thiếu một PIN thì không hoàn tất được.

**AC-20 · FR-SHF-01, FR-SHF-04, FR-SHF-06, BR-29: chốt ca có chênh lệch**
- Cho trước ca mở với quỹ đầu ca 1.000.000đ và tiền mặt dự kiến 12.450.000đ.
- Khi thu ngân nhập đếm được 12.200.000đ.
- Thì chênh lệch **−250.000đ** hiện ra, **bắt buộc lý do**, **cảnh báo chủ ngay** (vì > 200.000đ). Không có chức năng sửa doanh thu.
- Sau khi quản lý ký, **báo cáo chốt ca tự gửi kế toán**.

**AC-21 · FR-SHF-02, BR-24: chi tiền mặt**
- Cho trước quản lý chi 250.000đ mua đá với lý do.
- Khi tới lúc chốt ca mà phiếu chi **chưa có ảnh chứng từ**.
- Thì hệ thống cảnh báo và **không cho chốt ca** cho tới khi đính kèm.
- Khi chi 400.000đ.
- Thì hệ thống **đòi chủ duyệt trước**, hoặc chọn "khẩn cấp" và **bắt buộc ghi vì sao không duyệt trước được**.

## 4. Đặt bàn và cọc

**AC-22 · FR-RSV-04, BR-10: cấn trừ cọc**
- Cho trước booking K-31 có cọc 1.000.000đ đã nhận.
- Khi nhóm tới ăn, bill 3.400.000đ.
- Thì bill hiện **cọc −1.000.000đ**, còn phải trả 2.400.000đ. Doanh thu ghi nhận là **3.400.000đ**, không phải 4.400.000đ.

**AC-23 · FR-RSV-05, BR-11: huỷ booking**
- Cho trước cọc 2.000.000đ và **có** bằng chứng đã gửi điều khoản.
- Khi khách huỷ trước 30 giờ thì hoàn **2.000.000đ**.
- Khi khách huỷ trước 10 giờ thì giữ **1.000.000đ**, hoàn **1.000.000đ**.
- Khi khách huỷ trước 10 giờ và **dời sang ngày trong vòng 7 ngày** thì **chuyển toàn bộ** cọc sang booking mới.
- Cho trước **không có** bằng chứng điều khoản. Khi khách huỷ trước 10 giờ thì hoàn **2.000.000đ**.

## 5. Kênh giao hàng

**AC-24 · FR-DLV-02, BR-40: chặn trùng đơn app**
- Cho trước đơn GrabFood mã "GF-8812" đã có.
- Khi nhân viên nhập lại mã "GF-8812" cho kênh GrabFood.
- Thì hệ thống **cảnh báo trùng và không tạo đơn**. Mã giống nhau ở **kênh khác** không bị coi là trùng.

## 6. Offline và khôi phục

**AC-25 · FR-OFF-01, FR-OFF-02, FR-OFF-03, NFR-08: mất Internet 30 phút**
- Cho trước buổi tối thử có giám sát.
- Khi ngắt đường Internet (cả 4G) trong 30 phút.
- Thì gọi món, in phiếu bếp, màn hình bếp, bill, tiền mặt **vẫn chạy**. Khi có mạng lại, dữ liệu đồng bộ lên cloud với **0 bản ghi trùng**. Mọi thiết bị hiện **thời điểm mất và khôi phục kết nối**.

**AC-26 · FR-OFF-04, BR-38: nhập bù phiếu giấy**
- Cho trước phiếu giấy số 017 (bàn 4, 2 món) lập khi máy chủ tại quán bị mất điện.
- Khi thu ngân nhập bù phiếu 017.
- Thì cờ **mặc định là "Đã phục vụ, không gửi bếp"** và **không có phiếu nào in ở bếp**. Muốn gửi bếp phải **đổi cờ và xác nhận lần hai**.

**AC-27 · FR-OFF-05, BR-39: đối chiếu trước khi chốt ca**
- Cho trước ngày có sự cố và chế độ khôi phục đã bật.
- Khi thu ngân chốt ca.
- Thì hệ thống **không cho chốt** cho tới khi danh sách đối chiếu được hoàn tất và **quản lý ký**.

## 7. Báo cáo và cảnh báo

**AC-28 · FR-RPT-04, BR-29: cảnh báo đúng ngưỡng**
- Khi xảy ra một trong các sự kiện sau thì chủ nhận **cảnh báo ngay**:
  - lệch két 210.000đ;
  - huỷ món **đã phục vụ** trị giá 120.000đ;
  - dùng nhánh dự phòng;
  - máy chủ tại quán **mất tín hiệu 16 phút** lúc 19:30.
- Khi huỷ món đã phục vụ trị giá 60.000đ, hoặc xoá món tính trùng, thì **không** cảnh báo, chỉ đưa vào **báo cáo đêm**.

**AC-29 · FR-RPT-01: dashboard cùng đêm**
- Cho trước 3 quán đã chốt ca cuối lúc 23:00.
- Khi chủ mở dashboard lúc 23:20.
- Thì mỗi quán hiện: doanh thu trước và sau giảm giá, theo kênh, theo phương thức; lệch két; tổng QR và thẻ; đơn app; giảm giá; huỷ món; danh sách **khoản chưa giải quyết** (ví dụ khoản thẻ chưa xác nhận, tiền app chưa về).

## 8. Kho, sơ chế, mua hàng

**AC-30 · FR-PRP-03, FR-PRP-05, BR-34: chuyển kho bị tranh chấp**
- Cho trước phiếu chuyển 10 hộp sốt từ bếp sơ chế tới Hai Bà Trưng.
- Khi Hai Bà Trưng xác nhận **9 hộp nhận tốt, 1 hộp bị rò**.
- Thì phiếu chuyển sang **Tranh chấp**. Tồn kho Hai Bà Trưng tăng 9 hộp. **Giá chuyển chỉ tính cho 9 hộp**. Hộp rò ghi **hao hỏng chờ xem xét**, chưa quy vào quán nào.

**AC-31 · FR-PUR-04, BR-32, BR-33: nhận hàng**
- Cho trước phiếu nhận 20kg thịt bò.
- Khi người nhận bấm xác nhận mà **chưa nhập số kg thực nhận** hoặc **chưa nhập nhiệt độ** thì hệ thống **không cho xác nhận**.
- Khi giá trên phiếu giao là 285.000đ/kg, còn giá thoả thuận là 270.000đ/kg, thì dòng hàng được **đánh dấu chênh giá**. Thiếu ảnh **không chặn** việc xác nhận.

**AC-32 · FR-INV-06, BR-36: báo cáo chênh lệch**
- Cho trước kỳ kiểm kê bia Tiger chai có đủ tồn đầu, nhập, chuyển, bán, bể vỡ, khuyến mãi.
- Khi xem báo cáo.
- Thì chênh lệch hiện **theo đơn vị chai**, tách theo từng loại giao dịch.
- Nếu **chưa có kiểm kê đầu kỳ đáng tin** thì báo cáo **không hiện %** mà hiện cảnh báo "chưa có baseline".

## 9. Phân quyền và menu

**AC-33 · FR-ADM-03, BR-28: phân quyền**
- Khi phục vụ, thu ngân hoặc quản lý mở chi tiết "Sốt chấm nướng" thì chỉ thấy **tên món và chất gây dị ứng**; bếp trưởng và chủ thấy **công thức chi tiết**.
- Quản lý Cầu Giấy **không** xem được doanh thu hay lợi nhuận quán Đống Đa.
- Kế toán **không** có chức năng sửa order bếp.

**AC-34 · FR-MNU-09, FR-MNU-10, BR-15, BR-16: menu và giá**
- Cho trước món mới "Lẩu nấm" đã được chủ duyệt giá nhưng **chưa có loại thuế**. Khi phục vụ tìm món thì món **không bán được**.
- Trên màn hình thu ngân **không có** chức năng đổi giá bán.

## 10. Bổ sung sau kiểm tra độ phủ

> Thêm sau khi script kiểm tra phát hiện một số yêu cầu Must của thí điểm chưa có tiêu chí chấp nhận trực tiếp (xem [báo cáo chất lượng](../05-kiem-soat-chat-luong/02-kiem-tra-chat-luong.md)).

**AC-35 · FR-BIL-03, FR-BIL-07: gộp bill và thanh toán kết hợp**
- Cho trước bàn 3 (820.000đ) và bàn 4 (430.000đ) cùng một nhóm khách.
- Khi thu ngân gộp hai bàn thành một bill.
- Thì bill có **1.250.000đ** và đủ các dòng món của cả hai bàn.
- Khi khách trả 250.000đ tiền mặt và 1.000.000đ chuyển khoản.
- Thì bill ghi **hai khoản thanh toán riêng**; báo cáo cuối ngày tính đúng 250.000đ vào tiền mặt và 1.000.000đ vào chuyển khoản.

**AC-36 · FR-BIL-13, FR-BIL-14, BR-25, BR-27: hoàn tiền và đổi phương thức sau khi chốt**
- Cho trước bill đã đóng và đã có số HĐĐT.
- Khi quản lý duyệt hoàn 220.000đ với lý do "lỗi chất lượng bếp".
- Thì hệ thống ghi **khoản hoàn** (phương thức, người duyệt), tạo **yêu cầu điều chỉnh HĐ** cho kế toán, và **không xoá** bản ghi HĐ.
- Khi thu ngân đổi một khoản từ "tiền mặt" sang "chuyển khoản" sau khi bill đã chốt.
- Thì hệ thống đòi **lý do và người duyệt**; bản ghi gốc vẫn còn.

**AC-37 · FR-AUD-01, FR-AUD-02, FR-AUD-04, FR-SHF-05: nhật ký không sửa được**
- Cho trước một lần giảm giá đã áp.
- Khi tra lịch sử bill.
- Thì thấy **người làm, người duyệt, thời điểm, thiết bị, giá trị trước và sau, lý do**.
- Khi quản lý sửa số đếm két đã chốt từ 12.200.000đ thành 12.250.000đ.
- Thì hệ thống tạo **bản ghi điều chỉnh mới**, bản ghi gốc vẫn còn.
- **Không có** chức năng xoá hay sửa bản ghi nhật ký. Kiểm tra chuỗi băm của nhật ký cho kết quả **hợp lệ**.

**AC-38 · FR-KIT-03, FR-KIT-04, FR-ORD-10: trạng thái món từ bếp tới sảnh**
- Cho trước bàn 5 có 3 món ở khu Nướng.
- Khi đầu bếp bấm **Bắt đầu** rồi **Xong** cho 1 món.
- Thì màn hình điều phối hiện "1/3 xong"; máy cầm tay của phục vụ phụ trách bàn 5 hiện món đó ở trạng thái **Xong, chờ mang ra**.
- Khi người chạy món bấm **Đã mang ra**.
- Thì món biến khỏi danh sách chờ của bếp.

**AC-39 · FR-RPT-03, FR-RPT-07: báo cáo cuối ngày và báo cáo ngoại lệ**
- Cho trước ngày có 2 lần giảm giá, 1 lần huỷ món đã phục vụ, 1 cọc được cấn trừ, 1 khoản thẻ chưa xác nhận.
- Khi kế toán mở báo cáo cuối ngày của quán.
- Thì báo cáo có đủ mục theo FR-RPT-03; khoản thẻ chưa xác nhận nằm ở mục **chưa giải quyết**.
- Báo cáo ngoại lệ liệt kê 3 điều chỉnh kèm **lý do, người làm, người duyệt**; lọc được riêng lý do "Khác".

**AC-40 · FR-ADM-05, FR-ADM-06: tham số và lý do**
- Khi chủ đổi trần theo ca từ 600.000đ lên 700.000đ.
- Thì lịch sử tham số ghi **giá trị cũ, giá trị mới, người đổi, thời điểm**. Các yêu cầu tạo **sau** thời điểm đổi dùng mức mới.
- Khi bất kỳ ai chọn lý do "Khác" mà để trống ghi chú.
- Thì hệ thống **không cho lưu**.

**AC-41 · FR-DLV-01, FR-DLV-03: đơn mang về và huỷ đơn app**
- Cho trước khách mua mang về 2 món.
- Khi thu ngân tạo đơn.
- Thì giá lấy từ **bảng giá mang về** và đơn tới bếp như order thường.
- Khi một đơn ShopeeFood bị huỷ trên app.
- Thì nhân viên chuyển đơn sang **Đã huỷ** và **bắt buộc chọn lý do**. Nếu bếp đã làm thì món được ghi hao hỏng.

**AC-42 · FR-ORD-11, FR-RPT-08: sổ lỗi món**
- Cho trước phục vụ phát hiện bàn 7 bị trùng 1 set lẩu **trước khi** khách phàn nàn.
- Khi ghi lỗi với loại "trùng" và nguyên nhân "nhập hai lần".
- Thì báo cáo lỗi món tuần đếm **1 lỗi trùng**, có nguyên nhân, dùng để đo mục tiêu G3.

## 11. Kênh khách QR và tự thanh toán (CR-01)

**AC-43 · FR-GST-03, BR-41: bàn chưa mở**
- Cho trước bàn 6 **chưa được mở** trên hệ thống.
- Khi khách quét QR bàn 6.
- Thì trang hiện "Vui lòng đợi nhân viên đón và mở bàn". **Không** tạo được phiên khách, **không** gửi được món.

**AC-44 · FR-GST-02, BR-57: mã ngồi bàn**
- Cho trước bàn 6 vừa mở với mã ngồi bàn "4821".
- Khi một điện thoại nhập sai mã 5 lần trong 15 phút.
- Thì điện thoại đó **bị khoá 15 phút** và quản lý nhận cảnh báo.
- Khi khách nhập đúng "4821".
- Thì phiên khách được tạo; các lần mở sau trên cùng điện thoại **không phải nhập lại**.
- Sau khi bill đóng và bàn được mở cho nhóm mới, mã cũ **không còn dùng được**. Phiếu bếp **không** chứa mã ngồi bàn.

**AC-45 · FR-GST-05, FR-GST-06, BR-42: xác nhận đơn QR**
- Cho trước khách ở bàn 6 gửi 2 "Nước suối".
- Thì trang khách hiện **Chờ nhân viên xác nhận** và **không có phiếu bếp nào được in**.
- Khi phục vụ khu vực bấm **Xác nhận** trên máy cầm tay.
- Thì phiếu in ở quầy đồ uống, trang khách đổi sang **Bếp đã nhận** trong ≤ 5 giây.
- Nếu sau 1 phút chưa ai xác nhận thì đơn được **tô nổi** và thu ngân nhận nhắc.

**AC-46 · FR-GST-07, BR-43, BR-37: luôn chờ người**
- Khi khách gửi 2 "Bia Tiger chai" thì đơn có cờ **"kiểm tra tuổi khi mang ra"**.
- Khi khách gửi món kèm ghi chú "dị ứng tôm" thì đơn **không xác nhận được** cho tới khi phục vụ đánh dấu **đã trao đổi với khách** và bếp trưởng bấm **đã xem**.
- Khi khách gửi 12 phần bò (vượt ngưỡng) thì đơn có cờ **số lượng bất thường**.
- Các cờ này **vẫn còn** kể cả khi QR-2 bật tự nhận món.

**AC-47 · FR-GST-08, BR-44: nghi trùng**
- Cho trước điện thoại A của bàn 6 gửi "Lẩu set A" lúc 19:02.
- Khi điện thoại B gửi "Lẩu set A" lúc 19:03.
- Thì đơn thứ hai được **tô nổi "có thể trùng với đơn 19:02"**. Không đơn nào bị xoá tự động; phục vụ chọn giữ một hoặc cả hai.

**AC-48 · FR-GST-09, BR-45: rút lại món**
- Khi đơn **còn chờ xác nhận** thì khách bấm **Rút lại** được, đơn biến khỏi hàng chờ.
- Sau khi đơn đã được xác nhận, nút **Rút lại** không còn, thay bằng **Gọi nhân viên**.

**AC-49 · FR-GST-10, BR-47: trạng thái trung thực**
- Cho trước cấu hình "hiện trạng thái Xong" **tắt**.
- Khi người ở pass bấm Xong cho món.
- Thì khách **không** thấy "Xong", chỉ thấy *Chờ nhân viên xác nhận → Bếp đã nhận → Đã phục vụ*.
- Khi bật cấu hình thì "Xong" xuất hiện.
- **Không bao giờ** có trạng thái "đang nấu" hay giờ dự kiến.

**AC-50 · FR-GST-04, BR-46: giờ gọi cuối**
- Lúc 21:46, món ăn trên trang khách chuyển sang **"đã qua giờ gọi món"** và không gửi được. Đồ uống chai **vẫn gọi được**.
- Khi quản lý bấm **lệnh gọi cuối** lúc 22:15 thì đồ uống chai cũng bị khoá.

**AC-51 · FR-GST-12, BR-56: tạm dừng QR**
- Khi quản lý tạm dừng QR của bàn 6.
- Thì trang khách hiện "Tạm dừng gọi món, vui lòng gọi nhân viên"; **bill bàn 6 vẫn mở**. Đơn khách gửi trong lúc tạm dừng bị **từ chối ngay**.
- Sau khi bật lại, **không** đơn nào bị từ chối trước đó được giao xuống bếp.

**AC-52 · FR-GST-12, BR-55: mất kết nối**
- Khi kết nối giữa máy chủ tại quán và cloud bị ngắt.
- Thì trong ≤ 60 giây QR của quán **tự tạm dừng**, quản lý nhận cảnh báo.
- Đơn khách thử gửi trong lúc mất kết nối **không bao giờ** xuất hiện ở bếp sau khi kết nối lại.

**AC-53 · FR-GST-13, BR-48: chỉ trả bill đã chốt**
- Cho trước bill bàn 6 có một yêu cầu giảm giá **đang chờ chủ duyệt**. Thì nút **Thanh toán** trên trang khách **bị khoá**, kèm lý do "bill đang được cập nhật".
- Khi bill đã chốt với cọc 1.000.000đ và giảm giá 100.000đ đã duyệt.
- Thì trang hiện đủ món, giảm giá, cọc, và **số còn phải trả**.

**AC-54 · FR-GST-14, FR-GST-15, FR-GST-17, BR-49: tự thanh toán thành công**
- Cho trước lệnh thanh toán 1.230.000đ, mã tham chiếu "BNN DDA 7K3F2Q".
- Khi tuyến ngân hàng gửi thông báo **có chữ ký hợp lệ**, đúng số tiền và đúng mã.
- Thì bill **đã thanh toán** trong ≤ 10 giây. Khách thấy "Đã nhận thanh toán". Máy của phục vụ hiện bàn 6 **đã trả, có thể dọn**. Dữ liệu HĐĐT vào hàng chờ.
- Thông báo **sai chữ ký** bị **bỏ qua** và ghi nhật ký.

**AC-55 · FR-GST-15, FR-GST-16, BR-49: trả thiếu, sai mã**
- Khi khách chuyển 1.000.000đ cho lệnh 1.230.000đ.
- Thì bill ở trạng thái **trả một phần**, còn 230.000đ; thu ngân thấy trên màn hình theo dõi.
- Khi có giao dịch 650.000đ với nội dung **không có mã tham chiếu**, trong khi hai bàn cùng nợ 650.000đ.
- Thì giao dịch vào hàng chờ **không khớp**. Hệ thống **không gán** cho bàn nào.

**AC-56 · FR-GST-14, FR-GST-19, BR-50, BR-51: chờ xác nhận và trả trùng**
- Cho trước lệnh thanh toán ở trạng thái **chờ xác nhận** quá 3 phút. Thì trang khách **không có** nút "trả lại" và hiện "đang chờ ngân hàng xác nhận"; thu ngân thấy lệnh đang chờ.
- Khi bill đã đủ tiền và có thêm một giao dịch cùng mã.
- Thì giao dịch sau được đánh dấu **trả trùng** và tạo **việc hoàn tiền về tài khoản gốc**; khoản thanh toán gốc **không bị xoá**.

**AC-57 · FR-GST-18, BR-54: thông tin HĐ và đồng ý tiếp thị**
- Khi khách mở phần "Xuất hoá đơn công ty".
- Thì các trường là **tuỳ chọn** và có bước **xem lại trước khi gửi**; dữ liệu vào hàng chờ HĐĐT để kế toán kiểm tra.
- Ô "Nhận ưu đãi qua Zalo/SMS" **không được tích sẵn**.

**AC-58 · FR-GST-03, BR-41: chuyển bàn**
- Cho trước nhóm ở bàn T1 (sân thượng) đang dùng QR.
- Khi quản lý chuyển nhóm sang bàn 8.
- Thì QR bàn T1 **ngừng nhận đơn** của nhóm; điện thoại của khách vẫn trong phiên và gửi món vào **bill mới của bàn 8**.

**AC-59 · FR-GST-20, BR-58: tiệc phòng riêng**
- Cho trước tiệc ở phòng riêng Hai Bà Trưng **chưa** được quản lý xác nhận set đặt trước. Thì trang khách hiện "Vui lòng trao đổi với quản lý", không gửi được món.
- Sau khi xác nhận, khách gọi **thêm** được. **Không có** nút tự thanh toán cho tiệc (thanh toán qua thu ngân).

**AC-60 · NFR-38, FR-GST-03: cách ly phiên khách**
- Khi điện thoại có phiên của bàn 6 thử **đăng ký kênh realtime** hoặc gọi API của bàn 7.
- Thì yêu cầu bị **từ chối** và ghi nhật ký. Không có dữ liệu nào của bàn 7 bị trả về.

**AC-61 · FR-GST-01, BR-53: cấp lại mã QR bàn**
- Cho trước quản lý nghi thẻ QR bàn 6 bị dán đè.
- Khi quản lý bấm **Cấp lại mã QR** và in thẻ mới.
- Thì quét thẻ **cũ** hiện "mã QR không còn hiệu lực, vui lòng hỏi nhân viên"; quét thẻ mới hoạt động bình thường. Nhật ký ghi người cấp lại và lý do.

**AC-62 · FR-GST-11: gọi nhân viên**
- Khi khách bàn 6 bấm **Gọi nhân viên** với lý do "dị ứng".
- Thì máy của phục vụ khu vực hiện yêu cầu **khẩn** ở đầu danh sách. Khi phục vụ bấm **Đã nhận** thì trang khách hiện "nhân viên đang tới".
- Thời gian từ lúc gọi tới lúc nhận được ghi lại để đo mục tiêu nội bộ 2–3 phút.

**AC-63 · FR-GST-21: chỉ số QR**
- Cho trước 1 tuần QR-1 ở 6 bàn.
- Khi quản lý mở báo cáo chỉ số QR.
- Thì thấy:
  - tỷ lệ món gọi thêm qua QR trên tổng món gọi thêm **ở 6 bàn thí điểm** (G6);
  - thời gian chờ xác nhận (trung vị, P90);
  - thời gian từ "yêu cầu tính tiền" tới xác nhận thanh toán (G7);
  - **số ca ngân hàng xác nhận chậm, tách riêng**;
  - thời gian phản hồi gọi nhân viên.

## 12. Nhân viên quét QR bàn (CR-02)

**AC-64 · FR-TBL-07, FR-TBL-08, BR-64: mở bàn trống bằng quét**
- Cho trước bàn "Sân trong C3" ở Đống Đa đang **trống** và là bàn thí điểm.
- Khi phục vụ quét thẻ bằng app nhân viên.
- Thì trong ≤ 2 giây app hiện chữ lớn **"SÂN TRONG C3 · TRỐNG"**.
- Sau khi nhập 6 khách và bấm **Mở bàn**, app hiện **mã ngồi bàn mới** để ghi lên thẻ đưa khách. Nhật ký ghi cách chọn bàn là **"quét"**.

**AC-65 · FR-TBL-08, BR-64: bàn đang có khách**
- Cho trước bàn 12 đang phục vụ một nhóm và có 1 đơn QR chờ xác nhận.
- Khi phục vụ khác quét thẻ bàn 12.
- Thì app mở **nhóm hiện tại** (bill, món, đơn QR chờ). **Không có** nút hay đường nào để mở nhóm thứ hai.

**AC-66 · FR-TBL-08, BR-64: bàn đã đặt và bàn đang dọn**
- Khi phục vụ quét bàn 8 **đã đặt** cho 19:30 thì app hiện **giờ đặt và tên booking**. Nút "cho khách vãng lai ngồi" chỉ bấm được bằng **PIN quản lý**.
- Khi quét bàn 9 **đang dọn** thì app chặn mở bàn cho tới khi quản lý đánh dấu sẵn sàng.

**AC-67 · FR-TBL-07, BR-63: chọn tay khi thẻ hỏng**
- Cho trước thẻ bàn 4 bị ướt, không đọc được.
- Khi phục vụ chọn bàn 4 trên sơ đồ.
- Thì mở được bàn như bình thường; nhật ký ghi **"chọn tay"**.

**AC-68 · FR-TBL-07, FR-TBL-08, BR-68: quét khi mất Internet**
- Cho trước đường Internet của Cầu Giấy bị đứt, **máy chủ tại quán và Wi-Fi nội bộ vẫn chạy**.
- Khi phục vụ quét thẻ và mở bàn.
- Thì mọi thứ chạy bình thường (trang khách lúc này đang tạm dừng).
- Khi **máy cầm tay mất liên lạc với máy chủ tại quán** thì app **không mở được bàn** và nhắc dùng phiếu giấy đánh số.

**AC-69 · FR-TBL-09, BR-65: chuyển nhóm bằng quét**
- Cho trước nhóm 6 khách ở bàn Sân trong C1 và C2.
- Khi quản lý quét C1, rồi quét hai bàn trong nhà 7 và 8 (đều trống).
- Thì app hiện **"C1, C2 → 7, 8"**. Sau khi xác nhận, bill đi theo nhóm; khách quét thẻ C1 hoặc C2 thì **không** gửi được món vào bill của nhóm này nữa.
- Khi chọn bàn đích đang có **nhóm khác** thì app **không cho chọn**. Khi chọn bàn **đã đặt** thì app đòi quản lý xử lý booking trước.

**AC-70 · FR-BIL-18, BR-66: thu ngân quét để mở bill**
- Cho trước nhóm đã chuyển từ C1, C2 sang 7, 8 và có cọc 1.000.000đ.
- Khi thu ngân quét thẻ bàn 7.
- Thì app hiện bill của **cả nhóm**: các bàn 7, 8 (đã chuyển từ C1, C2), cọc, giảm giá đã duyệt, số còn phải trả. Có bước **đọc lại số bàn và số tiền**.
- Không có trạng thái thanh toán nào thay đổi chỉ vì quét.

**AC-71 · FR-GST-26, BR-67: khách quét ở bàn không thí điểm**
- Khi khách quét thẻ bàn 15 (không thuộc 6 bàn thí điểm) bằng camera.
- Thì trang hiện thực đơn **chỉ đọc** kèm lời nhắn "Vui lòng gọi nhân viên để gọi món". **Không có** nút gửi món hay gọi nhân viên.

**AC-72 · FR-KIT-10, BR-69, BR-70: nguồn đơn ở bếp**
- Khi phục vụ Hoa gửi món đầu, phiếu bếp ghi **"PV: Hoa"**.
- Khi đơn QR được phục vụ Minh xác nhận, phiếu ghi **"QR – Minh xác nhận"**.
- Đơn QR **đang chờ** không xuất hiện trên phiếu hay màn hình bếp.
- Set lẩu chưa đủ thành phần thì **không** bấm được "Xong" cho cả set.

**AC-73 · FR-TBL-07, BR-62: thẻ của quán khác**
- Khi phục vụ ở Đống Đa quét thẻ của quán Hai Bà Trưng.
- Thì app báo **"Thẻ không thuộc quán này"**, không mở gì.

**AC-74 · FR-GST-25, BR-43: xác nhận tại bàn không có đường tắt**
- Cho trước bàn 6 có đơn QR "2 bia" và "gà nướng, ghi chú dị ứng tôm" đang chờ.
- Khi phục vụ quét thẻ bàn 6.
- Thì app hiện cả hai đơn.
  - Đơn bia chỉ xác nhận được sau khi bấm **"đã kiểm tra tuổi khi cần"**.
  - Đơn dị ứng chỉ xác nhận được sau khi đánh dấu **"đã trao đổi với khách"** và bếp trưởng bấm **đã xem**.
- **Không có** nút xác nhận tất cả.
