# Tóm tắt phỏng vấn khách hàng

> **Quy ước** (theo guardrail của skill `requirements-elicitation`: tách sự kiện, suy luận, giả định):
> **[F]** = sự kiện khách hàng đã nói · **[I]** = suy luận của BA, chưa xác nhận · **[?]** = câu hỏi còn mở.
> Mã nguồn `S1-Q7` = Buổi 1, câu 7 trong biên bản gốc [02-bien-ban-phong-van.en.md](02-bien-ban-phong-van.en.md).

---

## Buổi 0 — Yêu cầu ban đầu

- Khách: chị **Mai Anh**, chủ chuỗi 3 quán **Bếp Nhà & Nướng** tại Hà Nội. [F]
- Mỗi quán có POS nhưng vẫn ghi order giấy và tổng hợp số liệu bằng Excel. [F]
- Đau: giờ đông bếp sót món gọi thêm; chủ không thấy tình hình 3 quán cho tới hôm sau. [F]
- Mong muốn: phục vụ trơn tru hơn; thấy doanh thu và kho toàn chuỗi; thí điểm 1 quán rồi nhân rộng. [F]

---

## Buổi 1 — Bối cảnh, mục tiêu, ràng buộc (chủ quán)

### 1.1 Hồ sơ doanh nghiệp [F]

| | Đống Đa | Cầu Giấy | Hai Bà Trưng |
|---|---|---|---|
| Bàn / chỗ ngồi | 24 / ~96 (6 bàn sân trong có mái) | 20 / ~80 (4 bàn mặt tiền, khu quây riêng cho nhóm) | 22 / ~88 (phòng riêng 3 bàn, sân thượng 4 bàn) |
| Khách ngày thường | ~100 | 85–100 | 90–110 |
| Khách cuối tuần đông | 160–175 | 145–160 | 150–165 |

- Mở cửa 10:30–22:30 mỗi ngày; bếp sơ chế từ sớm hơn. (S1-Q2)
- Mô hình: món Việt ăn chung, đồ nướng than, lẩu theo set. Khoảng 85 mục menu (tính cả size và món theo mùa). Có bia, nước ngọt, trà. Thịt bán theo nhiều size phần, **không** bán theo kg. (S1-Q1)
- Chi tiêu 210–250k/người, cao hơn khi nhóm uống bia. (S1-Q2)
- Tỷ trọng kênh (ước lượng, **kế toán cần xác nhận**): 75–80% ăn tại chỗ, ~5% mang về, phần còn lại qua app giao hàng (GrabFood, ShopeeFood). (S1-Q2, S1-Q6)

### 1.2 Tổ chức và thẩm quyền [F] (S1-Q3)

- **Khối chung:** chủ (Mai Anh), 1 điều phối mua hàng, 1 kế toán, 2 nhân viên sơ chế chung.
- **Mỗi quán:** 1 quản lý, 1 thu ngân, 1 bếp trưởng, 3–4 phụ bếp, ~7 phục vụ/chạy món, 1 tạp vụ. Thêm part-time vào tối đông. Tổng dưới 50 người cố định và khoảng 7 part-time.
- **Thẩm quyền:**
  - Chủ duyệt giá bán, khuyến mãi lớn, tuyển quản lý và bếp trưởng.
  - Quản lý xử lý sự cố nhỏ với khách, xếp ca, tuyển phục vụ (chủ duyệt), nhận hàng, chốt két.
  - Điều phối mua hàng đặt hàng NCC theo đề xuất của quản lý và bếp.
  - Kế toán soát số liệu chốt ca.
- **Quy định giảm giá chưa được viết rõ** (chủ tự nhận).

### 1.3 Quy trình phục vụ hiện tại (as-is, Đống Đa giờ cao điểm) [F] (S1-Q4, Q5)

1. Phục vụ hoặc quản lý hỏi khách có đặt bàn không, tìm bàn trên **sơ đồ giấy ép plastic**, dẫn khách vào.
2. Order đầu tiên ghi **giấy**. Thu ngân nhập vào **POS365**, máy in ra **phiếu bếp**.
3. Bếp làm theo phiếu. Khi ồn, phục vụ **hô miệng** hoặc **nhắn Zalo** để báo thay đổi. Đồ uống xử lý riêng.
4. Món gọi thêm: phục vụ ghi giấy, thu ngân thêm vào bill.
5. Thanh toán: thu ngân in bill, nhận tiền mặt, kiểm tra chuyển khoản, hoặc quẹt thẻ ở **máy POS thẻ riêng**.
6. Quản lý kiểm tra bàn đã dọn rồi mở bàn lại.

- POS365: mỗi quán **một tài khoản riêng**. Chỉ dùng để nhập order tính tiền, in phiếu bếp và bill, xuất doanh thu cuối ngày.
- Lý do vẫn dùng giấy: ghi nhanh khi đứng cạnh bàn đông, nhân viên quen tay, và **dự phòng khi mất mạng**. Internet ở Cầu Giấy rớt khoảng 2 lần/tháng, mỗi lần 15–40 phút. Khi đó nhân viên ghi giấy rồi nhập bù sau, nên không rõ món nào đã tới bếp.

### 1.4 Tổng hợp số liệu hiện tại [F] (S1-Q6)

- Kế toán gom: 3 file xuất từ POS, sao kê ngân hàng, tổng tiền thẻ, báo cáo đối soát GrabFood/ShopeeFood, số chốt két của quản lý. Quản lý chụp **phiếu chốt két gửi qua Zalo**.
- Kế toán còn theo dõi mua hàng, công nợ NCC, lương.
- Chủ thấy số liệu gộp vào hôm sau, sau cuối tuần đông thì lâu hơn.
- Chủ **ít tin** hai loại số liệu:
  - Tiền theo phương thức thanh toán: chuyển khoản không có mã bill; tiền app về trễ, đã trừ phí và khuyến mãi.
  - Số liệu kho: bia đếm đều, nhưng thịt và rau trên giấy không giải thích được lượng dùng thực tế.
- Kế toán mất **2–3 giờ/ngày** để khớp số.

### 1.5 Vấn đề chủ ưu tiên [F] (S1-Q7, Q8)

1. **Order tới bếp chính xác.**
   - Ví dụ: bàn gọi thêm bò nhưng phiếu bếp không có. Phục vụ tưởng thu ngân đã nhập, thu ngân tưởng phục vụ còn xác nhận. Khách chờ 20 phút, quán giảm 150k.
   - Sót hoặc trùng món khoảng **2–3 lần/tuần** toàn chuỗi (ước lượng, không có nhật ký).
   - Từng có một set lẩu cho nhóm lớn bị làm trùng, gây lãng phí.
2. **Biết tiền thực thu và tiền đi đâu** (đối soát thanh toán đa kênh).
3. **Tiêu hao và hao hụt kho.**
   - Bếp sơ chế ở phòng sau quán Đống Đa làm sốt và chia phần thịt, rồi chuyển cho 2 quán còn lại.
   - Số hộp và số kg ghi **sổ tay**. Hàng trả lại và hao hụt không phải lúc nào cũng ghi, nên kế toán phân bổ chi phí ước chừng.
   - Một lần kiểm thấy lệch bia **4,8 triệu trong 2 tuần**, chưa rõ nguyên nhân. Chủ **không muốn đổ lỗi khi chưa có bằng chứng**.

### 1.6 Mục tiêu và tiêu chí thành công (6 tháng sau khi chạy cả chuỗi) [F] (S1-Q9)

| Mã | Mục tiêu | Chỉ số hiện tại | Đích |
|---|---|---|---|
| G1 | Chủ xem doanh thu và thanh toán tối đó của từng quán **trước khi đi ngủ**, chênh lệch có giải thích | Hôm sau, hoặc lâu hơn | Cùng ngày |
| G2 | Giảm thời gian kế toán khớp thanh toán | 2–3 giờ/ngày | < 1 giờ/ngày |
| G3 | Giảm khiếu nại sót món | ~2–3 sự cố/tuần (ước lượng) | Giảm rõ rệt (cần đo baseline) |
| G4 | Chênh lệch kho bia và thịt đắt tiền | ~6–8% (ước lượng) | < 3% (cần đo baseline) |

### 1.7 Ràng buộc [F] (S1-Q5, Q10, Q11)

- **Ngân sách:** 350–500 triệu cho xây dựng và đưa vào dùng. Vận hành và hỗ trợ không quá 8–12 triệu/tháng. Có thể bàn thêm nếu có lý do mạnh, nhưng không duyệt dự án "vô hạn".
- **Thời gian:**
  - Thí điểm nghiêm túc tại Đống Đa **cuối 01/2027**. Phải chạy được tối thứ 6 và thứ 7.
  - Hai quán còn lại xong trước **31/03/2027**.
  - Tháng 4 là mùa lễ đông khách, không thay đổi cách làm vào lúc đó.
- **Phần cứng:** sẵn sàng mua tablet, máy in, màn hình bếp, nhưng cần báo giá tổng và cho nhân viên dùng thử trước khi mua cho cả 3 quán.
- **Hạ tầng:** mạng ở Cầu Giấy không ổn định, nên hệ thống phải chạy được khi mất mạng.

### 1.8 Xung đột giữa các bên [F]

- **Quản lý ↔ Kế toán:** quản lý muốn tự do sửa bill cho nhanh; kế toán muốn **ghi lý do mỗi lần xoá món hoặc giảm giá**. (S1-Q3)
- **Phục vụ ↔ Thu ngân:** không rõ ai chịu trách nhiệm đưa món gọi thêm vào hệ thống. (S1-Q7)

### 1.9 Phương án đã tìm hiểu [F] (S1-Q12)

