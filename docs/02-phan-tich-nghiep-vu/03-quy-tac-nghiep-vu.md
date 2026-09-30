# BRD — Phần 3: Sổ quy tắc nghiệp vụ (Business Rules Register)

> Phương pháp: skill `business-rule-extractor`. Mỗi quy tắc viết thành một câu **có điều kiện, kiểm thử được**.
> **Độ tin cậy:**
> - ✅ **Đã xác nhận**: khách chốt trong buổi xác nhận (Buổi 5–7).
> - 🟡 **Khách nêu**: khách nói ra nhưng chưa đưa vào buổi xác nhận.
> - 🔵 **Đề xuất**: BA suy luận, **cần khách xác nhận**.
>
> **Tham số** (số tiền, thời gian) phải **cấu hình được** trong hệ thống (FR-ADM-06), không viết cứng trong mã nguồn.

## 1. Giảm giá, tặng món, huỷ món

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-01 | NẾU quản lý giảm giá hoặc tặng món cho một khiếu nại thông thường THÌ tổng mức giảm trên bill ≤ **10% giá trị bill** và ≤ **150.000đ/bill**, và phải chọn lý do | S5-BR1, S6-R1 | ✅ | Quản lý | Vượt mức: xem BR-02, BR-03 |
| BR-02 | NẾU tổng giảm giá/tặng món do khiếu nại thông thường của **một quản lý trong một ca** vượt **600.000đ** THÌ các lần tiếp theo phải có **chủ duyệt** | S5-D1, S6-R1 | ✅ | Quản lý | Trừ BR-03. Xem lại sau thí điểm |
| BR-03 | NẾU yêu cầu duyệt gửi chủ **không được phản hồi sau 3 phút** VÀ khách đang chờ VÀ đây là **lỗi phục vụ thật** THÌ quản lý được xử lý tới **300.000đ** khi có **thu ngân hoặc bếp trưởng** xác nhận sự việc; hệ thống **cảnh báo chủ ngay** | S5-D2, S6-R3 | ✅ | Quản lý | **Không phải hạn mức giảm giá thứ hai.** Kế toán rà từng trường hợp trong thí điểm |
| BR-04 | NẾU xoá một món bị **tính nhầm** (tính trùng, sai món) THÌ **không** tính vào hạn mức BR-01, BR-02 | S5-BR1 | ✅ | Thu ngân, quản lý | Vẫn phải có lý do (BR-05) và người duyệt nếu món đã tới bếp (BR-07) |
| BR-05 | Mỗi lần giảm giá, tặng món, huỷ món, hoàn tiền phải chọn **một lý do trong danh mục**: sai order; khách đổi ý trước khi nấu; lỗi chất lượng bếp; chờ lâu; khuyến mãi; lỗi nhân viên; **tính trùng**; khác | S4-O1, S5-BR2 | ✅ | Mọi vai trò | "Khác" **bắt buộc ghi chú** và được đánh dấu trong báo cáo |
| BR-06 | NẾU order **chưa gửi bếp** THÌ phục vụ được sửa hoặc xoá món tự do, không cần duyệt | S5-BR3 | ✅ | Phục vụ | — |
| BR-07 | NẾU phiếu **đã tới bếp** (dù chưa nấu) THÌ huỷ hoặc đổi món cần **quản lý duyệt** và lý do, **và** bếp phải **xác nhận đã thấy thay đổi**; quản lý thấy được trạng thái xác nhận | S5-BR3, S6-R2 | ✅ | Phục vụ, quản lý, bếp | Bếp xác nhận nghĩa là "đã thấy", **không đảm bảo** cứu được nguyên liệu |
| BR-08 | NẾU khách phàn nàn chất lượng **sau khi món đã mang ra** THÌ quản lý xử lý trong hạn mức BR-01, sau đó ghi nhận sự việc (món, lý do, cách xử lý) | S5-BR3 | ✅ | Quản lý | Làm lại món: ghi remake hoặc bỏ đi (FR-KIT-07) |
| BR-09 | **Bữa ăn nhân viên** được ghi riêng (kể cả khi nấu từ nguyên liệu, không theo món trong menu) và **không** được ghi là giảm giá cho khách | S2-M5, S5-BR4 | ✅ | Bếp, quản lý | — |

## 2. Đặt bàn, sự kiện, tiền cọc

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-10 | Tiền cọc **chỉ** nhận vào **tài khoản công ty**, nội dung chuyển khoản chứa **mã hoặc tên booking**. Cọc là **tiền giữ cho một booking có tên**, được **cấn trừ vào số phải trả** của bill cuối, **không bao giờ** ghi nhận là doanh thu | S4-F5, O2, S5-BR5 | ✅ | Quản lý, thu ngân, kế toán | Xem BR-12 |
| BR-11 | Huỷ **≥ 24 giờ** trước giờ đặt: **hoàn 100%**. Huỷ **< 24 giờ** hoặc **không tới**: **giữ 50%**, hoàn phần còn lại, **trừ khi** dời booking trong **7 ngày**. NẾU **không chứng minh được** khách đã nhận điều khoản lúc đặt THÌ **hoàn 100%** | S5-D3, S6-R4 | ✅ | Quản lý, kế toán | **Kế toán duyệt câu chữ và cách hạch toán trước khi áp dụng.** Lưu tin nhắn xác nhận booking và kết quả (hoàn, giữ, chuyển booking) |
| BR-12 | NẾU tiền cọc vào **tài khoản cá nhân** của chủ THÌ phải **chuyển ngay** về tài khoản công ty, gắn đúng booking, **giữ cả hai chứng từ** | S4-F5 | ✅ (kế toán yêu cầu) | Chủ, kế toán | Chủ cam kết ngừng dùng QR cá nhân (S4-O2) |
| BR-13 | Mức **chi tối thiểu phòng riêng** (Hai Bà Trưng) do quản lý chốt **cho từng booking**; không cố định. Tham khảo: ~3 triệu buổi trưa, ~5 triệu tối cuối tuần đông | S8-Q1 | 🟡 | Quản lý Hai Bà Trưng | **Xử lý khi khách không đạt mức chi tối thiểu: CHƯA RÕ** (OI-02) |
| BR-14 | Mỗi booking có **mức cọc và set đặt trước riêng**; không áp một mức cọc hay một set chung cho mọi booking phòng riêng | S8-Q4 | 🟡 | Quản lý | — |

## 3. Thực đơn, giá, hết món

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-15 | Chỉ **chủ** duyệt món mới và giá bán. **Kế toán xác nhận loại thuế** trước khi món được bán. Bếp được **đề xuất** món | S4-O3, F1, S5-BR6 | ✅ | Chủ, kế toán, bếp | Hệ thống **chặn bán** món chưa có loại thuế |
| BR-16 | **Thu ngân không được đổi giá bán** tại quầy; chỉ giảm giá theo BR-01 đến BR-05 | S4-O3 | ✅ | Thu ngân | — |
| BR-17 | Giá bán theo **kênh** (tại chỗ và mang về, GrabFood, ShopeeFood). Giá app được đặt **theo từng món**, không theo một tỷ lệ cố định. **Bảng giá lễ** có ngày hiệu lực và ghi rõ **kênh, quán** áp dụng | S4-O3 | ✅ | Chủ | Món riêng từng quán được phép |
| BR-18 | **Quản lý hoặc bếp trưởng** được báo hết món. Người cập nhật trạng thái hết món **phải cập nhật các kênh còn lại** (app giao hàng) | S3-K6, S5-BR6 | ✅ | Quản lý, bếp | Thí điểm cập nhật app thủ công, hệ thống nhắc việc tới khi xác nhận từng app |