- Chủ đã xem demo **Sapo** và **KiotViet**, biết người dùng **iPOS** và **CukCuk**. Thấy tốt hơn giấy, nhất là phần order và báo cáo.
- Lo ngại: 3 quán, bếp sơ chế chung và việc khớp thanh toán của kế toán có vừa với phần mềm có sẵn không, hay rốt cuộc vẫn phải giữ nhiều file Excel.
- **Chủ vẫn sẵn sàng nghe chứng minh rằng một sản phẩm có sẵn đáp ứng được.**

### 1.10 Suy luận của BA [I]

- **I1:** Nguyên nhân gốc của sót món là có **nhiều "nguồn sự thật"** (giấy, POS, Zalo, lời nói) và một bước **chuyển tay qua thu ngân**. Cần một luồng order duy nhất đi thẳng từ bàn tới bếp.
- **I2:** Khó đối soát vì **giao dịch tiền không gắn với hóa đơn**. Nếu mỗi bill có QR động (VietQR kèm mã bill) và ghi nhận thanh toán theo từng bill, phần khớp tay sẽ giảm mạnh.
- **I3:** Để kiểm soát kho cần **định lượng món** và **ghi nhận chuyển kho nội bộ** từ bếp sơ chế. Khi đó mới so được lượng dùng lý thuyết với tồn kiểm thực tế.
- **I4:** Chủ mở lòng với phần mềm có sẵn, nên tài liệu phải có **phân tích phương án mua, tự xây, hoặc kết hợp**.
- **I5:** Từ lúc chốt yêu cầu tới thí điểm chỉ khoảng 4 tháng, nên cần **MVP rất rõ** và ưu tiên MoSCoW chặt.

### 1.11 Câu hỏi mở sau Buổi 1 [?]

- Quy định giảm giá và ngưỡng duyệt cụ thể? → Buổi 2
- Hiện xuất hóa đơn điện tử thế nào? Hình thức pháp lý là hộ kinh doanh hay công ty? → Buổi 2, Buổi 4
- Chấm công và lương có nằm trong phạm vi không? → Buổi 4
- Khách hàng thân thiết, khuyến mãi? → Buổi 4
- Bếp sơ chế: danh mục, quy trình, định mức, hàng trả về? → Buổi 3

---

## Buổi 2 — Phục vụ ngoài sảnh Đống Đa (quản lý Trần Ngọc Lan, thu ngân Phạm Quốc Huy)

### 2.1 Đặt bàn [F] (S2-M1)

- Khách đặt qua điện thoại, Zalo, Facebook, hoặc hẹn trực tiếp. Quản lý ghi **sổ tay**: tên, số điện thoại, ngày, giờ, số khách, yêu cầu.
- Số lượng: 4–6 lượt đặt vào ngày thường, 12–18 lượt vào thứ 7. Khách vãng lai vẫn chiếm phần lớn buổi tối.
- **Cọc** cho nhóm từ 8 người: 500k–2 triệu, nhất là thứ 7.
  - Thường chuyển vào tài khoản công ty.
  - **Đôi khi vào tài khoản cá nhân của chủ.** Khi đó quản lý phải báo lại thu ngân và kế toán, gây rối.
- Khách trễ 15 phút thì gọi điện; giữ bàn khoảng 20 phút vào tối đông. Chưa xử lý cọc no-show thống nhất, mỗi lần phải hỏi chủ.
- Chỉ quán Hai Bà Trưng có phòng riêng.

### 2.2 Xếp bàn [F] (S2-M2)

- Dùng sơ đồ giấy ép plastic. Người đón khách hoặc quản lý theo dõi trạng thái bàn: đang ăn / chờ thanh toán / đang dọn.
- Hết bàn: ghi tên và số điện thoại khách chờ ra giấy, báo thời gian chờ. Thời gian này hay ước sai vì có bàn đã xin bill nhưng còn ngồi.
- **Ghép bàn** cho nhóm lớn: báo thu ngân các số bàn thuộc nhóm.
- **Chuyển bàn:** phục vụ ghi số bàn cũ và mới lên phiếu. Sơ đồ và POS không cập nhật cùng lúc.
  - Sự cố: nhóm 6 người chuyển vào trong vì mưa, đồ uống suýt mang ra bàn cũ.

### 2.3 Gọi món [F] (S2-M3)

- Phiếu gồm: số bàn, mã phục vụ, giờ, tên hoặc mã món, số lượng, ghi chú (ít cay, không ngò, không bột ngọt).
- **Dị ứng:** quản lý muốn phục vụ báo trực tiếp bếp trưởng. Chưa rõ chính sách cho dị ứng nặng.
- Set lẩu có mã riêng, chỉ ghi chi tiết khi khách đổi thành phần. Thịt nướng: khoanh size trên phiếu.
- **Món "ra sau":** ghi "later" và giữ phiếu riêng ở quầy, rất dễ quên.
- Đồ uống: phục vụ nhận order; bia và nước lấy từ tủ cạnh quầy; trà pha tại quầy.

### 2.4 Đổi, huỷ, trả món [F] (S2-M4, B1)

| Thời điểm | Cách làm hiện tại |
|---|---|
| Bếp chưa làm | Thường cho huỷ |
| Bếp đang làm | Hỏi bếp xem có dừng được hoặc dùng cho order khác không |
| Đã mang ra | Không xoá đơn thuần; phải có lý do (sai món, món kém chất lượng) |

- Thực tế thu ngân xin quản lý duyệt hầu hết các lần xoá món. **Không có quy định bằng văn bản.**
- Sự cố: gà nướng bị khô, quán làm lại và không tính đĩa đầu. Bếp trưởng khó chịu vì quản lý hứa với khách trước khi hỏi bếp.

### 2.5 Giảm giá và tặng món [F] (S2-M5)

- Các loại đang có: giảm nhỏ khi khách chờ lâu hoặc phàn nàn; thỉnh thoảng dùng voucher; tặng món cho khách quen; chủ mời bạn (nhắn quản lý).
- **Bữa ăn nhân viên** ghi sheet riêng, **không** được tính là giảm giá cho khách.
- Thẩm quyền trên thực tế: quản lý tự quyết món tráng miệng nhỏ hoặc giảm khoảng 5–10%; vượt mức đó thì gọi chủ. Đây là thói quen, không phải chính sách có chữ ký. Lý do nhập trên POS quá chung chung.

### 2.6 Mang về và giao hàng [F] (S2-M6)

- GrabFood và ShopeeFood mỗi app một tablet. Phục vụ đứng quầy nhận đơn, ghi hoặc in cho bếp. Thu ngân **nhập lại** vào POS, tối đông thì bị trễ.
- Giá trên app cao hơn giá tại chỗ (vì phí sàn). Một số set trên app khác set tại chỗ.
- **Hết món:** quản lý bảo phục vụ tắt món trên cả 2 app, **từng quên 1 app**. Quản lý đổi được trạng thái còn/hết, còn giá do chủ duyệt. Bộ phận mua hàng đôi khi báo thiếu hàng muộn.

### 2.7 Mất mạng hoặc POS hỏng [F] (S2-M7)

- **Mất mạng:** ghi giấy và đánh dấu "chưa nhập"; người chạy món mang phiếu giấy vào bếp.
- **POS hỏng:** thu ngân viết bill tay.
- **Khi khôi phục:** thu ngân nhập bù, quản lý đối chiếu phiếu với chồng phiếu bếp. Khó biết phiếu nào đã in trước sự cố. **Quán Cầu Giấy từng bị in trùng phiếu bếp sau sự cố tháng 8.**

### 2.8 Thông tin cần trong giờ cao điểm [F] (S2-M8)

- Bàn nào chờ món lâu nhất; món nào đang nấu; bàn nào sắp trả bàn.
- Biết ngay khi món bán chạy bị hết.
- Quản lý lưu ý: *màn hình chỉ có ích nếu mọi người cập nhật.*

### 2.9 Quầy thu ngân [F] (S2-C1, C2)

- **Thiết bị trên quầy:** POS, máy in bill, két tiền, máy quẹt thẻ, điện thoại nhận thông báo ngân hàng, phiếu giấy, thường có thêm tablet app giao hàng.
- **Nhập liệu:** nhập phiếu đầu khi tới quầy, sau đó nhập món thêm và ghim phiếu lại. Giờ đông có thể 3 phục vụ cùng đưa thay đổi một lúc.
- **Tách bill:** 4–6 nhóm mỗi tối thứ 6 và thứ 7.
  - Chia đôi thì dễ.
  - Chia theo món thì khó vì lẩu và đồ uống dùng chung.
- **Gộp bàn:** khoảng 2 lần/tuần. Nếu biết muộn, thu ngân sợ sót món gọi thêm.

### 2.10 Thanh toán và két tiền [F] (S2-C3, C5)

- **Cơ cấu thanh toán** (theo cảm nhận của thu ngân): tiền mặt 25%, chuyển khoản 55%, thẻ 15%, ví điện tử 5%.
- **QR tĩnh in sẵn:**
  - Khách tự nhập số tiền. Thu ngân kiểm tra thông báo ngân hàng và số tiền, không tin ảnh chụp màn hình.
  - Thông báo ngân hàng hay về trễ. Có lúc 2 khách chuyển cùng số tiền gần nhau.
  - Thu ngân ghi số bill cạnh giao dịch nếu nhận ra được.
- Thanh toán kết hợp tiền mặt và chuyển khoản khá thường xuyên.
- **Két tiền:**
  - Quỹ đầu ca 1 triệu.
  - Giao ca trưa → tối: đếm tiền cùng thu ngân ca trước hoặc quản lý, rồi ký sổ.
  - Cuối ngày: đếm tiền, liệt kê **khoản chi tiền mặt có chứng từ**, so với số tiền mặt trên POS. Quản lý kiểm tra rồi gửi ảnh qua Zalo cho kế toán.
  - Khi lệch: đếm lại, kiểm tra các thanh toán kết hợp. Không giải thích được thì ghi nhận chênh lệch, **không sửa doanh thu cho khớp**.