## 4. Thanh toán, tiền mặt, hóa đơn

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-19 | Một khoản chuyển khoản **chỉ được coi là đã thanh toán** khi có một trong hai: (a) ngân hàng xác nhận tự động khớp mã bill và số tiền; hoặc (b) **thu ngân kiểm tra tiền về thật** trên app ngân hàng và nhập **số tiền và mã tham chiếu**. Khớp không chắc chắn hoặc ghi đè sau đó cần **quản lý duyệt** | S2-C3, S5-BR7 | ✅ | Thu ngân, quản lý | **Ảnh chụp màn hình không bao giờ là bằng chứng** |
| BR-20 | Khi **offline** hoặc chưa xác nhận, giao dịch thẻ hoặc QR chỉ được ghi là **"dự định, chưa xác nhận"**, **không** tính là tiền đã nhận, và **phải hiện ở chốt ca** | S5-N2, S6-R6 | ✅ | Thu ngân | — |
| BR-21 | **Làm tròn:** không làm tròn từng món hay từng phần chia; tổng các phần chia = tổng bill; thẻ và chuyển khoản trả **đúng số tiền**. **Chỉ tiền mặt**: làm tròn **xuống** tới bội số 1.000đ trên **số tiền cuối cùng**. Khoản làm tròn **ghi thành dòng riêng trước khi xuất HĐ**, không ghi thành lệch két | S7-Q3 | ✅ | Thu ngân | Quán chịu tối đa 999đ/bill |
| BR-22 | Quỹ đầu ca **1.000.000đ** là tiền lẻ để thối, **không phải quỹ chi tiêu**. Mọi khoản chi từ két phải có **phiếu chi** | S2-C5, S5-BR8 | ✅ | Thu ngân, quản lý | — |
| BR-23 | Giao ca: tiền được đếm và **cả hai người xác nhận**. Chênh lệch ghi kèm lý do. **Không bao giờ sửa doanh thu cho khớp két** | S2-C5 | ✅ (thực hành hiện tại) | Thu ngân, quản lý | — |
| BR-24 | Chi tiền mặt từ két **≤ 300.000đ**: cần lý do và chứng từ (ảnh bổ sung **trước khi chốt ca**). **> 300.000đ**: gọi chủ duyệt **trước**. Ngoại lệ khẩn cấp (an toàn, phục vụ) thì ghi **vì sao không duyệt trước được** và báo chủ sớm nhất | S4-F6, S5-D4, S6-R5 | ✅ | Quản lý | — |
| BR-25 | **HĐĐT đã phát hành không bao giờ bị xoá** trong hệ thống nhà hàng. Mọi điều chỉnh hoặc thay thế đi qua quy trình của nhà cung cấp HĐĐT, **do kế toán thực hiện** | S4-F2, S5-BR10 | ✅ | Thu ngân, kế toán | — |
| BR-26 | Mỗi bill có thể gồm **nhiều bên thanh toán**, mỗi bên có **thông tin HĐ riêng** (công ty, MST, email). Với tiệc, chỉ dẫn xuất HĐ của kế toán phải có **trước khi tiệc bắt đầu** | S8-Q3 | 🟡 | Quản lý, thu ngân, kế toán | — |

## 5. Kiểm soát, nhật ký, phân quyền, cảnh báo

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-27 | Bắt buộc có **lý do, người thực hiện và người duyệt** (nếu cần duyệt) cho: xoá món đã tới bếp; giảm giá; hoàn tiền; **đổi phương thức thanh toán sau khi chốt**; điều chỉnh kho; sửa số đếm két đã hoàn tất. **Giữ bản ghi gốc và bản điều chỉnh** (không sửa đè) | S4-F11, S5-BR11 | ✅ | Mọi vai trò | — |
| BR-28 | Phân quyền theo ma trận vai trò. Công thức sốt chi tiết **chỉ bếp trưởng và chủ** được xem. Quản lý **không** xem lợi nhuận và lương của quán khác. Kế toán **không** sửa được order bếp. Phục vụ **không** xem giá vốn và doanh thu tổng | S4-O5, F11, S5-BR16 | ✅ | Hệ thống | Chi tiết ở FR-ADM-03 |
| BR-29 | **Cảnh báo tức thời** cho chủ khi: lệch két **> 200.000đ**; huỷ món **đã phục vụ > 100.000đ**; quản lý dùng dự phòng BR-03; **mất kết nối > 15 phút trong giờ phục vụ**. Các điều chỉnh thông thường chỉ đưa vào **báo cáo đêm** | S4-O6, S5-BR17 | ✅ | Hệ thống | — |
| BR-30 | Chỉ gửi tin **tiếp thị** tới khách **đã đồng ý** (có lưu bằng chứng), và khách phải **từ chối nhận tin** được dễ dàng | S4-O4, S5-BR15 | ✅ | Chủ, quản lý | Tin giao dịch (xác nhận booking) không phải tiếp thị |
| BR-31 | Dữ liệu bán hàng, hóa đơn, kho **lưu ít nhất 10 năm**, truy xuất và xuất được | S4-F11, S5-N4 | ✅ (chờ tư vấn xác nhận thời hạn chính xác) | Hệ thống | OI-07 |