### 2.11 Hoá đơn và phụ phí [F] (S2-C4)

- **Hóa đơn VAT công ty:** 4–8 khách/ngày xin, nhiều hơn khi có tiệc văn phòng.
  - Thu ngân lấy tên công ty, mã số thuế, email.
  - Kế toán xuất trên hệ thống HĐĐT của **nhà cung cấp riêng**.
  - Nếu sai thông tin, kế toán điều chỉnh.
- **Giá menu:** là giá cuối cùng, đã gồm thuế. Thuế suất từng món thì phải hỏi kế toán.
- **Phụ phí:**
  - Không có phí phục vụ, không có phí phòng riêng (tại Đống Đa).
  - **Có bảng giá ngày lễ**, thông báo trước.
  - Phí bao bì: thường đã gồm trong giá app; vài set lớn có thể tính riêng (cần kiểm tra).

### 2.12 Tình huống bất thường [F] (S2-C6)

- **Khách về không trả (360k):** phục vụ tưởng người khác trong nhóm đã trả. Quán gọi số điện thoại lúc đặt bàn, khách chuyển khoản ngay tối đó.
- **Tính trùng chai bia:** xoá trước khi thanh toán, có quản lý duyệt.
- **Khách trả 2 lần (680k):** chuyển khoản nhưng không thấy xác nhận nên quẹt thẻ thêm. Kế toán hoàn tiền hôm sau.
- **Hoàn tiền sau thanh toán (220k):** khách phàn nàn một món. Quản lý đồng ý, thu ngân ghi điều chỉnh, kế toán xử lý phần hóa đơn.

### 2.13 Mong muốn và lo ngại của người dùng [F] (S2-C7, B1)

- **Thu ngân muốn bỏ:** việc nhập lại phiếu; việc khớp chuyển khoản không rõ người gửi cuối ca.
- **Thu ngân sợ:**
  - Thêm món hoặc tách bill mất quá nhiều thao tác, khách phải xếp hàng.
  - Hệ thống đánh dấu "đã thanh toán" chỉ vì khách đưa ảnh chụp màn hình.
- **Quản lý:** khi khách đang bực, không thể để 3 người tranh luận về một món 60k.

### 2.14 Suy luận của BA [I]

- **I6:** Cần **quy tắc giảm giá và huỷ món theo ngưỡng, bắt buộc chọn lý do**. Quản lý tự duyệt trong hạn mức; vượt hạn mức thì chủ duyệt từ xa. Cách này giải quyết xung đột quản lý – kế toán – thu ngân mà không làm chậm phục vụ.
- **I7:** Trạng thái của món trong bếp (chờ / đang làm / xong / đã mang ra) quyết định ai được huỷ và huỷ có mất phí không. Vì vậy cần màn hình bếp (KDS) hoặc cách đánh dấu trạng thái.
- **I8:** Phục vụ gọi món trên tablet tại bàn, order đi thẳng xuống bếp và vào bill, bỏ bước thu ngân nhập lại. Giải quyết vấn đề #1 và mong muốn C7.
- **I9:** Cần chạy **offline-first** tại quán (máy chủ trong mạng LAN) và **mỗi phiếu có mã duy nhất**, để không bị trùng khi đồng bộ lại.
- **I10:** Dùng **QR động** (có sẵn số tiền và mã bill), xác nhận qua webhook của ngân hàng hoặc cổng thanh toán. Không còn dựa vào ảnh chụp màn hình, giảm khớp tay.
- **I11:** Tiền cọc phải ghi nhận trên hệ thống, gắn với lượt đặt bàn và tự trừ vào bill. Không để cọc vào tài khoản cá nhân.
- **I12:** Đồng bộ trạng thái hết món lên app giao hàng: cần kiểm tra có API đối tác không, hay chỉ làm được cảnh báo để thao tác tay.

### 2.15 Câu hỏi mở sau Buổi 2 [?]

- Chủ chấp nhận hạn mức giảm giá nào cho quản lý? Danh mục lý do gồm những gì? → Buổi 4
- Chính sách cọc: xử lý no-show thế nào, tài khoản nào nhận cọc? → Buổi 4
- Bảng giá ngày lễ áp dụng thế nào, cho kênh nào? → Buổi 4
- Nhà cung cấp HĐĐT hiện tại là ai? Xuất HĐ cho mọi giao dịch hay chỉ khi khách yêu cầu? → Buổi 4 (kế toán)
- Tỷ lệ thanh toán thực tế (thu ngân chỉ ước lượng)? → kế toán

---

## Buổi 3 — Bếp, sơ chế chung, mua hàng, kho (bếp trưởng Võ Thanh Đức, trưởng sơ chế Lê Thu Thảo, điều phối mua hàng Nguyễn Quang Minh)

### 3.1 Bếp và các khu chế biến [F] (S3-K1, K2)

- **Nhân sự tối thứ 6:** 4 đầu bếp và bếp trưởng.
  - 2 người ở khu **nướng**; 1 người lo **xào và nồi đất**; 1 người lo **nước lẩu và soạn set lẩu**.
  - Bếp trưởng đi lại giữa các khu, kiểm tra đĩa, hỗ trợ chỗ bị dồn. Vai trò này gọi là "điều phối ra món" (expeditor).
- Món nguội sơ chế trước, ai rảnh thì hoàn thiện. Đồ uống do phục vụ lo ở quầy, **không qua bếp**.
- **Chỉ 1 máy in** ở cửa bếp. Bếp trưởng tự chia phiếu và hô thứ tự ưu tiên:
  - Mặc định vào trước ra trước.
  - Điều chỉnh khi bàn có trẻ em, hoặc khi một món đang giữ chân cả bàn.
  - Tránh ra 4 món nóng cùng lúc cho một bàn khiến 2 món bị nguội.
- Phiếu cài trên thanh. Bắt đầu làm thì gạch bút; món rời bàn chuyển (pass) thì gạch bỏ. Người chạy món lấy món ở pass và hô số bàn.
- **Món gọi thêm in thành phiếu riêng**, nên bếp phải lục lại phiếu đầu mới thấy toàn bộ bàn.
- **"Later" là chữ tệ nhất:** không rõ là 10 phút sau, sau món đầu, hay khi khách gọi. Ví dụ: đồ lẩu đã soạn sẵn trong khi bàn còn đang ăn đồ nướng, chiếm chỗ ở pass.

### 3.2 Yêu cầu của bếp với phiếu và màn hình [F] (S3-K3, K4)

- **Thử màn hình bếp (KDS) được, nhưng ngày đầu không bỏ hẳn phiếu in.** Bếp nóng, dầu mỡ, tay ướt, ồn đến mức chuông báo gần như vô dụng. Giai đoạn thử cần có phiếu in tại từng khu.
- **Phiếu hoặc màn hình phải hiện:** số bàn, giờ, món, số lượng, size, ghi chú đặc biệt, và là **món thêm hay món thay thế**.
- **Không được giấu** "không đậu phộng" hay "giữ món này" sau nhiều lần chạm.
- **Món bị thay đổi không được biến mất lặng lẽ.** Bếp cần thấy đã đổi gì và mình đã bắt đầu làm chưa.
- **Huỷ món đã in phải báo bếp trực tiếp.** Thu ngân xoá trên máy không làm đầu bếp dừng tay.
- **Thế nào là "đã bắt đầu" tuỳ từng món:**
  - Thịt đã lên vỉ nướng: không hoàn lại nguyên liệu được.
  - Nồi đất đã cho nguyên liệu và đang nấu: có thể đã quá muộn.
  - Nguyên liệu mới cân sẵn: thường dùng lại cho order khác được.
- **Làm lại và bỏ đi:** thỉnh thoảng ghi "remake"/"waste" lên phiếu, không đếm thống nhất. Bếp phân biệt đĩa **bị bỏ** với đĩa **nếm để kiểm tra lời phàn nàn**.

### 3.3 Dị ứng [F] (S3-K5)

- Phục vụ **phải hỏi bếp trưởng** trước khi hứa món nào đó an toàn.
- Bếp dùng chung vỉ, dụng cụ, sốt và mặt bàn sơ chế, nên **không cam kết 0% tiếp xúc** với chất gây dị ứng.
- Sốt pha sẵn nên yêu cầu "không bột ngọt" không phải lúc nào cũng làm được. Với dị ứng nặng, bếp thà từ chối món còn hơn đoán.

### 3.4 Hết món [F] (S3-K6)

- Bếp trưởng quyết định hết món và báo quản lý Lan. Lan báo phục vụ và người trực app.
- Cuối tuần thỉnh thoảng hết 1–2 món đặc biệt vào cuối tối; món thường ít khi hết.
- Có độ trễ: bếp đã báo hết nhưng app vẫn nhận đơn. **Shipper phải chờ** trong lúc quán gọi khách đổi món.

### 3.5 Công thức và định lượng [F] (S3-K7, K8)

- **Đã có:** thành phần set viết sẵn và trọng lượng ước chừng của thịt chính. Heo nướng size nhỏ ~150g sống, size lớn ~250g.
- **Cách chia phần:** chia phần bằng cân ở khâu sơ chế. Giờ đông, đầu bếp lấy gói đã chia sẵn thay vì cân từng đĩa.
- **Chưa chuẩn:** rau ăn kèm ít chính xác. Công thức nước lẩu và sốt nằm trong **sổ riêng của bếp trưởng**, được chỉnh khi chất lượng nguyên liệu thay đổi.
- **Bếp trưởng không muốn thu ngân và phục vụ xem được công thức sốt** → cần phân quyền.
- Hao hụt khi sơ chế thay đổi theo lô hàng của nhà cung cấp. **Lượng dùng thực tế lệch so với định mức không có nghĩa là có người lấy.**
- **Ghi chép còn thiếu:**
  - Bữa ăn nhân viên ghi sheet, đôi khi nấu từ đồ thừa.
  - Nếm sốt không đo.
  - Hàng hỏng rõ ràng thì ghi khi nhớ; vụn thịt và rau dập thì không ghi.

### 3.6 Bếp sơ chế chung [F] (S3-P1–P4)

- **Sản phẩm:** 2 loại ướp, 3 loại sốt, nước lẩu cốt; lọc và chia phần heo, gà, một phần bò. Khoảng **35–50kg thịt/ngày** cho 3 quán, trước cuối tuần nhiều hơn.
- **Đóng gói:** túi ghi ngày, hộp dán nhãn theo quán; hộp sốt ghi ngày làm và nơi nhận.
- **Đơn vị không thống nhất** (kg, túi, hộp), gây khó cho mua hàng và kế toán.
- **Đặt hàng:** mỗi quản lý nhắn **nhóm Zalo** vào buổi chiều để đặt cho hôm sau. Sơ chế làm theo lượng quen thuộc, điều chỉnh theo lượng đặt bàn và khuyến mãi.
- **Giao hàng:** tài xế chở tới Cầu Giấy và Hai Bà Trưng buổi sáng; người nhận ký **sổ tay**. Đống Đa lấy trực tiếp ở phòng sau.
- **Thiếu hàng:** giao thêm chuyến, hoặc quán này **cho quán kia mượn qua Zalo**.
- **Hàng trả lại:** hiếm. Hộp sốt còn niêm phong trả về được kiểm tra, nhưng sổ không phải lúc nào cũng ghi.
  - Sự cố: Hai Bà Trưng báo một hộp sốt bị rò sau khi tài xế đã đi. Không rõ kế toán trừ vào kho quán nào.
- **Hao hụt khi sơ chế:** có cân hàng vào và gói thành phẩm, nhưng **không có sổ theo dõi hao hụt**.
  - Tham khảo (không phải con số chuẩn): 1kg bò sống cho ~880g thành phẩm; heo ~830g.
  - Phần vụn còn dùng cho nước lẩu hoặc bữa nhân viên thì tính thế nào: cần bếp trưởng thống nhất.
- **Chi phí:** mặt bằng và lương sơ chế nằm ở Đống Đa, nhưng thành phẩm cấp cho cả chuỗi. Kế toán đang **phân bổ theo doanh thu từng quán**. Điều phối mua hàng cho rằng cách này không phản ánh đúng quán dùng nhiều thịt đắt.

### 3.7 Mua hàng và nhà cung cấp [F] (S3-U1–U4)

- **Khoảng 10 nhà cung cấp thường xuyên:** 2 thịt, 1 hải sản, 2 rau, 1 nhà phân phối bia, 1 nước ngọt, và các mặt hàng gạo, đồ khô, than, gas.
- **Tần suất:** thịt và rau giao gần như mỗi sáng; bia và đồ khô 2–3 lần/tuần.
- **Cách đặt và giá:**
  - Đặt qua Zalo hoặc điện thoại.
  - Một số giá chốt theo tháng; rau và hải sản đổi giá thường xuyên.
  - **Giá trên phiếu giao không phải lúc nào cũng khớp giá đã thoả thuận.**
- **Tính số lượng:** quản lý và bếp gửi đề xuất; Minh so với tồn "ước tính" và doanh số gần đây. **Không có tồn tối thiểu đáng tin.**
  - Thời gian giao: đồ tươi sáng hôm sau; phần bò đặc biệt hoặc bia bán chạy 1–2 ngày.
- **Mua gấp bằng tiền mặt:** quản lý mua ở tiệm gần, gửi ảnh hoá đơn cho kế toán. Minh biết muộn, dẫn tới **đặt trùng** (ví dụ nấm, 2 tuần trước).
- **Nhận hàng:**
  - Quản lý và đầu bếp đối chiếu phiếu giao với số lượng hoặc cân nặng, chất lượng, hạn dùng.
  - Thịt lạnh có kiểm tra độ lạnh nhưng **không ghi nhiệt độ**.
  - Thiếu hoặc kém chất lượng thì chụp ảnh gửi Zalo, Minh đòi nhà cung cấp đổi hàng hoặc giảm công nợ.
  - **Đôi khi ký nhận trước vì tài xế giục**, sau đó rất khó khiếu nại.
- **Thanh toán nhà cung cấp:**
  - Mua gấp trả tiền mặt.
  - Nhà cung cấp thường xuyên trả **theo tuần hoặc tháng** bằng chuyển khoản, do kế toán thực hiện sau khi Minh xác nhận bảng kê.
  - Công nợ theo dõi bằng Excel.
  - Nhà cung cấp lớn có HĐ VAT; nhà cung cấp rau nhỏ chứng từ không đầy đủ.
  - Minh không duyệt chi.

### 3.8 Bia và kiểm kho [F] (S3-U5, U6)

- **Bia:** nhà phân phối giao theo thùng, đôi khi kèm khuyến mãi.
  - **Két và vỏ chai trả lại** tính riêng, cũng phải đếm.
  - Bia để cạnh quầy. Chai đã mở được ghi nhận qua doanh số.
  - Đếm thùng nguyên và chai lẻ **mỗi tối**.
- **Giả thuyết cho khoản lệch 4,8 triệu:** chai khuyến mãi, bể vỡ, chuyển qua quán khác, số đầu kỳ đếm sai. Có thể bị lấy cắp, nhưng **không ai kết luận khi chưa có bằng chứng**.
- **Lịch kiểm kho:**
  - Bia và nước ngọt: mỗi tối khi đóng cửa.
  - Thịt cao cấp: khoảng 2 lần/tuần.
  - Kiểm toàn bộ: cuối tháng.
- **Cách kiểm:** quản lý hoặc đầu bếp đếm. Đơn vị mỗi loại một kiểu: thịt tính kg hoặc túi, bia tính thùng và chai, sốt tính hộp.
- **Khi lệch:** tìm phiếu giao bị thiếu, hàng chuyển quán, hàng dùng cho nhân viên, hàng hỏng, rồi gửi giải trình cho kế toán. Không giải thích được thì để là chênh lệch trên sheet của kế toán.
- **Nỗi khổ của mua hàng:** câu hỏi *"Đã nhận chưa, đã dùng chưa, hay chuyển quán khác rồi?"*. Tin nhắn Zalo trả lời được lúc đó, một tuần sau thì không truy lại được.

### 3.9 ~15 mặt hàng ưu tiên theo dõi chênh lệch [F] (S3-A1)

| Nhóm | Mặt hàng | Người đề xuất và lý do |
|---|---|---|
| Thịt | Phần bò, phần heo, gà | Bếp trưởng: giá trị cao, hao hụt thay đổi |
| Hải sản | Hải sản đắt tiền | Bếp trưởng |
| Bán thành phẩm | Nước lẩu cốt, 2 sốt chính, hộp ướp và sốt từ bếp sơ chế | Bếp trưởng, trưởng sơ chế: hộp bị thất lạc dễ bị hiểu nhầm là bếp dùng quá tay |
| Đồ uống | Bia theo nhãn (2 loại bán chạy nhất), nước ngọt chai, két vỏ | Điều phối mua hàng: đếm được chính xác |

Gạo và rau thường để giai đoạn sau.

### 3.10 Suy luận của BA [I]

- **I13:** Cần **phân luồng in hoặc hiển thị theo khu bếp** (nướng / xào-nồi đất / lẩu), có **màn hình tổng cho điều phối ra món**, và **gom món thêm vào ngữ cảnh của cả bàn**.
- **I14:** Mỗi món cần một cờ **"giữ lại, chờ gọi ra"** (hold/fire) với lệnh "ra món" rõ ràng, thay cho ghi chú "later".
- **I15:** Nếu món đã ở trạng thái "đang làm" mà vẫn muốn huỷ, **bếp phải xác nhận**. Lý do huỷ phải phân loại rõ: sai món, chất lượng, khách đổi ý, làm lại, hàng hỏng.
- **I16:** Dị ứng nên là **trường có cấu trúc** (chọn chất gây dị ứng), hiện nổi bật trên phiếu. Hệ thống chỉ ghi nhận và cảnh báo, **không cam kết an toàn**.
- **I17:** Cần **đơn vị tính chuẩn và bảng quy đổi** (túi, hộp ↔ kg). Cần **phiếu chuyển kho nội bộ** có xác nhận nhận hàng thay cho sổ tay và Zalo. Chi phí sơ chế chuyển theo **giá thành thực tế của phần được chuyển**.
- **I18:** Cần ghi nhận **tỷ lệ hao hụt khi sơ chế** theo lô, để định mức tính theo hao hụt thực tế thay vì một con số cố định.
- **I19:** Phiếu nhận hàng cần: số lượng đặt / nhận / chấp nhận, giá thoả thuận / giá trên phiếu (chênh lệch được đánh dấu), ảnh, nhiệt độ (tuỳ chọn). **Không cho xác nhận nhận hàng khi chưa nhập số kiểm.**
- **I20:** Mua gấp bằng tiền mặt phải nhập vào hệ thống (phiếu chi kèm ảnh hoá đơn) để bộ phận mua hàng thấy ngay.
- **I21:** Báo cáo chênh lệch nên hiện nguyên nhân theo **từng loại giao dịch** (bán, chuyển kho, hỏng, bữa nhân viên, khuyến mãi, bể vỡ) thay vì một con số "mất".
- **I22:** Công thức cần **phân quyền xem**: bếp trưởng và chủ xem được công thức chi tiết; người khác chỉ thấy tên món và thành phần gây dị ứng.

### 3.11 Câu hỏi mở sau Buổi 3 [?]

- Thống nhất cách tính phần vụn tái sử dụng (cho nước lẩu, bữa nhân viên) → bếp trưởng và kế toán.
- Có cần ghi nhiệt độ khi nhận thịt không (an toàn thực phẩm)? → chủ
- Quy tắc cho quán này mượn hàng quán kia? → chủ và kế toán