## 6. Kho, sơ chế, mua hàng

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-32 | Khi nhận hàng, **bắt buộc nhập số lượng kiểm đếm** trước khi xác nhận. So **giá trên phiếu giao với giá thoả thuận** và đánh dấu chênh lệch. Ảnh và bằng chứng **không chặn** việc ghi nhận, **được bổ sung sau** | S3-U3, S5-BR12 | ✅ | Người nhận hàng | Ghi nhận được cả giao thiếu và hàng bị từ chối |
| BR-33 | Khi nhận **thịt lạnh và hải sản**: ghi **nhiệt độ đo được, thời điểm** và vấn đề chất lượng | S6-R10 | ✅ | Người nhận hàng | **Ngưỡng chấp nhận và cách xử lý hàng đáng ngờ: CHƯA CHỐT** (OI-01, Minh và Đức) |
| BR-34 | Mọi lần chuyển kho (sơ chế → quán, quán → quán, trả về): **bên gửi ghi xuất, bên nhận xác nhận**. Chênh lệch, hàng **rò hoặc hỏng** được ghi và chuyển trạng thái **tranh chấp**. **Không tính chi phí** cho quán nhận khi đang tranh chấp hoặc chưa nhận; chi phí được **giữ lại chờ xem xét** | S5-BR13 | ✅ | Sơ chế, quản lý, kế toán | — |
| BR-35 | Giá chuyển nội bộ = **giá nguyên liệu sống** × điều chỉnh theo **tỷ lệ thành phẩm đo được** của lô. Lương và chi phí sơ chế **phân bổ theo tháng** | S4-F8, S5-BR13 | ✅ | Kế toán | Cách tính phần vụn tái sử dụng: OI-03. Phương pháp phân bổ tháng: OI-04 |
| BR-36 | Báo cáo chênh lệch kho phải **so cùng đơn vị** và tách theo **loại giao dịch** (bán, chuyển, hỏng, bữa nhân viên, khuyến mãi, bể vỡ). **Chênh lệch không đồng nghĩa với lấy cắp** | S3-K7, U5, S5-G4 | ✅ | Hệ thống, chủ | — |

## 7. Dị ứng, khôi phục sự cố

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-37 | Ghi nhận dị ứng gồm **danh sách chất gây dị ứng** và **nguyên văn lời khách**. Phục vụ **phải trao đổi trực tiếp** với bếp trưởng. Bếp xác nhận nghĩa là **đã xem xét**, **không** chứng nhận "không chứa chất gây dị ứng". Hệ thống **không bao giờ** hiển thị nhãn "an toàn dị ứng" | S3-K5, S5-BR14 | ✅ | Phục vụ, bếp | Dị ứng nặng: bếp có thể từ chối món |
| BR-38 | Phiếu giấy lập trong sự cố, khi **nhập bù** vào hệ thống, **mặc định "đã phục vụ, không gửi bếp"**. Chỉ gửi bếp khi người nhập xác nhận món **chưa được làm** | S8-T1 | 🔵 | Thu ngân, quản lý | **Cần khách xác nhận** (OI-15) |
| BR-39 | Sau khôi phục: **một người được chỉ định** đối chiếu phiếu giấy với order trong hệ thống; **quản lý ký xác nhận trước khi chốt ca** | S6-R9 | ✅ | Quản lý | — |
| BR-40 | Đơn app phải có **mã đơn app**. Mã đơn là **duy nhất theo kênh**; nhập trùng thì cảnh báo | S5-C1 | ✅ | Người nhập đơn app | — |