---

## Buổi 4 — Tài chính, kiểm soát, chính sách, vận hành, ưu tiên (kế toán Lê Mỹ Hạnh, chủ Mai Anh)

### 4.1 Pháp lý, thuế, hóa đơn [F] (S4-F1, F2)

- **Công ty TNHH Bếp Nhà & Nướng** có 3 địa điểm kinh doanh, nộp thuế GTGT theo **phương pháp khấu trừ**. Không phải hộ kinh doanh.
- **Thuế suất không đồng nhất:** đồ ăn thông thường được mức giảm; **bia và một số mặt hàng khác phân loại riêng**.
  - Kế toán gán loại thuế cho từng món, chủ duyệt giá bán.
  - Tư vấn thuế phải rà danh mục trước khi nạp vào hệ thống mới.
- **HĐĐT:** dùng **MISA meInvoice**, tách rời POS365.
  - Xuất **cho mọi giao dịch hoàn tất**; thêm thông tin công ty khi khách yêu cầu.
  - Hiện phải đối chiếu tay giữa tổng POS và hóa đơn.
- **Đơn app:** tiền khách trả, doanh thu, phí sàn, tiền về tài khoản là **4 con số khác nhau**.
- **Sai tên, huỷ, hoàn tiền:** kế toán xử lý trên meInvoice. **Thu ngân không được xoá hóa đơn đã phát hành** như xoá một bill chưa thanh toán.

### 4.2 Kênh tiền [F] (S4-F3)

- **Chuyển khoản:** **1 tài khoản thu chung Vietcombank** cho cả 3 quán. Mỗi quán có QR in sẵn riêng nhưng cùng về tài khoản này, nên giờ chuyển, số tiền và nội dung chuyển khoản rất quan trọng.
- **Thẻ:** mỗi quán một máy quẹt thẻ của ngân hàng. Tiền về **T+1 ngày làm việc**, lễ thì lâu hơn. Phí theo hợp đồng.
- **App giao hàng:**
  - GrabFood trả sau vài ngày làm việc; ShopeeFood có lịch riêng.
  - Bảng kê có: đơn, khuyến mãi, phí sàn, điều chỉnh, số thực nhận. Tên cột 2 app khác nhau.
- *"Tiền về tài khoản trong ngày không phải doanh thu món ăn của ngày đó."*

### 4.3 Quy trình khớp số mỗi sáng [F] (S4-F4)

1. Kiểm tra đủ phiếu chốt két của 3 quán và các ca có lệch tiền mặt chưa giải thích.
2. Xuất doanh thu POS theo quán và phương thức thanh toán.
3. So khớp từng loại:
   - tiền mặt ↔ số đếm có ký;
   - QR ↔ tiền về ngân hàng;
   - thẻ ↔ máy quẹt thẻ và bảng đối soát sau đó;
   - đơn app ↔ bảng đối soát app.
4. Rà hoàn tiền, giảm giá, món bị xoá.

- **"Khớp"** nghĩa là *giải thích được mỗi giao dịch bán và mỗi khoản tiền đúng một lần*, kể cả chênh lệch do thời điểm. **Không** phải ép tổng ngân hàng bằng tổng POS trong ngày.
- **Lỗi hay gặp:**
  - chuyển khoản không có mã bill;
  - thanh toán kết hợp bị ghi chung vào một phương thức;
  - khuyến mãi app ghi sai ngày;
  - **tiền cọc bị tính thêm lần nữa như doanh thu mới.**
- **Báo cáo đêm mong muốn, cho từng quán:**
  - doanh thu trước và sau giảm giá; thuế và số đã xuất HĐ;
  - thanh toán theo phương thức;
  - quỹ đầu ca, chi tiền mặt, tiền mặt dự kiến và thực đếm;
  - cọc đã nhận và đã cấn trừ; hoàn tiền;
  - chênh lệch chưa giải quyết.

### 4.4 Tiền cọc [F] (S4-F5, O2)

- **Hiện tại:** Lan ghi sổ và nhắn giao dịch cho kế toán. Kế toán giữ Excel riêng: khách, ngày, số tiền, ngày tiệc.
- **Nguyên tắc kế toán:** cọc **cấn trừ vào bill cuối, không được thành doanh thu lần hai**.
- **Cọc vào tài khoản cá nhân chủ:** phải chuyển ngay về tài khoản công ty, ghi rõ booking, giữ cả hai chứng từ.
- **Chủ quyết định từ nay:**
  - Cọc chỉ vào tài khoản công ty, nội dung chuyển khoản có **tên hoặc mã booking**. Chủ ngừng dùng QR cá nhân.
  - Huỷ **trước ít nhất 24 giờ** thì hoàn 100%.
  - Huỷ **dưới 24 giờ**: có thể giữ một phần nếu đã mua thực phẩm riêng cho tiệc. Mức cụ thể chưa chốt.
  - Quy định phải thống nhất cho cả 3 quán và báo khách lúc nhận đặt.
- **Kế toán không tự ghi nhận cọc không được nhận lại thành thu nhập.**

### 4.5 Chi tiền mặt từ két [F] (S4-F6)

- Ngoài quỹ đầu ca 1 triệu, mỗi quán có một khoản nhỏ cho chi phí gấp đã được duyệt.
- Quản lý thường chi 200–300k (rau, đá), chụp hoá đơn gửi kế toán.
- Khoản lớn hơn phải hỏi chủ trước, nhưng đôi khi hỏi sau khi đã mua.
- Kế toán khớp: hoá đơn ↔ ghi chú của quản lý ↔ tiền lấy khỏi két ↔ số đếm cuối ca.

### 4.6 Nhà cung cấp và phần mềm kế toán [F] (S4-F7)

- Hệ thống cần cho thấy hàng **đã đặt, đã nhận, bị từ chối, đã chuyển**, kèm chứng từ nhà cung cấp.
- **Công nợ nhà cung cấp nằm ở phần mềm kế toán MISA.** Không cần một số dư công nợ thứ hai có thể lệch với MISA.
- **Bản đầu:** file xuất đáng tin cậy (nhà cung cấp, số chứng từ, ngày, mặt hàng, số lượng, thuế, giá trị) có ích hơn một kết nối trực tiếp làm vội.

### 4.7 Chi phí bếp sơ chế [F] (S4-F8)

- **Tối thiểu:** tính cho mỗi quán **đúng phần quán đó nhận**, theo giá nguyên liệu sống đã điều chỉnh theo tỷ lệ thành phẩm đo được.
- **Muốn thêm:** phân bổ lương đội sơ chế và chi phí phòng sơ chế, có thể theo tháng.
- Không yêu cầu đo thời gian lao động cho từng hộp sốt trong giai đoạn thí điểm.

### 4.8 Lương và chấm công [F] (S4-F9)

- **Ngoài phạm vi dự án đầu.** Sau này có thể xuất danh sách ai làm ca nào, nhưng chưa dùng làm nguồn tính lương.

### 4.9 Báo cáo cần có [F] (S4-F10)

| Kỳ | Nội dung | Nguồn |
|---|---|---|
| Ngày | Doanh thu theo kênh và phương thức thanh toán; chốt két; giảm giá; huỷ món; hoàn tiền; cọc; ngoại lệ | Hệ thống mới |
| Tuần | Món bán chạy; mua hàng; biến động giá nhà cung cấp; tồn lý thuyết so với kiểm thực tế cho các mặt hàng chọn | Hệ thống mới |
| Tháng | Lãi lỗ theo quán (phí app, giá vốn, lương, thuê mặt bằng, phân bổ sơ chế) | **Hoàn thiện ở phần mềm kế toán.** Hệ thống mới chỉ cấp số liệu bán hàng và kho |

### 4.10 Kiểm soát và phân quyền [F] (S4-F11, O5)

- **Bắt buộc có lý do và tên người thực hiện** (một số việc cần thêm người duyệt):
  - xoá món đã xuống bếp;
  - giảm giá;
  - hoàn tiền;
  - đổi phương thức thanh toán sau khi đã chốt;
  - điều chỉnh kho;
  - sửa số đếm két đã hoàn tất.
- **Lưu trữ:** kế hoạch **ít nhất 10 năm** (tư vấn thuế xác nhận).
- **Phân quyền:**

| Vai trò | Được xem và làm | Không được |
|---|---|---|
| Phục vụ | Bàn mình phụ trách, trạng thái món | Giá vốn, doanh thu tổng |
| Thu ngân | Bill, thanh toán, ca của mình | |
| Bếp | Order bếp, kho và công thức mình quản lý | Chứng từ ngân hàng |
| Quản lý quán | Vận hành quán mình; duyệt xử lý sự cố thường; doanh thu và kho quán mình | Lợi nhuận, lương của quán khác |
| Mua hàng (Minh) | Mua hàng, giao nhận mọi quán | |
| Kế toán (Hạnh) | Tài chính và hóa đơn mọi quán | **Sửa order bếp** |
| Chủ | Tất cả | |

### 4.11 Chính sách giảm giá và huỷ món [F] (S4-O1)

- Quản lý tự xử lý khiếu nại thông thường **tối đa 5% hoặc 150.000đ/bill** mà không cần gọi chủ.
- Huỷ món đã nấu hoặc đã mang ra phải có **lý do và tên quản lý**.
- Vượt mức thì chuyển chủ duyệt, **duyệt trên điện thoại**.
- **Danh mục lý do:** sai order; khách đổi ý trước khi nấu; lỗi chất lượng bếp; chờ lâu; khuyến mãi; lỗi nhân viên. Không chấp nhận "khác" tràn lan.
- **Phương án dự phòng khi chủ không trả lời sau vài phút:** với lỗi phục vụ thật, quản lý được xử lý **tới 300.000đ khi có người thứ hai xác nhận**, báo chủ trong tối.
- ⚠️ **Xung đột:** kế toán cho rằng 300k là quá cao nếu chưa biết tần suất và nguyên nhân.