## 8. Kênh khách QR và tự thanh toán (CR-01)

Nguồn: [CR-01](../05-kiem-soat-chat-luong/03-yeu-cau-thay-doi-CR01.md), Buổi 9 và 10. **QR-1** là giai đoạn thí điểm 6 bàn; **QR-2** là giai đoạn sau, cần chủ quyết riêng.

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-41 | QR của bàn **chỉ nhận đơn khi bàn đang mở** trên hệ thống. Khi nhóm khách chuyển bàn, **bàn cũ ngừng nhận đơn** và phiên khách đi theo bill | S9-L1, S10-R1 | ✅ | Hệ thống | — |
| BR-42 | Ở QR-1, **mọi đơn QR phải chờ nhân viên xác nhận** (phục vụ khu vực; thu ngân dự phòng) mới được gửi bếp. Mục tiêu 1 phút là **chỉ số đo**, không hứa với khách. Tự nhận đơn chỉ có ở QR-2, khi **chủ quyết riêng** | S9-L2, S10-Q1, Q3 | ✅ | Phục vụ, thu ngân | — |
| BR-43 | **Luôn chờ nhân viên**, kể cả ở QR-2: bia và đồ có cồn (**kiểm tra tuổi khi mang ra**, không lưu ảnh giấy tờ); ghi chú dị ứng (phục vụ **nói chuyện với khách** rồi báo bếp trưởng); số lượng bất thường; set lẩu bị đổi thành phần; món "ra sau" | S9-K2, F4, S10-Q4 | ✅ | Phục vụ, bếp | Ngưỡng "bất thường": OI-26 |
| BR-44 | Khách gọi từ **điện thoại riêng** vào **cùng một bill của bàn**; món của từng điện thoại được nhận diện; **không có giỏ chung**. Món gắn cờ (ví dụ set lẩu) gọi trùng trong **2 phút** được **tô nổi** cho người xác nhận. **Không bao giờ tự xoá** | S9-L4, S10-R3 | ✅ | Hệ thống, phục vụ | — |
| BR-45 | Khách **chỉ rút lại** món khi món **còn chờ xác nhận**. Sau đó phải gọi nhân viên, và áp dụng BR-07 | S9-L5, S10-R4 | ✅ | Khách | — |
| BR-46 | Gọi **món ăn** qua QR dừng lúc **21:45**. **Đồ uống chai** được gọi tới **~22:15** theo lệnh gọi cuối của quản lý. Quán đóng cửa 22:30. Ngoại lệ cho tiệc lớn **chỉ do quản lý và bếp trưởng** quyết định | S9-K4, S10-R5 | ✅ | Hệ thống | Giải quyết OI-22 |
| BR-47 | Khách **chỉ thấy trạng thái trung thực**: *Chờ nhân viên xác nhận* → *Bếp đã nhận* (đơn được nhận và phiếu đã in) → *Xong* (**chỉ khi bật cấu hình**, người ở pass bấm) → *Đã phục vụ* (người chạy món bấm). **Không** có "đang nấu", **không** hứa thời gian | S9-K1, S10-Q1 | ✅ | Hệ thống | Bật trạng thái "Xong" hay không: OI-27 |
| BR-48 | Khách **chỉ tự thanh toán được bill đã chốt**: khách đã yêu cầu tính tiền, không còn duyệt treo, không còn đơn QR chờ xác nhận. Màn hình hiện món, giảm giá đã duyệt, **cọc đã trả**, số còn phải trả. Ở QR-1 **chỉ trả cả bill** | S9-C1, C4, S10-R7 | ✅ | Khách, thu ngân | Tách tự thanh toán: QR-2 |
| BR-49 | Khoản tự thanh toán **chỉ được tính** khi tuyến ngân hàng **xác nhận đúng số tiền kèm mã tham chiếu của bill**, và thông báo đã được xác thực. Trả thiếu thì bill là **"trả một phần"**. Sai mã hoặc thừa tiền thì vào **hàng chờ không khớp**, **không tự đoán bàn** | S9-C2, S10-R7 | ✅ | Hệ thống, thu ngân | — |
| BR-50 | Trả trùng, trả thừa, hoàn tiền vì huỷ món sau thanh toán: ghi **cả tiền vào và tiền ra**; hoàn **về tài khoản gốc** sau khi xác minh giao dịch; cố gắng xong trong **ngày làm việc kế tiếp** (không phải cam kết); **không xoá** bản ghi gốc | S9-F3, S10-R10 | ✅ | Kế toán, thu ngân | — |
| BR-51 | Khách nói đã trả nhưng hệ thống **chưa xác nhận**: nhân viên **kiểm tra tài khoản công ty trước**, không yêu cầu trả lần hai. Khách rời đi khi còn chờ xác nhận: **bill vẫn mở**, báo quản lý; xác nhận đến sau sẽ được khớp vào đúng bill | S9-C3, Z1, S10-R8 | ✅ | Thu ngân, quản lý | — |
| BR-52 | Nhân viên **kiểm tra trạng thái thanh toán trước khi dọn và nhả bàn**. Hệ thống **không được giữ chân khách đã trả** chỉ vì màn hình cập nhật chậm | S9-C3, S10-R9 | ✅ | Phục vụ | NFR-36 |
| BR-53 | Chống lừa đảo: thẻ QR bàn **chống bóc dán**, được kiểm tra mỗi lần mở bàn, thay ngay khi hỏng. Thanh toán **chỉ trên tên miền của quán**, trang hiện **tên công ty và số tài khoản**. Nghi thẻ bị giả thì **cấp lại mã QR** | S9-Z1, S10-R13 | ✅ | Quản lý, hệ thống | — |
| BR-54 | Thông tin HĐ công ty trên điện thoại là **tuỳ chọn**, khách **xem lại trước khi gửi**; kế toán kiểm tra thông tin bất thường trước khi phát hành. Số điện thoại **tuỳ chọn** (liên hệ HĐ hoặc xử lý thanh toán). **Đồng ý tiếp thị là ô riêng, không tích sẵn** | S9-O3, F2, S10-R11 | ✅ | Khách, kế toán | BR-30 |
| BR-55 | Khi phát hiện **mất kết nối**, QR **tạm dừng**; trang khách (nếu mở được) hiện "tạm dừng gọi món, vui lòng gọi nhân viên"; nhân viên chuyển sang máy cầm tay hoặc phiếu giấy. **Đơn gửi trong lúc mất kết nối không bao giờ được giao sau** | S9-T1, S10-R12 | ✅ | Hệ thống, quản lý | — |
| BR-56 | Quản lý **tạm dừng hoặc bật lại QR** theo bàn, khu hoặc quán **bất cứ lúc nào**, **không đóng bill** | S10-Q1 | ✅ | Quản lý | — |
| BR-57 | Mỗi lượt khách có **mã ngồi bàn** (bắt buộc từ QR-1). Mã được đưa cho khách **trên thẻ khi ngồi**, **đổi cho nhóm sau**, **không in lên phiếu bếp** | S10-R2 | ✅ | Phục vụ, hệ thống | Cách đưa mã và khối lượng việc: OI-25 |
| BR-58 | Tiệc phòng riêng: QR **chỉ cho gọi thêm** sau khi quản lý **xác nhận set đặt trước**. Thanh toán tiệc lớn hoặc nhiều công ty **qua thu ngân** | S9-Q1, S10-Q3 | ✅ | Quản lý Hai Bà Trưng | — |
| BR-59 | **Chỉ bật tự thanh toán** khi thu ngân và kế toán **đã thử xác nhận ngân hàng thật**. Trước đó dùng QR theo bill và nhân viên xác minh (BR-19) | S9-O2, S10-Q2 | ✅ | Chủ, kế toán | TR-11 |
| BR-60 | Tuyến xác nhận ngân hàng: **hỏi Vietcombank trước**. Nhà cung cấp bên thứ ba phải nêu rõ dữ liệu nhận, nơi lưu, ai truy cập, **cách thu hồi quyền** và **thông báo có chữ ký**. **Không bao giờ chia sẻ mật khẩu ngân hàng** | S9-F1, S10-C2 | ✅ | Kế toán | OI-24 |