### 4.12 Thực đơn và giá [F] (S4-O3)

- **Thực đơn:** menu lõi dùng chung. Vài món đặc biệt riêng từng quán. Quán có sân thượng hoặc sân trong bán nhiều set nướng hơn.
- **Quyền:** chủ duyệt món và giá; bếp đề xuất món; quản lý đánh dấu hết món. **Thu ngân không được đổi giá tại quầy.**
- **Giá app:** khác theo từng món, không theo một tỷ lệ cố định.
- **Giá ngày lễ:** bảng giá lễ có ngày hiệu lực, công bố trước, **ghi rõ kênh áp dụng**.
- **Hiện tại:** sửa giá riêng trên từng POS và từng app, dễ sót.

### 4.13 Khách hàng [F] (S4-O4)

- Nhận ra khách quen qua tên hoặc số điện thoại; thỉnh thoảng gửi ưu đãi sinh nhật thủ công.
- Muốn **một danh sách khách chung cho cả chuỗi**, sau đó có thể tích điểm hoặc voucher đơn giản. **Không tung chương trình thân thiết lớn vào tháng 1.**
- **Chưa có sự đồng ý nhận tin tiếp thị:** phải hỏi trước khi gửi Zalo hoặc SMS và cho khách từ chối nhận.

### 4.14 Điện thoại của chủ buổi tối [F] (S4-O6)

- **Trước khi ngủ muốn xem, cho từng quán:** doanh thu, lệch két, tổng QR và thẻ, đơn app, giảm giá, huỷ món, việc chưa giải quyết.
- **Cảnh báo ngay khi:**
  - lệch tiền mặt > 200.000đ;
  - huỷ món đã phục vụ > 100.000đ;
  - quản lý dùng hạn mức dự phòng;
  - **mất kết nối > ~15 phút trong giờ phục vụ.**
- Không báo cho mỗi món ăn kèm được sửa.

### 4.15 Thiết bị và vận hành [F] (S4-O7, O8)

- **Thiết bị:**
  - Đống Đa tối thứ 7 cần khoảng **4 máy cầm tay cho phục vụ, thêm 1 máy dự phòng hoặc cho quản lý** (Lan sẽ thử).
  - Gần pass có chỗ và nguồn điện cho màn hình. Bếp trưởng muốn giữ phiếu in khi thử.
  - Thêm máy in theo khu cần khảo sát chỗ đặt và dây điện.
  - **Không có UPS, không có Internet dự phòng.** Phát wifi từ điện thoại không phải phương án tin cậy.
  - **Dùng thiết bị của quán, không dùng điện thoại cá nhân.**
- **Hỗ trợ:** cần liên lạc được tới ~23:30. Khi ngừng gọi món hoặc thanh toán thì **phản hồi trong ~15 phút**; lỗi phần cứng có thể lâu hơn.
- **Người phụ trách:**
  - Lan: vận hành hằng ngày ở quán thí điểm.
  - Minh: dữ liệu mặt hàng và nhà cung cấp, cùng bếp.
  - Hạnh: kiểm tra thanh toán và hóa đơn.
- **Dữ liệu:** công ty sở hữu và xuất được. **Ưu tiên hosting tại Việt Nam.**

### 4.16 Tương lai [F] (S4-O9)

- Có thể mở **quán thứ 4 năm 2027**, chưa ký. Không nhượng quyền. Có thể cần chỗ sơ chế lớn hơn.
- Khách tự gọi món bằng QR tại bàn và website đặt món riêng: có quan tâm nhưng **không dùng tiền của đợt thí điểm**.
- **Camera nhận diện từng đĩa ra khỏi bếp:** chủ tự nhận là đắt, **không phải yêu cầu nghiêm túc giai đoạn đầu**.

### 4.17 Ưu tiên MoSCoW do chủ xếp [F] (S4-O10)

| Năng lực | Thí điểm Đống Đa | Triển khai toàn chuỗi |
|---|---|---|
| a. Sơ đồ bàn và trạng thái | Must | Must |
| b. Gọi món trên máy cầm tay, đi thẳng xuống bếp và bill | Must | Must |
| c. Phiếu bếp và trạng thái món | Must | Must |
| d. Giữ món / gọi ra món | Should | Must |
| e. Huỷ, giảm giá, lịch sử | Must | Must |
| f. Tách, gộp bill; chuyển bàn | Must | Must |
| g. QR theo bill, tự xác nhận | Could | Should |
| h. Thẻ, ví, thanh toán kết hợp | Must | Must |
| i. Đếm két và giao ca | Must | Must |
| j. Chạy khi mất Internet | Must | Must |
| k. Đặt bàn và cọc | Should | Must |
| l. Danh sách chờ | Could | Should |
| m. Đơn app trong hệ thống | Must (nhân viên nhập tay) | Must; kết nối trực tiếp là Could |
| n. Quản lý hết món | Must cho sảnh; app cập nhật tay | Must cho sảnh; liên kết app là Should |
| o. Xuất HĐĐT từ hệ thống | Could (giữ nhà cung cấp hiện tại) | Should (qua kết nối nhà cung cấp đã kiểm chứng) |
| p. Định lượng và tiêu hao lý thuyết | Should (một số mặt hàng) | Must (mặt hàng ưu tiên) |
| q. Sản xuất sơ chế và chuyển kho | Should | Must |
| r. Mua hàng: đề xuất → nhận hàng | Could | Should |
| s. Kiểm kê và chênh lệch ~15 mặt hàng | Should | Must |
| t. Dashboard trên điện thoại cùng đêm | Must | Must |
| u. Khớp thanh toán | Should | Must (chấp nhận tiền app về muộn) |
| v. Quản lý menu và giá tập trung | Should | Must |
| w. Danh sách khách và tích điểm | Could (danh sách cơ bản) | Should (ưu đãi hoặc điểm đơn giản) |
| x. Chấm công và lương | Won't | Won't |
| y. Khách tự gọi món bằng QR tại bàn | Won't | Won't |
| z. Xuất sang phần mềm kế toán | Should | Must |

- "Could" của HĐĐT trong thí điểm **không có nghĩa là ngừng xuất hóa đơn**: quy trình hiện tại của kế toán vẫn tiếp tục.
- Đơn app phải có trong số liệu, nhưng không bắt buộc kết nối trực tiếp với app ngay tháng 1.

### 4.18 Rủi ro do khách tự nêu [F] (S4-Z1)

- **Dữ liệu gốc bẩn:** tên món khác nhau giữa các quán và các app, đơn vị kho thiếu, số điện thoại khách trùng. Nếu chuyển sang hệ thống mà không làm sạch thì *báo cáo đẹp vẫn sai*.
- **Chưa thống nhất cách hạch toán** tiền cọc, phí app, chi phí sơ chế, nên chưa thể tin vào số lợi nhuận.
- **Nhân viên có thể quay lại dùng giấy** nếu gọi món lúc 19:30 chậm hơn giấy. Cần Lan và Đức tham gia thử ở một buổi tối thật.
- **Kết nối app hoặc ngân hàng có thể không khả thi về thương mại.** Chủ muốn được báo sớm, không để thành bất ngờ trước tháng 3.

### 4.19 Suy luận của BA [I]

- **I23:** Hạn mức "5% hoặc 150k" chưa rõ lấy mức nào. BA đề xuất lấy **mức thấp hơn** và đưa ra xác nhận ở Buổi 5.
- **I24:** Mục tiêu G2 (khớp số dưới 1 giờ) phụ thuộc nhiều vào **QR theo bill**. Chủ đang xếp QR là Could cho thí điểm, **mâu thuẫn ngầm với G2**. BA đề xuất nâng lên Should.
- **I25:** Tách **"bill" (hoá đơn tạm tính trong hệ thống)** khỏi **"HĐĐT" (chứng từ thuế)**: hai đối tượng có vòng đời và quyền khác nhau.
- **I26:** Tiền cọc là **khoản nhận trước**, không phải doanh thu. Cần sổ cọc có trạng thái: nhận → cấn trừ / hoàn / giữ lại (theo quyết định của chủ).
- **I27:** Tài khoản thu chung cho 3 quán, nên **nội dung chuyển khoản phải chứa mã quán và mã bill** thì mới tự khớp được.
- **I28:** Tiêu chí offline N2 bắt buộc có **UPS** cho router, máy chủ tại quán và máy in. Đây là chi phí phần cứng cần đưa vào quyết định.

---

## Buổi 5 — Xác nhận yêu cầu (gói xác nhận gửi khách)

Khách **không ký nguyên văn**. Khách đồng ý **hướng đi và thứ tự ưu tiên đã sửa**. Các điểm được sửa hoặc chốt:

| Chủ đề | Nội dung được sửa hoặc chốt | Nguồn |
|---|---|---|
| G2 | Đích < 1 giờ/ngày áp dụng **sau khi triển khai toàn chuỗi**, không phải cho thí điểm. Đo **thời gian thao tác tay** | S5-A |
| G3 | Ghi cả món **trùng**, kể cả lỗi phát hiện trước khi khách phàn nàn | S5-A |
| G4 | So **cùng đơn vị**, đã tính hao hụt sơ chế đo được và hao hỏng đã ghi. Bếp sẽ phản đối nếu số đầu kỳ đếm sai | S5-A |
| G5 | Được dùng giấy khi có sự cố thật. Mục tiêu là tối thứ 6 bình thường nhân viên **tự chọn** dùng thiết bị | S5-A |
| Phương án | PA2 (hệ thống riêng) **có điều kiện**: kiểm tra kỹ thuật và giá 2 tuần, **thử phần mềm có sẵn cho phần sảnh**; không trả tiền xây lại phần gọi món nếu có sản phẩm đáp ứng | S5-B, D5 |
| Phiếu bếp | Giữ phiếu in trong thí điểm; màn hình chỉ hỗ trợ. Đơn app phải có **mã đơn app** để phát hiện nhập trùng | S5-C1 |
| HĐĐT | meInvoice là **quy trình đang chạy**, không phải tính năng "Could". Thí điểm phải cấp đủ dữ liệu bán hàng và người mua cho kế toán | S5-C3 |
| QR theo bill | Nâng lên **Should** cho thí điểm, tuỳ ngân hàng có cho truy cập không. Nếu chưa tự động được thì vẫn phải có mã bill và cách khớp thủ công rõ ràng | S5-C6, D6 |
| BR1 | Hạn mức quản lý: **≤ 10% bill, trần 150.000đ/bill**. Xoá món tính nhầm **không tính** vào hạn mức | S5-BR1 |
| BR2 | Thêm lý do **"tính trùng"**. "Khác" phải ghi chú | S5-BR2 |
| BR3 | Trước khi gửi bếp: sửa tự do. **Sau khi phiếu tới bếp (dù chưa nấu):** quản lý duyệt, bếp xác nhận đã thấy thay đổi | S5-BR3 |
| BR7 | Thu ngân xác nhận được tiền chuyển khoản **thật** (số tiền và mã tham chiếu) sau khi kiểm tra app ngân hàng. Quản lý duyệt trường hợp không chắc hoặc ghi đè sau đó | S5-BR7 |
| BR12 | Bắt buộc nhập số lượng kiểm đếm. Ảnh không chặn việc ghi nhận, được bổ sung sau | S5-BR12 |
| BR13 | Ghi được hàng **rò, hỏng**. **Không tính chi phí** cho quán nhận khi đang tranh chấp hoặc chưa nhận | S5-BR13 |
| BR14 | Lưu **nguyên văn lời khách** cùng danh sách chất gây dị ứng. Bếp xác nhận nghĩa là đã xem, **không** chứng nhận an toàn | S5-BR14 |
| N1 | 4 chạm, 10 giây là **mục tiêu thử nghiệm**, chưa phải ngưỡng nghiệm thu | S5-N1 |
| N2 | Offline: thẻ và QR chỉ ghi "dự định", **không được coi là đã trả** | S5-N2 |
| N5 | Hosting tại Việt Nam là **ưu tiên**, cần báo giá. Bảo vệ dữ liệu cá nhân là **bắt buộc** | S5-N5 |
| N6 | Không tính phí cho 20 thiết bị mỗi quán. Đống Đa cần ~5 máy cầm tay, cộng quầy và bếp | S5-N6 |
| N8 | 8–12 triệu/tháng chỉ là **chi phí cố định cho hosting và hỗ trợ**. Chi phí biến đổi tách riêng và phải được ước tính | S5-N8 |
| D1 | **Trần theo ca 600.000đ** cho mỗi quản lý | S5-D1 |
| D2 | Chủ không trả lời **sau 3 phút** thì quản lý được xử lý ≤ 300.000đ khi thu ngân hoặc bếp trưởng xác nhận. Cảnh báo chủ ngay. Kế toán rà từng trường hợp trong thí điểm | S5-D2 |
| D3 | Huỷ ≥ 24h: hoàn 100%. Huỷ < 24h hoặc không tới: **giữ 50%**, trừ khi dời booking trong 7 ngày. Nếu không chứng minh được đã báo điều khoản cho khách thì **hoàn đủ** | S5-D3 |
| D4 | Chi tiền mặt từ két ≤ 300.000đ. Vượt mức thì gọi chủ trước, trừ trường hợp khẩn cấp (phải ghi lý do) | S5-D4 |
| D7, D8 | Báo giá UPS và 4G cho từng quán. Khảo sát hiện trường bếp; **chưa chốt** số máy in và màn hình | S5-D7, D8 |
| D9 | Dữ liệu thí điểm xong **giữa tháng 12**. Dữ liệu hai quán còn lại xong **cuối tháng 1** | S5-D9 |
| D10 | Lan ghi sổ lỗi món; Minh và Thảo ghi kiểm kê và luân chuyển; Hạnh bấm giờ khớp số. **Bắt đầu từ tháng 10** | S5-D10 |
| Bổ sung | Cần thời gian **đào tạo part-time** và **kịch bản khôi phục khi thiết bị hoặc máy in hỏng tối thứ 6** | S5 sign-off |

## Buổi 6 — Xác nhận baseline đã sửa

- Khách **xác nhận baseline yêu cầu**. Việc duyệt xây dựng còn chờ **kiểm tra kỹ thuật 2 tuần có chi phí**. [F]
- **Làm rõ thêm:**
  - Xác nhận của bếp phải **hiển thị cho quản lý**.
  - Ngoại lệ chi khẩn cấp phải ghi **vì sao không duyệt trước được**.
  - Khi chốt ca phải thấy các khoản "dự định, chưa xác nhận".
  - Buổi thử phải **tập khôi phục**, gồm cả phiếu đã in ngay trước khi mất mạng.
  - Đào tạo cả **part-time chỉ làm cuối tuần**.
  - Sau khôi phục: **1 người đối chiếu phiếu giấy, quản lý ký trước khi chốt ca**.
- **p, u, v, z vẫn là Must cho tháng 3:**
  - p: định lượng cho **mặt hàng ưu tiên**.
  - u: khớp tiền mặt, ngân hàng, thẻ; tiền app nhập bảng kê thủ công cũng được.
  - v: một menu có kiểm soát, giá theo quán, theo kênh, bảng giá lễ có ngày hiệu lực.
  - z: file xuất đã được kế toán **thử với MISA**.
- **Nhiệt độ khi nhận hàng:** ghi nhiệt độ đo được, giờ nhận và vấn đề chất lượng cho **thịt lạnh và hải sản**. **Ngưỡng chấp nhận chưa chốt** (Minh và Đức sẽ thống nhất).

## Buổi 7 — Khoảng trống về kế hoạch

- **Tết:**
  - Từ nửa cuối tháng 1 tới trước Tết là cao điểm tất niên. Quán dự kiến **nghỉ 5–9/02/2027**, mở lại ngày 10 với menu rút gọn.
  - **Go-live thí điểm dời sang khoảng 22/02/2027**; tháng 1 chỉ tập dượt.
  - Toàn chuỗi vẫn nhắm cuối tháng 3, nhưng **không ép** nếu thí điểm lộ lỗi nghiêm trọng.
- **Phỏng vấn thêm:** gặp quản lý **Thanh** (Cầu Giấy) và **Quyên** (Hai Bà Trưng) trước cuối tháng 10. **Không sao chép sơ đồ bàn** của Đống Đa sang.
- **Làm tròn:**
  - **Không làm tròn từng món hay từng phần chia.** Tổng các phần phải bằng tổng bill.
  - Chuyển khoản và thẻ thanh toán **đúng số tiền**.
  - **Chỉ tiền mặt** được làm tròn **xuống** tới 1.000đ; quán chịu tối đa 999đ.
  - Khoản làm tròn **ghi rõ trước khi xuất HĐ**, không ghi thành lệch két.

## Buổi 8 — Quản lý Cầu Giấy (Vũ Minh Thanh) và Hai Bà Trưng (Đỗ Ngọc Quyên)

### 8.1 Cầu Giấy [F]

- **Sự cố tháng 8** (tối thứ 7, sau 19:00, kéo dài khoảng 30 phút):
  - Trong lúc mất mạng: phục vụ đánh số phiếu giấy và mang thẳng vào bếp; món thêm ghi "addition". Tiền mặt và thẻ vẫn thu được. Khách chuyển khoản phải chờ kiểm tra ngân hàng bằng 4G.
  - **Lỗi khi có mạng lại:**
    - Thu ngân nhập bù phiếu giấy vào POS365 thì **máy in bếp in lại phiếu**, dẫn tới **2 món bị nấu lần hai**, 1 món bỏ đi.
    - Quản lý mất thêm 1 giờ sau khi đóng cửa để đối chiếu.
- **Hạ tầng:**
  - Mất mạng khoảng 2 lần/tháng, mỗi lần 15–40 phút; có vẻ do **đường Internet**, không phải mất điện.
  - **Wi-Fi yếu ở bàn mặt tiền.**
  - Router và POS nằm sau quầy, khó với tới. Máy in bếp nối mạng LAN. Chưa có 4G và UPS.
- **Vận hành:**
  - ~80 chỗ. **Khu quây là 4 bàn thường**; nếu một nhóm giữ khu này thì mất chỗ cho khách lẻ. Khách ở bàn mặt tiền hay xin chuyển vào trong.
  - Đơn app có thể chiếm **1/5–1/4 doanh thu** một số ngày thường (kế toán cần kiểm tra). Shipper đứng ở cửa; phục vụ phải chọn giữa đón khách và trả lời tablet app.
- **Cần riêng:**
  - Biết **phiếu giấy nào đã tới bếp, phiếu nào chỉ nhập bù để tính tiền**.
  - 4 bàn khu quây hiển thị **riêng lẻ nhưng đặt chung được**, không phải một "phòng" cố định.

### 8.2 Hai Bà Trưng [F]

- 22 bàn, gồm **phòng riêng 3 bàn** và **sân thượng 4 bàn**.
- **Phòng riêng:**
  - Không tính tiền phòng mà tính **mức chi tối thiểu**: khoảng 3 triệu buổi trưa, 5 triệu tối cuối tuần đông. Quản lý chốt từng booking.
  - Nhóm từ 8 người cọc 500k–2tr.
  - Set đặt trước: gửi ảnh menu cho khách, ghi lựa chọn vào sổ.
  - Chia theo buổi trưa hoặc tối, không chia khung 2 giờ. Khách được mang đồ trang trí, không thu phí.