## 9. Nhân viên quét QR bàn và bối cảnh Hà Nội (CR-02)

Nguồn: [CR-02](../05-kiem-soat-chat-luong/04-yeu-cau-thay-doi-CR02.md), Buổi 11 và 12.

| ID | Quy tắc | Nguồn | Tin cậy | Áp dụng cho | Ngoại lệ / Ghi chú |
|---|---|---|---|---|---|
| BR-61 | **Mọi bàn** có thẻ QR chống bóc dán ghi **tên bàn và khu đúng như trên app** (ví dụ "Sân trong C3"); mỗi quán có thẻ dự phòng. Phòng riêng: **mỗi bàn một thẻ**. Thẻ ở bàn **không** thí điểm không quảng cáo việc khách tự gọi món | S11-L6, Q1, S12-V3, V9 | ✅ | Quản lý | Đống Đa gắn đủ 24 bàn trước khi tập quét |
| BR-62 | Cùng một thẻ QR có **hai chế độ**: quét bằng **app nhân viên** (đã đăng nhập PIN, thiết bị của quán) là **chế độ nhân viên**; quét bằng **camera hoặc Zalo** là **chế độ khách**. App nhân viên **từ chối** thẻ của quán khác | CR-02 | ✅ | Hệ thống | — |
| BR-63 | Quét là **cách mở bàn thông thường** nhưng **không bắt buộc**: sơ đồ bàn và chọn tay luôn có. Chọn tay được ghi nhật ký là **"chọn tay"** | S11-L1, L5, S12-V4 | ✅ | Phục vụ, quản lý | Thẻ mất, hỏng hoặc không quét được |
| BR-64 | Khi nhân viên quét, app hiện **tên bàn, khu, trạng thái** (chữ lớn) **trước khi làm gì**. **Trống**: mở bàn, nhập số khách; bàn thí điểm thì hiện **mã ngồi bàn mới** cho nhóm. **Đang có khách**: hiện nhóm, bill, đơn QR chờ; **không bao giờ mở nhóm thứ hai**. **Đã đặt**: hiện giờ đặt, **chỉ quản lý** được cho khách vãng lai ngồi. **Đang dọn**: chờ quản lý đánh dấu sẵn sàng | S11-L2, S12-V5 | ✅ | Phục vụ, quản lý | — |
| BR-65 | **Chuyển nhóm bằng quét**: quét bàn cũ rồi bàn đích; app hiện **mọi bàn bị ảnh hưởng** trước khi xác nhận. Bàn đích phải **trống hoặc đã thuộc chính nhóm đó**; **không bao giờ gộp** vào nhóm khác. Nếu chỉ còn bàn đã đặt thì **quản lý xử lý booking trước**. Sau khi xác nhận, bill và QR đi theo nhóm; thẻ cũ ngừng nhận đơn của nhóm | S11-L4, Q1, S12-V7 | ✅ | Phục vụ, quản lý | Vẫn chuyển được bằng sơ đồ bàn khi cần nhanh |
| BR-66 | Thu ngân quét thẻ để mở **bill hiện tại của nhóm** (mọi bàn ghép hoặc chuyển, cọc, giảm giá đã duyệt). Thu ngân **đọc lại số bàn và số tiền**. Quét **không bao giờ** đánh dấu đã trả hay đóng bill | S11-C1, S12-V8 | ✅ | Thu ngân | — |
| BR-67 | Khách quét thẻ ở **bàn không thí điểm**: chỉ **xem thực đơn** kèm lời nhắn nhờ nhân viên gọi món. **Gọi nhân viên** chỉ có ở 6 bàn thí điểm (QR-1) cho tới khi quản lý chắc chắn các yêu cầu được đáp ứng | S12-V9 | ✅ | Hệ thống | — |
| BR-68 | Máy **mất liên lạc với máy chủ tại quán**: **không mở được bàn mới**; món đang nhập được giữ là **bản nháp CHƯA GỬI** hiển thị nổi bật; **không bao giờ tự gửi** khi có kết nối lại. Nhân viên dùng **phiếu giấy đánh số**; khi có kết nối lại thì **tự quyết** gửi bản nháp hay huỷ vì đã phục vụ bằng giấy | S11-Z1, S12-V10 | ✅ | Phục vụ, hệ thống | **Thay** cách cũ "hàng đợi tự gửi lại" ở FR-OFF-06. Buổi thử kỹ thuật phải chứng minh không có phiếu bếp bất ngờ |
| BR-69 | Phiếu và màn hình bếp ghi **nguồn đơn**: tên phục vụ gửi, hoặc "QR – đã được <tên> xác nhận". Đơn QR **chưa xác nhận không bao giờ in ở bếp** | S11-Z1, S12-V11 | ✅ | Hệ thống | — |
| BR-70 | Set (ví dụ set lẩu) chỉ được bấm **"Xong"** khi **đã ráp đủ thành phần** ở pass. Điện thoại khách **không được gọi ra** món "ra sau"; chỉ nhân viên gọi ra món | S11-K1, S12-V12 | ✅ | Bếp, hệ thống | Bổ sung cho BR-47 |