- **Lỗi hay gặp:**
  - Số khách thay đổi (12 thành 17).
  - Khách đổi set nhưng chỉ báo một nhân viên, bếp vẫn làm theo danh sách cũ.
  - Thông tin set và cọc nằm rải rác ở nhiều tin nhắn Zalo.
- **Chuyển bàn từ sân thượng:**
  - Cả nhóm thường chuyển sang **nhiều bàn** trong nhà, không phải 1 bàn tương ứng.
  - Ví dụ: 10 khách từ 2 bàn sân thượng sang 3 bàn trong nhà. Bill đầu nằm ở bàn sân thượng, bia gọi sau lại vào bàn trong nhà; thu ngân phải **ghép bill bằng tay**.
- **Tiệc lớn 20–40 người:**
  - Phòng riêng cộng bàn lân cận. Chọn trước 2–3 set, **định giờ ra đồ nướng và lẩu**. Bếp trưởng kiểm tra số lượng từ hôm trước.
  - **Nhiều bên trả tiền:** công ty trả đồ ăn và lấy 1 HĐ, khách tự trả bia; hoặc 2 phòng ban lấy 2 HĐ với thông tin công ty khác nhau.
  - Cần chỉ dẫn của kế toán **trước khi tiệc bắt đầu**. Chia set dùng chung cho nhiều công ty vào phút chót rất khó.
- **Cần riêng:**
  - Một sự kiện chiếm nhiều bàn nhưng từng bàn vẫn hiển thị riêng.
  - Mức chi tối thiểu và ghi chú đặt trước hiện cho quản lý và thu ngân.
  - Mỗi booking có mức cọc và set riêng.

### 8.3 Cả hai quản lý [F]

- **Trong ca:** bàn đang mở; order **chưa tới bếp**; món chờ lâu; món hết; thanh toán chưa xác nhận. Quyên cần thêm: tiệc sắp tới, cọc, set đặt trước, bàn được giữ.
- **Cuối ngày:**
  - Doanh thu quán mình theo phương thức; tiền mặt đếm so với dự kiến; danh sách order và chuyển khoản chưa giải quyết.
  - Quyên cần thêm: kiểm tra **mọi bàn đã chuyển và mọi order thêm của tiệc đã nằm đúng bill**.
- **Lo ngại:**
  - Thanh: sự cố thật đầu tiên xảy ra khi người đào tạo đã về, nên cần **cả đội tập** quy trình giấy và khôi phục.
  - Quyên: sao chép cấu hình Đống Đa rồi mới phát hiện không đáp ứng được tiệc lớn, nên cần **thử một booking tiệc thật của Hai Bà Trưng** trước khi coi là sẵn sàng.

### 8.4 Suy luận của BA [I]

- **I29:** Phiếu nhập bù sau sự cố phải có cờ **"đã phục vụ, không gửi bếp"** và để **mặc định** là không gửi bếp. Đây là quy tắc đề xuất BR-34, cần khách xác nhận.
- **I30:** Máy chủ tại quán (edge) giữ LAN chạy khi đứt Internet. Như vậy **phần lớn sự cố ở Cầu Giấy không còn cần tới giấy**. Giấy chỉ còn cho trường hợp mất điện hoặc hỏng máy chủ tại quán. Cần **khảo sát Wi-Fi** cho bàn mặt tiền.
- **I31:** Cần hai khái niệm tách riêng: **nhóm bàn** (các bàn ghép cho một lượt ăn hoặc một booking) và **bill** (thanh toán). Một nhóm bàn có thể có nhiều bill, một bill có thể gồm nhiều bàn. Chuyển bàn phải giữ nguyên bill.
- **I32:** Cần đối tượng **Sự kiện/Tiệc** gồm: nhiều bàn, số khách có lịch sử thay đổi, mức chi tối thiểu, set đặt trước kèm giờ ra món, danh sách chuẩn bị cho bếp, **nhiều bên trả tiền, mỗi bên có thông tin hóa đơn riêng**.

---

## CR-01 — Khảo sát thực tế và yêu cầu thay đổi của chủ

- **Codex khảo sát** (có nguồn): [04-codex-khao-sat-qr-thanh-toan.en.md](04-codex-khao-sat-qr-thanh-toan.en.md). Tóm tắt ở [CR-01 §2](../05-kiem-soat-chat-luong/03-yeu-cau-thay-doi-CR01.md).
- **Chủ đổi ý** sau khi quan sát các tối thứ 6: khách thường muốn gọi thêm thịt hoặc đồ uống lúc phục vụ đang bận. Chủ muốn khách **quét QR để gọi thêm**, **xem món tới đâu**, **tự trả bằng chuyển khoản**. [F]
- **Điều chủ lo:**
  - mất sự phục vụ tận tình;
  - dị ứng hoặc đơn lạ đi thẳng vào bếp;
  - điện thoại báo "đã phục vụ" khi món còn trong bếp;
  - khách thấy app ngân hàng báo "đã trả" trong khi quầy chưa nhận được.
- Chủ **không** yêu cầu làm cả ba trước 22/02; muốn biết cái gì thử an toàn được và chi phí thêm bao nhiêu. [F]

## Buổi 9 — Hội thảo tác động CR-01 (chủ, Lan, Đức, Huy, Hạnh; Thanh và Quyên qua điện thoại)

| Người | Điểm chính [F] |
|---|---|
| Mai Anh | Thử **6 bàn** Đống Đa, trưa rồi một tối đông. Nhân viên nhận order đầu, khách dùng QR để **gọi thêm**. Mục tiêu: ~1/4 món gọi thêm qua QR; "tính tiền" tới xác nhận ~3 phút. **Trần ngân sách 600 triệu**; CR-01 thêm 50–80 triệu một lần và 1–2 triệu/tháng. Số điện thoại tuỳ chọn, đồng ý tiếp thị tách riêng |
| Lan | Bàn phải **do nhân viên mở**; **xác nhận mọi đơn QR** (mục tiêu ~1 phút). Luôn giữ bia, dị ứng, số lượng lớn, set lẩu đổi, món "ra sau". Muốn **mã ngồi bàn** trên thẻ. Mỗi người gọi từ điện thoại riêng vào **một bill**, không có giỏ chung; nghi trùng thì tô nổi, không tự xoá. Khách chỉ rút lại khi đơn còn chờ. Cần nút gọi nhân viên, yêu cầu tính tiền |
| Đức | Chỉ hiện trạng thái trung thực: **"Bếp đã nhận"** và **"Đã phục vụ"**; **"Xong"** chỉ khi pass bấm đều; **không "đang nấu"**. Dị ứng phải giữ đơn và nói chuyện với khách. Nhiều món một lúc thì nhân viên điều nhịp. Món ăn dừng lúc **21:45** |
| Huy | Chỉ trả **cả bill đã chốt**; tách bill qua quầy. Cần thấy rõ: *đã mở lệnh, chờ xác nhận, đã xác nhận, tiền về nhưng không khớp*. Trả thiếu thì hiện "trả một phần". Khách rời đi khi còn chờ thì bill vẫn mở. Hiện cọc và giảm giá trên bill |
| Hạnh | **Hỏi Vietcombank trước**, sau đó mới xem payOS, SePay, Casso: dữ liệu, lưu trữ, thu hồi quyền, chữ ký thông báo. **Không đưa mật khẩu ngân hàng.** Thông tin HĐ khách nhập là tuỳ chọn, kế toán kiểm tra. Hoàn tiền về tài khoản gốc, mục tiêu ngày làm việc kế tiếp |
| Thanh | QR **tạm dừng** khi mất mạng, nhân viên quay lại máy cầm tay hoặc phiếu giấy; **không dồn đơn** gửi sau khi có mạng lại |
| Quyên | Tiệc phòng riêng: chỉ gọi **thêm** sau khi xác nhận set; thanh toán tiệc qua thu ngân. Chuyển bàn khi mưa thì QR phải đi theo bill |
| Rủi ro | **QR giả dán đè** (Lan); khách gọi nhanh hơn sức bếp (Đức); khách trả lần hai khi xác nhận chậm (Huy); thí điểm tháng 2 quá tải (Mai Anh) |

## Buổi 10 — Xác nhận CR-01

- Khách **duyệt đưa CR-01 vào thiết kế, có sửa**. **Chưa** duyệt mua dịch vụ hay xây: còn chờ kiểm tra kỹ thuật và báo giá tổng mới. [F]
- **Điểm sửa:**
  - Mã ngồi bàn **bắt buộc ngay từ QR-1** (BA đề xuất để tới QR-2).
  - "Xong" chỉ hiện nếu pass bấm đều.
  - Đồ uống chai được gọi tới ~22:15.
  - Quản lý tạm dừng QR mà không đóng bill.
  - Tiền về sai mã thì không đoán bàn.
  - Hoàn tiền trong ngày làm việc kế tiếp là mục tiêu, không phải cam kết. [F]
- Chi tiết: [CR-01 §3](../05-kiem-soat-chat-luong/03-yeu-cau-thay-doi-CR01.md), BR-41 … BR-60.

### Suy luận của BA [I]

- **I33:** Trang khách chạy trên mạng di động nên **đi qua cloud**, còn **máy chủ tại quán vẫn là nơi ghi duy nhất**. Khi cloud mất kết nối với quán, phải **từ chối ngay** chứ không xếp hàng (BR-55).
- **I34:** "Trạng thái trung thực" đòi hỏi **dữ liệu từ thao tác thật** (pass, người chạy món). Vì vậy trạng thái hiển thị cho khách phải **cấu hình được** theo độ tin cậy đo trong buổi thử.
- **I35:** Tự thanh toán dùng **lệnh thanh toán có mã tham chiếu duy nhất** và **chỉ tin thông báo máy chủ có chữ ký**. Làm như vậy giải quyết được cùng lúc bài toán tài khoản thu chung cho 3 quán và các giao dịch trùng số tiền.