## 10. Xung đột đã giải quyết (lịch sử)

| Xung đột | Các bên | Cách giải quyết | Nguồn |
|---|---|---|---|
| Quyền sửa bill nhanh ↔ yêu cầu lý do cho mọi lần xoá | Quản lý ↔ Kế toán | Hạn mức BR-01, BR-02; lý do bắt buộc BR-05; xoá món tính nhầm không tính hạn mức BR-04 | S1-Q3 → S5, S6 |
| Mức 300k cho dự phòng | Chủ ↔ Kế toán | Chỉ dùng cho lỗi phục vụ thật, sau 3 phút, có người thứ hai xác nhận; kế toán rà từng trường hợp trong thí điểm | S4-O1 → S5-D2 |
| Quản lý hứa làm lại món trước khi hỏi bếp | Quản lý ↔ Bếp | BR-07, BR-08: quản lý xử lý khách trong hạn mức; bếp xác nhận thay đổi; ghi remake | S2-M4, S3-K4 |
| Phạm vi Must tháng 3 (bảng MoSCoW Buổi 4 ↔ Buổi 5) | Chủ (tự mâu thuẫn) | p, u, v, z **vẫn Must** với phạm vi thu hẹp | S6-R8 |
| "5% hoặc 150k" không rõ | Chủ | Đổi thành ≤ 10%, trần 150k | S5-BR1 |
| Xác nhận mọi đơn QR ↔ khách phải chờ, mất ý nghĩa tự gọi | Quản lý ↔ Chủ | QR-1 xác nhận mọi đơn (đo thời gian); QR-2 có thể tự nhận món thêm thông thường khi chủ quyết riêng; nhóm món ở BR-43 luôn chờ người | S9-L2, S10-Q1, Q3 |
| Mã ngồi bàn chỉ cần khi tự nhận đơn (đề xuất của BA) ↔ cần ngay từ QR-1 | BA ↔ Quản lý | Theo quản lý: **mã ngồi bàn bắt buộc từ QR-1** (BR-57) | S10-R2 |
| Máy cầm tay **tự gửi lại** món khi có Wi-Fi lại (thiết kế FR-OFF-06 cũ) ↔ nguy cơ in trùng sau khi đã dùng giấy | BA ↔ QL Cầu Giấy | Chỉ giữ **bản nháp chưa gửi**; nhân viên tự quyết gửi hay huỷ (BR-68) | S12-V10 |
| Chuyển nhóm tới bàn "chỉ được trống" (đề xuất của BA) ↔ nhóm dùng nhiều bàn | BA ↔ QL Hai Bà Trưng | Bàn đích **trống hoặc đã thuộc chính nhóm đó**; không gộp nhóm khác (BR-65) | S12-V7 |
