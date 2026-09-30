# SRS — Phần 1: Yêu cầu chức năng (Functional Requirements)

> Phương pháp: skill `functional-vs-nonfunctional-splitter`, `proto-requirements-normalizer`, `requirements-prioritizer`, `moscow-prioritisation`.
> **Ưu tiên theo giai đoạn** (khách chốt ở S4-O10, S5-C, S6-R8):
> - **TĐ** = thí điểm Đống Đa (~22/02/2027)
> - **TC** = toàn chuỗi (≤ 31/03/2027)
> - **M** = Must · **S** = Should · **C** = Could · **—** = chưa làm ở giai đoạn đó
>
> Quy ước câu: *"Hệ thống phải …"* được lược bỏ để ngắn gọn; mỗi dòng là **một** yêu cầu kiểm thử được. **Nguồn** trỏ về biên bản phỏng vấn. **BR** trỏ về [sổ quy tắc](../02-phan-tich-nghiep-vu/03-quy-tac-nghiep-vu.md). **UC** trỏ về [use case](03-use-case.md).

## Tổng quan module

| Mã | Module | Số yêu cầu | Người dùng chính |
|---|---|---|---|
| ADM | Quản trị, cấu hình, dữ liệu ban đầu | 8 | Quản trị, chủ |
| MNU | Thực đơn và giá | 11 | Chủ, kế toán, bếp trưởng |
| RSV | Đặt bàn, tiệc, cọc, danh sách chờ | 10 | Quản lý |
| TBL | Sơ đồ bàn, **quét QR bàn** (CR-02) | 9 | Phục vụ, quản lý |
| ORD | Gọi món | 11 | Phục vụ |
| KIT | Bếp | 10 | Bếp trưởng, đầu bếp |
| BIL | Bill và thanh toán | 18 | Thu ngân, quản lý, chủ |
| SHF | Ca và két | 6 | Thu ngân, quản lý |
| DLV | Mang về và giao hàng | 6 | Phục vụ quầy, thu ngân, kế toán |
| INV | Kho, định lượng, kiểm kê | 10 | Bếp, quản lý, mua hàng |
| PRP | Sơ chế và chuyển kho | 7 | Sơ chế, quản lý, kế toán |
| PUR | Mua hàng và nhận hàng | 8 | Mua hàng, người nhận |
| CUS | Khách hàng | 5 | Quản lý, chủ |
| RPT | Báo cáo, dashboard, cảnh báo | 9 | Chủ, quản lý, kế toán |
| INT | Tích hợp và xuất dữ liệu | 6 | Kế toán, hệ thống |
| OFF | Offline và khôi phục | 6 | Mọi người trong quán |
| AUD | Nhật ký và phê duyệt | 4 | Chủ, kế toán |
| GST | Khách tự phục vụ qua QR: gọi món, trạng thái, tự thanh toán (**CR-01**, CR-02) | 26 | Khách, phục vụ, thu ngân |
| | **Tổng** | **170** | |

---

## ADM — Quản trị, cấu hình, dữ liệu ban đầu

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-ADM-01 | Quản lý **quán** (tên, mã, **địa chỉ pháp lý theo phường hiện hành** của Hà Nội, giờ mở cửa) và **khu vực** trong quán (trong nhà, sân trong, sân thượng, phòng riêng, khu quây) | M | M | S1-Q2, S8, S11-F1 | — | UC-30 |
| FR-ADM-02 | Quản lý **người dùng**: tạo, khoá, gán vai trò và quán. Mỗi người có **PIN riêng**, đăng nhập nhanh trên thiết bị dùng chung, **tự đăng xuất** sau thời gian không dùng (tham số) | M | M | S5-N7 | BR-28 | UC-30 |
| FR-ADM-03 | **Phân quyền theo vai trò** và **phạm vi quán** theo ma trận ở mục "Ma trận phân quyền" bên dưới | M | M | S4-O5, F11 | BR-28 | UC-30 |
| FR-ADM-04 | Đăng ký **thiết bị của quán** (máy cầm tay, máy thu ngân, màn hình bếp, máy in) và thu hồi thiết bị. Gán **máy in cho khu chế biến** | M | M | S4-O7, S5-N7 | — | UC-30 |
| FR-ADM-05 | Quản lý **danh mục lý do** cho huỷ món, giảm giá, hoàn tiền, điều chỉnh kho, hao hỏng. Lý do "Khác" **bắt buộc ghi chú** | M | M | S4-O1, S5-BR2 | BR-05 | UC-30 |
| FR-ADM-06 | Cấu hình **tham số quy tắc**, có lịch sử thay đổi (ai đổi, khi nào, giá trị cũ và mới), gồm: hạn mức giảm giá theo bill và theo ca; dự phòng khi chủ không trả lời (số tiền, số phút); ngưỡng chi tiền mặt; ngưỡng cảnh báo; thời gian giữ bàn | M | M | S5-D1–D4 | BR-01–03, 24, 29 | UC-30 |
| FR-ADM-07 | Cấu hình **phương thức thanh toán** (tiền mặt, chuyển khoản/QR, thẻ, ví) và **tài khoản nhận** (tài khoản thu chung của công ty) | M | M | S4-F3 | BR-10 | UC-30 |
| FR-ADM-08 | **Nhập dữ liệu ban đầu** (menu, đơn vị, bàn, khách hàng) từ file, có **kiểm tra trùng, thiếu, sai đơn vị** và báo lỗi trước khi nhập | M | M | S4-Z1, S5-D9 | — | UC-30 |

### Ma trận phân quyền (FR-ADM-03)

Nguồn: S4-O5, S4-F11, S3-K7.

Ký hiệu: ✔ được làm · 👁 chỉ xem · ✖ không · (Q) chỉ trong quán mình.

| Chức năng | Phục vụ | Thu ngân | Đầu bếp | Bếp trưởng | Quản lý quán | Sơ chế | Mua hàng | Kế toán | Chủ |
|---|---|---|---|---|---|---|---|---|---|
| Gọi món, bàn của mình | ✔ | ✔ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Xem trạng thái món | ✔ | ✔ | ✔ | ✔ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Duyệt huỷ món đã tới bếp | ✖ | ✖ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Xác nhận thay đổi ở bếp | ✖ | ✖ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |
| Bill, thanh toán, ca của mình | ✖ | ✔ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | 👁 | 👁 |
| Giảm giá trong hạn mức | ✖ | ✖ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Duyệt vượt hạn mức | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ |
| Báo hết món | ✖ | ✖ | ✖ | ✔ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Xem công thức sốt chi tiết | ✖ | ✖ | ✖ | ✔ | ✖ | ✖ | ✖ | ✖ | ✔ |
| Xem định lượng tóm tắt, chất gây dị ứng | 👁 | 👁 | 👁 | ✔ | 👁 | 👁 | 👁 | 👁 | ✔ |
| Kho quán, kiểm kê | ✖ | ✖ | 👁 | ✔(Q) | ✔(Q) | ✖ | ✔ | 👁 | ✔ |
| Sản xuất sơ chế, chuyển kho | ✖ | ✖ | ✖ | ✔(Q) | ✔(Q) | ✔ | ✔ | 👁 | ✔ |
| Mua hàng, nhận hàng | ✖ | ✖ | ✖ | ✔(Q) | ✔(Q) | ✔ | ✔ | 👁 | ✔ |
| Doanh thu quán mình | ✖ | 👁 ca mình | ✖ | ✖ | 👁(Q) | ✖ | ✖ | 👁 | 👁 |
| Doanh thu, lợi nhuận quán khác | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | 👁 | 👁 |
| Giá vốn | ✖ | ✖ | ✖ | 👁 | 👁(Q) | 👁 | 👁 | 👁 | 👁 |
| Khớp số, dữ liệu HĐĐT, xuất MISA | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ | 👁 |
| Menu, giá bán | ✖ | ✖ | ✖ | đề xuất | ✖ | ✖ | 👁 | loại thuế | ✔ duyệt |
| Sửa order bếp | — | — | — | — | — | — | — | **✖** | — |
| Người dùng, tham số | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ |
| Xác nhận hoặc từ chối đơn QR (CR-01) | ✔ | ✔ dự phòng | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Tạm dừng hoặc bật QR, cấp lại mã QR bàn (CR-01) | ✖ | ✖ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Theo dõi tự thanh toán, xử lý khoản không khớp (CR-01) | ✖ | ✔ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✔ | 👁 |
| Quét QR bàn ở chế độ nhân viên: mở bàn, xem nhóm (CR-02) | ✔(Q) | ✔(Q) mở bill | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |
| Cho khách vãng lai ngồi bàn đã đặt (CR-02) | ✖ | ✖ | ✖ | ✖ | ✔(Q) | ✖ | ✖ | ✖ | ✔ |

> **Khách (A11)** không có tài khoản. Khách chỉ truy cập **phiên khách** của bàn mình (sau khi quét QR và nhập mã ngồi bàn): xem menu, gửi món thêm, xem trạng thái món và bill **của bàn mình**, gọi nhân viên, tự thanh toán. Khách **không bao giờ** xem được dữ liệu bàn khác (NFR-38).

---

## MNU — Thực đơn và giá

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-MNU-01 | Quản lý **món**: mã, tên chuẩn, nhóm, mô tả, ảnh (tuỳ chọn), **loại thuế**, trạng thái đang bán hay ngừng | M | M | S1-Q1 | BR-15 | UC-26 |
| FR-MNU-02 | Món có **size hoặc biến thể** (ví dụ heo nướng nhỏ và lớn), mỗi size một giá | M | M | S1-Q1, S3-K7 | — | UC-26 |
| FR-MNU-03 | **Tuỳ chọn món** (modifier) có hoặc không tính tiền (ít cay, không ngò, không bột ngọt) | M | M | S2-M3 | — | UC-26 |
| FR-MNU-04 | **Set/combo**: danh sách thành phần; cho đổi thành phần có kiểm soát; thành phần **hiện trên phiếu bếp** | M | M | S2-M3, S8-Q3 | — | UC-26 |
| FR-MNU-05 | Gán món vào **khu chế biến** (nướng; xào và nồi đất; lẩu; quầy đồ uống) để định tuyến phiếu | M | M | S3-K1 | — | UC-26 |
| FR-MNU-06 | Món được **giới hạn theo quán** (món riêng từng quán) | S | M | S4-O3 | BR-17 | UC-26 |
| FR-MNU-07 | **Bảng giá theo kênh** (tại chỗ và mang về, GrabFood, ShopeeFood), đặt giá **theo từng món** | S | M | S2-M6, S4-O3 | BR-17 | UC-26 |
| FR-MNU-08 | **Bảng giá có ngày hiệu lực** (lễ, Tết, set tiệc), ghi rõ kênh và quán áp dụng, **tự áp dụng** theo thời gian | S | M | S2-C4, S4-O3, S7-Q1 | BR-17 | UC-26 |
| FR-MNU-09 | **Quy trình mở bán món**: bếp đề xuất → kế toán gán loại thuế → chủ duyệt giá → món mới được bán. **Chặn bán** món chưa có loại thuế | S | M | S4-O3, F1 | BR-15 | UC-26 |
| FR-MNU-10 | **Không ai ngoài chủ** được sửa giá bán. Màn hình thu ngân **không có** chức năng đổi giá | M | M | S4-O3 | BR-16 | UC-08 |
| FR-MNU-11 | **Ánh xạ món** giữa hệ thống và từng app giao hàng (tên, mã món trên app) để nhập đơn và đối soát | S | M | S4-Z1 | — | UC-19 |

## RSV — Đặt bàn, tiệc, cọc, danh sách chờ

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-RSV-01 | Tạo và sửa **booking**: tên, số điện thoại, ngày giờ, số khách, kênh đặt, ghi chú, bàn hoặc nhóm bàn dự kiến. Có **mã booking** | S | M | S2-M1 | — | UC-15 |
| FR-RSV-02 | Gửi hoặc tạo **tin xác nhận booking** kèm điều khoản cọc và huỷ; **lưu nội dung và thời điểm gửi** làm bằng chứng | S | M | S5-D3, S6-R4 | BR-11 | UC-15 |
| FR-RSV-03 | Ghi nhận **tiền cọc**: số tiền, phương thức, mã tham chiếu ngân hàng, người nhận. Nội dung chuyển khoản gợi ý chứa **mã booking**. Cọc có trạng thái riêng, **không phải doanh thu** | S | M | S4-F5, O2 | BR-10 | UC-15 |
| FR-RSV-04 | **Cấn trừ cọc** vào số phải trả của bill cuối khi khách tới; hiện rõ trên bill và trong báo cáo | S | M | S4-F5, S5-BR5 | BR-10 | UC-08 |
| FR-RSV-05 | Xử lý **huỷ và không tới** theo BR-11: tự tính số hoàn và số giữ theo thời điểm huỷ và bằng chứng điều khoản; cho **dời booking trong 7 ngày**; lưu kết quả (hoàn, giữ, chuyển) | S | M | S5-D3, S6-R4 | BR-11 | UC-17 |
| FR-RSV-06 | Ghi nhận **cọc vào tài khoản cá nhân** là ngoại lệ: bắt buộc nhập chứng từ chuyển về tài khoản công ty; **nhắc kế toán** tới khi hoàn tất | S | M | S4-F5 | BR-12 | UC-15 |
| FR-RSV-07 | **Tiệc/sự kiện**: nhiều bàn hoặc phòng riêng; **số khách có lịch sử thay đổi**; **mức chi tối thiểu**; **set đặt trước kèm giờ ra món**; **nhiều bên thanh toán**, mỗi bên có thông tin HĐ riêng | S | M | S8-Q1, Q3 | BR-13, 14, 26 | UC-16 |
| FR-RSV-08 | **Danh sách chuẩn bị cho bếp** của các tiệc ngày hôm sau. Khi set hoặc số khách thay đổi thì **thông báo bếp** | S | M | S8-Q1, Q3 | — | UC-16 |
| FR-RSV-09 | **Lịch booking** theo ngày và quán, hiện trên sơ đồ bàn. Đánh dấu khách trễ sau 15 phút; **giữ bàn** trong thời gian tham số (mặc định 20 phút) rồi giải phóng | S | M | S2-M1 | — | UC-15 |
| FR-RSV-10 | **Danh sách chờ**: tên, số điện thoại, số khách; ước thời gian chờ theo trạng thái bàn; đánh dấu đã gọi hoặc đã vào bàn | C | S | S2-M2 | — | UC-18 |

## TBL — Sơ đồ bàn

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-TBL-01 | **Sơ đồ bàn theo khu**, cấu hình riêng cho từng quán (không sao chép cứng giữa các quán) | M | M | S2-M2, S7-Q2 | — | UC-01 |
| FR-TBL-02 | **Trạng thái bàn thời gian thực**: trống, đã đặt, đang phục vụ, chờ thanh toán, đang dọn. Kèm thời gian ngồi và cờ **món chờ lâu** | M | M | S2-M2, M8 | — | UC-01 |
| FR-TBL-03 | **Mở bàn**: số khách, phục vụ phụ trách; gắn booking nếu có | M | M | S2-M2 | — | UC-01 |
| FR-TBL-04 | **Nhóm bàn**: ghép nhiều bàn cho một lượt ăn; **các bàn vẫn hiện riêng**. Có **nhóm bàn định sẵn** để đặt chung (ví dụ khu quây 4 bàn ở Cầu Giấy) | M | M | S2-M2, S8-T4, Q4 | — | UC-06 |
| FR-TBL-05 | **Chuyển bàn** toàn bộ hoặc một phần, kể cả chuyển **từ N bàn sang M bàn** (ví dụ 2 bàn sân thượng sang 3 bàn trong nhà), **giữ nguyên bill**. Bếp và người chạy món thấy số bàn mới. Lưu lịch sử chuyển | M | M | S2-M2, S8-Q2, S12-V7 | BR-65 | UC-06 |
| FR-TBL-06 | Đánh dấu **đã dọn xong** thì bàn về trạng thái trống | M | M | S1-Q4 | — | UC-01 |
| FR-TBL-07 | *(CR-02)* **Quét QR bàn bằng app nhân viên** (chế độ nhân viên): nhận diện bàn và hiện **tên bàn, khu, trạng thái** chữ lớn. **Chạy cả khi mất Internet** (máy chủ tại quán giải mã thẻ). **Từ chối** thẻ của quán khác. Luôn có **chọn tay** trên sơ đồ bàn, nhật ký ghi là "chọn tay" | M | M | S11-L1, L2, T1, S12-V4, V5 | BR-62, 63, 64 | UC-41 |
| FR-TBL-08 | *(CR-02)* **Hành động theo trạng thái sau khi quét:** bàn **trống** thì mở bàn (số khách; bàn thí điểm sinh **mã ngồi bàn mới**); bàn **có khách** thì mở **nhóm hiện tại** (bill, món, đơn QR chờ), **không mở nhóm thứ hai**; bàn **đã đặt** thì hiện giờ đặt và **cần quản lý**; bàn **đang dọn** thì chặn. **Mở bàn chỉ thành công khi máy chủ tại quán xác nhận** | M | M | S11-L2, S12-V5, V10 | BR-64, 68 | UC-41 |
| FR-TBL-09 | *(CR-02)* **Chuyển nhóm bằng quét:** quét bàn cũ rồi bàn đích, hiện **mọi bàn bị ảnh hưởng** trước khi xác nhận; bàn đích phải **trống hoặc đã thuộc chính nhóm đó**; bàn đã đặt thì quản lý xử lý trước; sau khi xác nhận **thẻ cũ ngừng nhận đơn** của nhóm | S | S | S11-L4, Q1, S12-V7 | BR-65, 41 | UC-41 |

## ORD — Gọi món

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-ORD-01 | Phục vụ **gọi món trên máy cầm tay** tại bàn: chọn món, size, số lượng, tuỳ chọn, ghi chú; gửi **cùng lúc** tới bếp và bill | M | M | S1-Q7, S2-C7 | — | UC-01 |
| FR-ORD-02 | **Món gọi thêm** gắn vào order của bàn; phiếu và màn hình bếp ghi rõ **MÓN THÊM** và số bàn | M | M | S3-K2 | — | UC-01 |
| FR-ORD-03 | **Dị ứng**: chọn chất gây dị ứng từ danh sách **và** ghi nguyên văn lời khách. Hiện **nổi bật** trên phiếu và màn hình bếp. **Yêu cầu bếp trưởng xác nhận đã xem**. Không bao giờ hiển thị nhãn "an toàn dị ứng" | M | M | S3-K5, S5-BR14 | BR-37 | UC-01 |
| FR-ORD-04 | **Giữ món / gọi ra món** theo món hoặc theo lượt món. Bếp thấy món đang giữ nhưng **chưa làm**. Phục vụ bấm "gọi ra món" thì bếp mới làm. Nhắc khi giữ quá lâu | S | M | S2-M3, S3-K2 | — | UC-02 |
| FR-ORD-05 | Sửa hoặc xoá món **trước khi gửi bếp**: không cần duyệt | M | M | S5-BR3 | BR-06 | UC-01 |
| FR-ORD-06 | Huỷ hoặc đổi món **sau khi phiếu đã tới bếp**: bắt buộc lý do và **quản lý duyệt**. Gửi thông báo thay đổi tới bếp; bếp **bấm xác nhận**; **quản lý thấy trạng thái xác nhận** | M | M | S5-BR3, S6-R2 | BR-05, BR-07 | UC-03 |
| FR-ORD-07 | Món bị đánh dấu **hết** thì **bị chặn gọi** trên mọi thiết bị sảnh ngay lập tức | M | M | S2-M8, S3-K6 | BR-18 | UC-05 |
| FR-ORD-08 | Mỗi dòng order có **mã định danh duy nhất** do thiết bị tạo, để việc gửi lại hay đồng bộ lại **không tạo trùng** món hay phiếu | M | M | S2-M7, S8-T1 | — | UC-01 |
| FR-ORD-09 | Ghi **bữa ăn nhân viên** riêng, theo món hoặc theo nguyên liệu; không phải giảm giá khách | S | M | S2-M5, S5-BR4 | BR-09 | UC-07 |
| FR-ORD-10 | Phục vụ xem **trạng thái món** của các bàn mình phụ trách (đang giữ, chờ, đang làm, xong chờ mang ra, đã mang ra) | M | M | S2-M8, S4-O5 | — | UC-01 |
| FR-ORD-11 | **Sổ lỗi món**: ghi món sót hoặc trùng (kể cả phát hiện trước khi khách phàn nàn), kèm nguyên nhân nếu biết, để đo G3 | M | M | S5-A-G3, D10 | — | UC-07 |

## KIT — Bếp

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-KIT-01 | **Định tuyến** từng món tới khu chế biến theo cấu hình (FR-MNU-05). Đồ uống định tuyến tới quầy | M | M | S3-K1 | — | UC-04 |
| FR-KIT-02 | **In phiếu bếp theo khu**. Phiếu gồm: **số bàn, giờ, món, số lượng, size, tuỳ chọn, ghi chú, dị ứng**, cờ **THÊM / THAY THẾ / HUỶ / GIỮ**, mã phiếu | M | M | S3-K3, S5-C1 | — | UC-04 |
| FR-KIT-03 | **Màn hình bếp và điều phối ra món**: phiếu theo thời gian, **gom theo bàn**, trạng thái từng món, thời gian chờ. Chữ đọc được từ khoảng 2m | M | M | S3-K2, K3 | — | UC-04 |
| FR-KIT-04 | Cập nhật **trạng thái món** (bắt đầu, xong) bằng thao tác chạm lớn, dùng được khi tay ướt. **Người chạy món xác nhận đã mang ra** | M | M | S3-K2, K3 | — | UC-04 |
| FR-KIT-05 | Thông báo **thay đổi hoặc huỷ** hiển thị nổi bật, **không tự biến mất** cho tới khi bếp bấm xác nhận; **in phiếu thay đổi hoặc huỷ** tại khu tương ứng; hiện món đã bắt đầu làm hay chưa | M | M | S3-K3, K4, S6-R2 | BR-07 | UC-03 |
| FR-KIT-06 | **Báo hết món** (bếp trưởng hoặc quản lý), ghi người báo. Tạo **nhắc việc cập nhật từng app giao hàng**, tới khi có người xác nhận đã cập nhật từng app | M | M | S2-M6, S3-K6 | BR-18 | UC-05 |
| FR-KIT-07 | Ghi **làm lại hoặc bỏ đi** theo lý do, gắn với món và order. **Phân biệt** đĩa bỏ đi với đĩa nếm kiểm tra | S | M | S3-K4 | BR-08 | UC-07 |
| FR-KIT-08 | **Cảnh báo món chờ quá ngưỡng** (ngưỡng theo nhóm món hoặc khu, cấu hình được) | S | M | S2-M8 | — | UC-04 |
| FR-KIT-09 | **Tổng số phần đang chờ** theo món cho từng khu (ví dụ: 7 phần bò đang chờ) | C | S | S3-K1 | — | UC-04 |
| FR-KIT-10 | *(CR-02)* Phiếu và màn hình bếp ghi **nguồn đơn**: tên phục vụ gửi, hoặc "QR – đã được <tên> xác nhận". Đơn QR **chưa xác nhận không bao giờ in ở bếp**. Set chỉ bấm "Xong" khi **đã ráp đủ** thành phần | S | S | S11-Z1, K1, S12-V11, V12 | BR-69, 70 | UC-04 |

## BIL — Bill và thanh toán

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-BIL-01 | Xem và in **bill tạm tính** theo bàn hoặc nhóm bàn: món, số lượng, đơn giá, giảm giá, **thuế theo loại thuế**, cọc cấn trừ, tổng | M | M | S2-C1 | — | UC-08 |
| FR-BIL-02 | **Tách bill**: chia đều, theo món, theo số tiền. **Tổng các phần = tổng bill** chính xác từng đồng; **không làm tròn từng phần** | M | M | S2-C2, S7-Q3 | BR-21 | UC-08 |
| FR-BIL-03 | **Gộp bill** của nhiều bàn hoặc nhóm bàn | M | M | S2-C2 | — | UC-08 |
| FR-BIL-04 | **Giảm giá, tặng món** theo món hoặc cả bill, theo % hoặc số tiền. **Bắt buộc lý do**. Tự kiểm tra **hạn mức theo bill và theo ca**; vượt thì chuyển sang duyệt | M | M | S4-O1, S5-BR1, D1 | BR-01, 02, 05 | UC-09 |
| FR-BIL-05 | **Duyệt từ xa**: gửi yêu cầu tới điện thoại chủ; chủ duyệt hoặc từ chối. Sau **3 phút** không phản hồi thì mở **quy trình dự phòng** (≤ 300.000đ, người xác nhận thứ hai là thu ngân hoặc bếp trưởng) và **cảnh báo chủ ngay** | M | M | S5-D2, S6-R3 | BR-03 | UC-10 |
| FR-BIL-06 | **Xoá món tính nhầm** (lý do "tính trùng" hoặc "sai order"); **không** tính vào hạn mức giảm giá; cần quản lý duyệt nếu món đã tới bếp | M | M | S2-C6, S5-BR1 | BR-04, 07 | UC-09 |
| FR-BIL-07 | Ghi nhận **thanh toán nhiều phương thức** cho một bill: tiền mặt (tiền khách đưa, tiền thối), thẻ (mã chuẩn chi), ví, chuyển khoản | M | M | S2-C3 | — | UC-08 |
| FR-BIL-08 | **Làm tròn tiền mặt**: làm tròn **xuống** bội số 1.000đ trên số tiền cuối, ghi **dòng làm tròn riêng** trước khi đóng bill. **Không** làm tròn thẻ và chuyển khoản | M | M | S7-Q3 | BR-21 | UC-08 |
| FR-BIL-09 | **QR động theo bill**: số tiền chính xác, nội dung chứa **mã quán và mã bill**. Tự xác nhận khi thông báo ngân hàng khớp (FR-INT-01) | S | S | S5-C6, D6 | BR-19 | UC-11 |
| FR-BIL-10 | **Xác nhận chuyển khoản thủ công**: thu ngân nhập **số tiền và mã tham chiếu** sau khi kiểm tra tiền về trên app ngân hàng. Trường hợp không chắc hoặc ghi đè sau đó thì **quản lý duyệt**. **Không có** chức năng xác nhận bằng ảnh chụp | M | M | S2-C3, S5-BR7 | BR-19 | UC-11 |
| FR-BIL-11 | Trạng thái **"dự định, chưa xác nhận"** cho thẻ hoặc QR khi offline hoặc chưa xác nhận. **Không** tính vào tiền đã nhận. **Hiện ở chốt ca** | M | M | S5-N2, S6-R6 | BR-20 | UC-08 |
| FR-BIL-12 | **Thông tin xuất HĐ theo từng bên thanh toán**: mặc định khách lẻ; hoặc công ty (tên, MST, địa chỉ, email). Một bill hoặc tiệc có **nhiều bên**, mỗi bên thông tin riêng | M | M | S2-C4, S4-F2, S8-Q3 | BR-26 | UC-08 |
| FR-BIL-13 | **Hoàn tiền sau thanh toán**: quản lý duyệt, lý do, phương thức hoàn. Tạo **yêu cầu điều chỉnh HĐ** cho kế toán. Không xoá HĐ đã phát hành | M | M | S2-C6, S4-F2 | BR-25, 27 | UC-12 |
| FR-BIL-14 | **Đổi phương thức thanh toán** sau khi bill đã chốt: bắt buộc lý do, người thực hiện, **người duyệt** | M | M | S4-F11 | BR-27 | UC-12 |
| FR-BIL-15 | Khi đóng bill, sinh **bản ghi dữ liệu HĐĐT** (người mua, dòng hàng, thuế, tổng) vào **hàng chờ xuất HĐ** cho kế toán. Kế toán **nhập lại số HĐ** đã phát hành để đối chiếu | M | M | S4-F2, S5-C3 | BR-25 | UC-29 |
| FR-BIL-16 | **Kiểm tra mức chi tối thiểu** của tiệc hoặc phòng riêng khi thanh toán và **cảnh báo** nếu chưa đạt (cách xử lý chờ OI-02) | — | S | S8-Q1 | BR-13 | UC-16 |
| FR-BIL-17 | **Khách về chưa trả tiền**: giữ bill ở trạng thái "khách nợ", ghi sự việc, cho thu sau; bill vẫn hiện trong báo cáo ngoại lệ | S | S | S2-C6 | — | UC-08 |
| FR-BIL-18 | *(CR-02)* Thu ngân **quét thẻ QR** để mở **bill hiện tại của nhóm** (mọi bàn ghép hoặc chuyển, cọc, giảm giá đã duyệt), có bước **đọc lại số bàn và số tiền**. Quét **không bao giờ** đánh dấu đã trả hay đóng bill | S | S | S11-C1, S12-V8 | BR-66 | UC-41 |

## SHF — Ca và két

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-SHF-01 | **Mở ca**: người mở, két, **quỹ đầu ca** (mặc định 1.000.000đ) | M | M | S2-C5 | BR-22 | UC-13 |
| FR-SHF-02 | **Phiếu chi tiền mặt từ két**: số tiền, lý do, người chi. **Đính kèm ảnh chứng từ trước khi chốt ca**. Vượt ngưỡng (300.000đ) cần chủ duyệt trước; ngoại lệ khẩn cấp thì phải ghi **vì sao không duyệt trước được**. Mua gấp hàng hoá thì tạo luôn **phiếu nhập** (FR-PUR-05) | M | M | S4-F6, S5-D4, S6-R5 | BR-22, 24 | UC-14 |
| FR-SHF-03 | **Giao ca**: đếm tiền (tuỳ chọn đếm theo mệnh giá); **cả hai người xác nhận bằng PIN** | M | M | S2-C5 | BR-23 | UC-13 |
| FR-SHF-04 | **Chốt ca**: tính tiền mặt dự kiến; nhập số đếm thực tế; tính chênh lệch; bắt buộc lý do khi lệch; liệt kê khoản "dự định, chưa xác nhận"; **quản lý kiểm tra và ký**. **Không có** chức năng sửa doanh thu cho khớp két | M | M | S2-C5, S4-F4, S6-R6 | BR-20, 23 | UC-13 |
| FR-SHF-05 | **Sửa số đếm két đã hoàn tất**: bắt buộc lý do, người thực hiện, người duyệt; **giữ bản ghi gốc** | M | M | S4-F11 | BR-27 | UC-13 |
| FR-SHF-06 | Sau chốt ca, **tự gửi báo cáo chốt ca** cho kế toán (thay ảnh Zalo) | M | M | S1-Q6, S4-F4 | — | UC-13 |

## DLV — Mang về và giao hàng

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-DLV-01 | Tạo **đơn mang về** tại quầy: món theo giá kênh mang về, thanh toán, gửi bếp | M | M | S1-Q2 | — | UC-19 |
| FR-DLV-02 | **Nhập đơn app giao hàng**: kênh (GrabFood, ShopeeFood), **mã đơn app bắt buộc** (cảnh báo trùng), món theo **bảng giá app**, giờ nhận; gửi bếp | M | M | S2-M6, S5-C1 | BR-40 | UC-19 |
| FR-DLV-03 | **Trạng thái đơn giao**: đã nhận, đang làm, sẵn sàng, đã giao shipper, huỷ (bắt buộc lý do) | M | M | S2-M6 | BR-05 | UC-19 |
| FR-DLV-04 | **Nhập bảng kê đối soát app** (file hoặc nhập tay): đơn, khuyến mãi, phí, điều chỉnh, số thực nhận; khớp theo mã đơn | S | M | S4-F3, S6-R8 | — | UC-28 |
| FR-DLV-05 | **Kết nối trực tiếp** với app giao hàng để nhận đơn tự động | — | C | S4-O10 | — | UC-19 |
| FR-DLV-06 | **Đồng bộ trạng thái hết món** lên app giao hàng qua kết nối | — | S | S4-O10 | BR-18 | UC-05 |

## INV — Kho, định lượng, kiểm kê

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-INV-01 | **Danh mục hàng kho** (nguyên liệu, bán thành phẩm, đồ uống, vật tư), **đơn vị cơ sở** và **bảng quy đổi** (túi, hộp, thùng → đơn vị cơ sở) | S | M | S3-P1, U6, S5-C2 | BR-36 | UC-23 |
| FR-INV-02 | **Kho theo địa điểm**: mỗi quán một kho; **bếp sơ chế là một kho riêng** tại Đống Đa | S | M | S3-P4 | — | UC-23 |
| FR-INV-03 | **Định lượng** món và size → hàng kho. **Công thức chi tiết** (sốt, nước lẩu) chỉ bếp trưởng và chủ xem; người khác chỉ thấy tên món và chất gây dị ứng | S | M | S3-K7, S5-BR16 | BR-28 | UC-24 |
| FR-INV-04 | Tính **tiêu hao lý thuyết** = số bán × định lượng, cho **mặt hàng ưu tiên** | S | M | S3-A1, S6-R8 | — | UC-23 |
| FR-INV-05 | **Phiếu kiểm kê** theo danh sách mặt hàng và lịch (bia mỗi tối, thịt 2 lần/tuần, toàn bộ cuối tháng). Nhập theo **đơn vị thực tế**, hệ thống tự quy đổi | S | M | S3-U6 | BR-36 | UC-23 |
| FR-INV-06 | **Báo cáo chênh lệch** theo kỳ và mặt hàng, **tách theo loại giao dịch** (bán, nhập, chuyển đến, chuyển đi, hỏng, bữa nhân viên, khuyến mãi, bể vỡ), **so cùng đơn vị** | S | M | S5-A-G4 | BR-36 | UC-23 |
| FR-INV-07 | Ghi **hao hỏng, bể vỡ, bữa ăn nhân viên, hàng khuyến mãi** (nhận từ nhà phân phối, tặng khách), kèm lý do | S | M | S3-K8, U5 | BR-09 | UC-07 |
| FR-INV-08 | **Điều chỉnh kho**: bắt buộc lý do, người thực hiện, **người duyệt**; giữ bản ghi gốc | S | M | S4-F11 | BR-27 | UC-23 |
| FR-INV-09 | Theo dõi **két và vỏ chai ký cược** (nhận, trả nhà phân phối) | C | S | S3-U5 | — | UC-22 |
| FR-INV-10 | Cập nhật **giá vốn nguyên liệu** từ giá thực tế trên phiếu nhận hàng (để tính giá chuyển và giá vốn) | S | M | S3-U1, S4-F8 | BR-35 | UC-22 |

## PRP — Sơ chế và chuyển kho

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-PRP-01 | **Yêu cầu hàng sơ chế** từ quán cho ngày hôm sau (thay nhóm Zalo): mặt hàng, số lượng, đơn vị, hạn chót gửi | S | M | S3-P2 | — | UC-20 |
| FR-PRP-02 | **Lệnh sản xuất sơ chế**: nguyên liệu vào (cân), thành phẩm ra (cân hoặc đếm gói), **tỷ lệ thành phẩm theo lô**, ngày làm, hạn dùng | S | M | S3-P1, P3 | BR-35 | UC-20 |
| FR-PRP-03 | **Phiếu chuyển kho**: bên gửi ghi xuất; bên nhận **xác nhận số thực nhận**. Chênh lệch, hàng **rò hoặc hỏng** thì chuyển trạng thái **tranh chấp**; bằng chứng bổ sung sau | S | M | S3-P2, S5-BR13 | BR-34 | UC-21 |
| FR-PRP-04 | **Chuyển hoặc cho mượn hàng giữa các quán**, dùng cùng cơ chế phiếu chuyển | S | M | S3-P2 | BR-34 | UC-21 |
| FR-PRP-05 | **Giá chuyển nội bộ** = giá nguyên liệu sống điều chỉnh theo tỷ lệ thành phẩm đo được. **Không tính** cho quán nhận khi phiếu đang tranh chấp hoặc chưa nhận | S | M | S4-F8, S5-BR13 | BR-34, 35 | UC-21 |
| FR-PRP-06 | **Hàng trả về bếp sơ chế** (hộp còn niêm phong) bằng phiếu chuyển ngược | S | M | S3-P2 | BR-34 | UC-21 |
| FR-PRP-07 | Xuất **dữ liệu để phân bổ tháng** lương và chi phí sơ chế (giá trị mỗi quán đã nhận) | C | S | S4-F8 | BR-35 | UC-29 |

## PUR — Mua hàng và nhận hàng

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-PUR-01 | **Danh mục nhà cung cấp**: liên hệ, mặt hàng, **giá thoả thuận có hiệu lực**, có xuất HĐ VAT hay không | C | S | S3-U1, U4 | — | UC-25 |
| FR-PUR-02 | **Đề xuất mua** từ quán hoặc bếp; tổng hợp cho điều phối mua hàng | C | S | S3-U2 | — | UC-25 |
| FR-PUR-03 | **Đơn mua** gửi nhà cung cấp (in hoặc chia sẻ PDF, ảnh) | C | S | S3-U1 | — | UC-25 |
| FR-PUR-04 | **Phiếu nhận hàng** (tối thiểu cho mặt hàng ưu tiên): số lượng giao, nhận, chấp nhận; **giá phiếu giao so với giá thoả thuận** (đánh dấu chênh); **nhiệt độ và giờ** với thịt lạnh và hải sản; chất lượng; ảnh (bổ sung sau). **Không xác nhận được khi chưa nhập số kiểm** | S | M | S3-U3, S5-BR12, S6-R10 | BR-32, 33 | UC-22 |
| FR-PUR-05 | **Mua gấp bằng tiền mặt**: phiếu chi (FR-SHF-02) kèm phiếu nhập; **thông báo ngay** cho điều phối mua hàng | S | M | S3-U2 | BR-24 | UC-14 |
| FR-PUR-06 | **Tranh chấp với nhà cung cấp**: ghi thiếu, hỏng, chênh giá, bằng chứng; theo dõi trạng thái đổi hàng hoặc giảm tiền | C | S | S3-U3 | — | UC-22 |
| FR-PUR-07 | **Báo cáo biến động giá** nhà cung cấp theo tuần | C | S | S4-F10 | — | UC-25 |
| FR-PUR-08 | **Tồn tối thiểu** và gợi ý số lượng đặt | — | C | S3-U2 | — | UC-25 |

## CUS — Khách hàng

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-CUS-01 | **Danh sách khách chung toàn chuỗi**, khoá theo số điện thoại đã chuẩn hoá; **phát hiện trùng** | C | S | S4-O4, Z1 | — | UC-32 |
| FR-CUS-02 | Lưu **trạng thái đồng ý nhận tiếp thị** (kênh, thời điểm, cách thu thập) và **từ chối nhận tin** | C | S | S4-O4, S5-BR15 | BR-30 | UC-32 |
| FR-CUS-03 | **Lịch sử ghé** (booking, bill) theo khách | C | S | S4-O4 | — | UC-32 |
| FR-CUS-04 | **Ưu đãi hoặc tích điểm đơn giản**, voucher | — | S | S4-O10 | BR-30 | UC-32 |
| FR-CUS-05 | Xử lý **quyền của chủ thể dữ liệu** (xem, sửa, xoá hoặc ẩn danh theo yêu cầu, trong giới hạn lưu trữ luật định) | C | S | S5-N5 | CON-07 | UC-32 |

## RPT — Báo cáo, dashboard, cảnh báo

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-RPT-01 | **Dashboard của chủ** trên điện thoại, **cùng đêm**, theo quán: doanh thu trước và sau giảm giá, theo kênh, theo phương thức; lệch két; tổng QR và thẻ; đơn app; giảm giá; huỷ món; **khoản chưa giải quyết** | M | M | S1-Q9, S4-O6 | — | UC-27 |
| FR-RPT-02 | **Màn hình quản lý trong ca** (quán mình): bàn đang mở, **order chưa tới bếp**, món chờ lâu, món hết, thanh toán chưa xác nhận, **tiệc sắp tới** (cọc, set, bàn) | M | M | S2-M8, S8-B1 | — | UC-27 |
| FR-RPT-03 | **Báo cáo cuối ngày cho kế toán** theo quán: doanh thu trước và sau giảm giá; thuế và số đã xuất HĐ; thanh toán theo phương thức; quỹ đầu ca; chi tiền mặt; tiền mặt dự kiến và đếm; cọc nhận và cấn trừ; hoàn tiền; **chênh lệch chưa giải quyết** | M | M | S4-F4 | — | UC-28 |
| FR-RPT-04 | **Cảnh báo tức thời** cho chủ theo BR-29. Các điều chỉnh thông thường chỉ đưa vào báo cáo đêm | M | M | S4-O6, S5-BR17 | BR-29 | UC-27 |
| FR-RPT-05 | **Báo cáo khớp thanh toán**: tiền mặt ↔ số đếm; QR và chuyển khoản ↔ giao dịch ngân hàng; thẻ ↔ bảng kê thẻ; app ↔ bảng kê app. Chênh lệch do thời điểm hiển thị riêng | S | M | S4-F4, S6-R8 | — | UC-28 |
| FR-RPT-06 | **Báo cáo tuần**: món bán chạy; mua hàng; biến động giá nhà cung cấp; tồn lý thuyết so với kiểm thực tế của mặt hàng chọn | S | M | S4-F10 | — | UC-27 |
| FR-RPT-07 | **Báo cáo ngoại lệ**: giảm giá, huỷ, hoàn tiền, điều chỉnh, dự phòng, kèm lý do, người làm, người duyệt; lọc riêng lý do "Khác" | M | M | S4-F11, S5-BR2 | BR-05, 27 | UC-27 |
| FR-RPT-08 | **Báo cáo lỗi món** (sót, trùng) theo nguyên nhân và theo tuần, để đo G3 | M | M | S5-A-G3 | — | UC-27 |
| FR-RPT-09 | **Kiểm tra cuối ngày cho quản lý**: bàn đã chuyển, order tiệc thêm, bill chưa đóng; cảnh báo món có thể nằm sai bill | S | M | S8-B1 | — | UC-27 |

## INT — Tích hợp và xuất dữ liệu

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-INT-01 | Nhận **thông báo giao dịch** của tài khoản thu (API ngân hàng hoặc dịch vụ trung gian), **tự khớp** theo mã quán, mã bill và số tiền. Giao dịch không khớp đưa vào hàng chờ xử lý tay | S | S | S5-C6, D6 | BR-19 | UC-11 |
| FR-INT-02 | **Xuất dữ liệu HĐĐT** theo mẫu nhập của nhà cung cấp HĐĐT hiện tại (MISA meInvoice) từ hàng chờ xuất (FR-BIL-15) | M | M | S5-C3, A1 | BR-25 | UC-29 |
| FR-INT-03 | **Kết nối API** với nhà cung cấp HĐĐT để phát hành HĐ từ hệ thống (sau khi đã kiểm chứng) | C | S | S4-O10, S5-C4 | BR-25 | UC-29 |
| FR-INT-04 | **Xuất dữ liệu kế toán** (bán hàng; nhập hàng: nhà cung cấp, số chứng từ, ngày, mặt hàng, số lượng, thuế, giá trị) theo định dạng **đã được kế toán thử với MISA** | S | M | S4-F7, S6-R8 | — | UC-29 |
| FR-INT-05 | **Xuất toàn bộ dữ liệu** của công ty (CSV/Excel) theo yêu cầu, và **truy xuất giao dịch cũ** | M | M | S4-O8, S5-N4 | BR-31 | UC-29 |
| FR-INT-06 | **Gửi tin xác nhận booking** qua kênh nhắn tin (Zalo hoặc SMS), hoặc tạo **mẫu tin để nhân viên gửi** và ghi nhận đã gửi | S | M | S5-D3 | BR-11 | UC-15 |

## OFF — Offline và khôi phục

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-OFF-01 | Khi **mất Internet**, trong quán vẫn chạy: gọi món, in phiếu bếp, màn hình bếp, bill, tiền mặt, ghi thẻ và QR ở trạng thái "chưa xác nhận" | M | M | S1-Q5, S5-N2 | BR-20 | UC-31 |
| FR-OFF-02 | Mọi thiết bị hiện **trạng thái kết nối** (Internet; máy chủ tại quán). Ghi thời điểm mất và khôi phục | M | M | S2-M7 | BR-29 | UC-31 |
| FR-OFF-03 | Có mạng lại thì **tự đồng bộ**, **không tạo trùng** order, phiếu hay giao dịch; có nhật ký đồng bộ | M | M | S5-N2 | — | UC-31 |
| FR-OFF-04 | **Chế độ khôi phục phiếu giấy**: nhập order từ phiếu giấy đánh số, cờ **mặc định "đã phục vụ, không gửi bếp"**; chỉ gửi bếp khi xác nhận món chưa làm | M | M | S8-T1, T4 | BR-38 | UC-31 |
| FR-OFF-05 | **Danh sách đối chiếu khôi phục**: người được chỉ định đối chiếu phiếu giấy với order; **quản lý ký trước khi chốt ca** | M | M | S6-R9 | BR-39 | UC-31 |
| FR-OFF-06 | Máy cầm tay **mất liên lạc với máy chủ tại quán** (ví dụ mất Wi-Fi): **không mở được bàn mới**; món đang nhập được giữ là **bản nháp CHƯA GỬI** hiển thị nổi bật; **không tự gửi** khi có kết nối lại. Nhân viên chọn **Gửi** (nếu món chưa được làm bằng phiếu giấy) hoặc **Huỷ bản nháp**; gửi lại vẫn không tạo trùng (mã idempotency). *(Sửa theo CR-02, S12-V10: trước đây máy tự gửi lại)* | M | M | S8-T2, S12-V10 | BR-68 | UC-01 |

## AUD — Nhật ký và phê duyệt

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-AUD-01 | **Nhật ký không sửa được** cho thao tác nhạy cảm: ai, làm gì, lúc nào, thiết bị nào, giá trị trước và sau, lý do, người duyệt | M | M | S4-F11, S5-BR10 | BR-27 | UC-30 |
| FR-AUD-02 | **Không sửa đè**: mọi điều chỉnh tạo bản ghi mới liên kết với bản ghi gốc | M | M | S5-BR10 | BR-27 | — |
| FR-AUD-03 | **Hàng đợi phê duyệt**: người duyệt xem, duyệt hoặc từ chối; yêu cầu có hạn và trạng thái hết hạn (kích hoạt dự phòng nếu áp dụng) | M | M | S4-O1, S5-D2 | BR-03 | UC-10 |
| FR-AUD-04 | **Tra cứu lịch sử** theo bill, bàn, người, khoảng thời gian | M | M | S4-F11 | — | UC-28 |

## GST — Khách tự phục vụ qua QR (CR-01)

> **QR-1** là thí điểm 6 bàn ở Đống Đa: nhân viên mở bàn và nhận order đầu, khách gọi **thêm** qua QR, **mọi đơn QR chờ nhân viên xác nhận**. **QR-2** cần chủ quyết riêng sau khi hệ thống lõi ổn định.
> Các FR của GST phụ thuộc vào **duyệt mua và xây** sau kiểm tra kỹ thuật (CR-01 §6), nên được xếp **S** chứ không phải M. Tự thanh toán chỉ được bật khi đạt BR-59. **Ngoại lệ:** FR-GST-01 (thẻ QR mọi bàn) là **M** từ CR-02, vì nhân viên dùng thẻ để mở bàn (FR-TBL-07).

| ID | Yêu cầu | TĐ | TC | Nguồn | BR | UC |
|---|---|---|---|---|---|---|
| FR-GST-01 | **Thẻ QR cho mọi bàn** (không chỉ bàn thí điểm, CR-02): token ngẫu nhiên ≥ 128 bit, thẻ **chống bóc dán** ghi **tên bàn và khu** đúng như app. In hàng loạt theo quán, có **thẻ dự phòng**; phòng riêng mỗi bàn một thẻ; **nội dung thẻ bàn thí điểm và bàn thường khác nhau**. Quản lý **cấp lại** mã (mã cũ mất hiệu lực) khi nghi bị giả hoặc thẻ hỏng | M | M | S9-Z1, S10-R13, S11-L6, S12-V3 | BR-53, 61 | UC-39 |
| FR-GST-02 | **Mã ngồi bàn**: sinh khi mở bàn (4–6 ký tự), hiện trên máy của nhân viên để ghi lên thẻ đưa khách; **đổi mỗi lượt khách**; **không** in lên phiếu bếp. Khách nhập một lần cho mỗi điện thoại. **Sai 5 lần thì khoá 15 phút** và báo quản lý | S | S | S10-R2 | BR-57 | UC-33 |
| FR-GST-03 | **Phiên khách**: chỉ tạo được khi bàn **đang mở** và mã ngồi bàn đúng. Hết hạn nếu không hoạt động (tham số, mặc định 30 phút) và **kết thúc khi bill đóng**. Khi nhóm chuyển bàn, phiên **đi theo bill**, bàn cũ ngừng nhận đơn | S | S | S9-L1, S10-R1 | BR-41 | UC-33 |
| FR-GST-04 | **Thực đơn QR**: giá kênh tại chỗ, ảnh và mô tả, món hết hiện **HẾT**. Món chỉ gọi qua nhân viên thì có nhãn riêng. **Giờ gọi cuối**: món ăn 21:45; đồ uống chai tới lệnh gọi cuối của quản lý (~22:15) | S | S | S9-K4, S10-R5 | BR-46 | UC-33 |
| FR-GST-05 | Khách **gửi món thêm từ điện thoại của mình** vào **bill chung của bàn**; không có giỏ chung; mỗi dòng gắn với điện thoại gửi; gửi lại không tạo trùng (mã idempotency) | S | S | S9-L4 | BR-44 | UC-34 |
| FR-GST-06 | **Hàng chờ xác nhận đơn QR** trên máy của phục vụ khu vực (thu ngân dự phòng): xem, **xác nhận**, **từ chối** (bắt buộc lý do), liên hệ khách. **Đo thời gian chờ**; đơn chờ quá 1 phút (tham số) được tô nổi | S | S | S9-L2, S10-Q1 | BR-42 | UC-35 |
| FR-GST-07 | Tự đánh dấu **luôn chờ nhân viên**: bia và đồ có cồn (kèm nhắc **kiểm tra tuổi khi mang ra**); ghi chú dị ứng (bắt buộc bước "đã trao đổi với khách" và bếp trưởng xem); số lượng vượt ngưỡng; set lẩu bị đổi; món "ra sau" | S | S | S9-K2, K3, F4, S10-Q4 | BR-43, BR-37 | UC-35 |
| FR-GST-08 | **Cảnh báo nghi trùng**: món gắn cờ gọi lại từ cùng bàn trong 2 phút (tham số) được tô nổi cho người xác nhận; **không tự xoá** | S | S | S9-L4, S10-R3 | BR-44 | UC-35 |
| FR-GST-09 | Khách **rút lại** món khi món **còn chờ xác nhận**. Sau khi xác nhận, nút rút lại đổi thành **"Gọi nhân viên"** | S | S | S9-L5, S10-R4 | BR-45 | UC-34 |
| FR-GST-10 | **Trạng thái món realtime** cho khách: *Chờ nhân viên xác nhận* → *Bếp đã nhận* → *Xong* (**chỉ khi bật cấu hình**) → *Đã phục vụ*. Không hiện "đang nấu", không hứa giờ. Có **dòng thời gian** theo từng món | S | S | S9-K1, S10-Q1 | BR-47 | UC-36 |
| FR-GST-11 | Nút **Gọi nhân viên**, **Yêu cầu tính tiền**, và danh sách yêu cầu nhanh ngắn (đá, khăn giấy, nước chấm), chuyển tới phục vụ khu vực. Yêu cầu dị ứng hoặc thanh toán được **đánh dấu khẩn**. Đo thời gian phản hồi (mục tiêu nội bộ 2–3 phút) | S | S | S9-L6, S10-R6 | — | UC-37 |
| FR-GST-12 | **Công tắc QR** theo bàn, khu, quán: tạm dừng hoặc bật lại **không đóng bill**. Mất kết nối edge–cloud thì **tự tạm dừng**; trang khách hiện "tạm dừng gọi món, vui lòng gọi nhân viên"; đơn gửi trong lúc tạm dừng bị **từ chối ngay**, **không bao giờ giao sau** | S | S | S9-T1, S10-Q1, R12 | BR-55, BR-56 | UC-39 |
| FR-GST-13 | Khách **xem bill của bàn**: món, giảm giá đã duyệt, **cọc đã trả**, số còn phải trả. Nút tự thanh toán **chỉ bật** khi bill đã chốt (đã yêu cầu tính tiền, không còn duyệt treo, không còn đơn QR chờ xác nhận) | S | S | S9-C1, C4, S10-R7 | BR-48 | UC-38 |
| FR-GST-14 | **Tự thanh toán chuyển khoản** (cả bill): tạo **lệnh thanh toán** đúng số còn phải trả, kèm **mã tham chiếu duy nhất**. Hiện QR VietQR, nút **mở app ngân hàng** (deeplink), nút **lưu ảnh QR**, **tên và số tài khoản công ty**. Sau đó màn hình "đang chờ ngân hàng xác nhận", **không** gợi ý trả lần hai | S | S | S9-C1, Z1, S10-R7, R13 | BR-48, 49, 51, 53, 59 | UC-38 |
| FR-GST-15 | **Tự xác nhận**: nhận thông báo từ tuyến ngân hàng **đã được duyệt** (không dùng mật khẩu ngân hàng), **xác thực chữ ký**, khớp mã tham chiếu và số tiền. Đủ tiền thì bill trả xong, báo khách và nhân viên. Thiếu thì **"trả một phần"**. Sai mã hoặc thừa thì vào **hàng chờ không khớp**, không đoán bàn | S | S | S9-C2, F1, S10-R7, C2 | BR-49, 60 | UC-38 |
| FR-GST-16 | **Màn hình theo dõi tự thanh toán** cho thu ngân: trạng thái lệnh (*đã mở, chờ xác nhận, đã xác nhận, trả một phần, không khớp*), số tiền kỳ vọng, mã bill, giao dịch ngân hàng liên quan; bill của khách **đã rời đi khi còn chờ** được đánh dấu | S | S | S9-C2, C3, S10-R8 | BR-49, 51 | UC-40 |
| FR-GST-17 | **Nhả bàn sau tự thanh toán**: phục vụ thấy trạng thái đã trả (realtime) trước khi dọn và nhả bàn | S | S | S9-C3, S10-R9 | BR-52 | UC-38 |
| FR-GST-18 | Khách **nhập tuỳ chọn** thông tin HĐ công ty (tên, MST, địa chỉ, email), **xem lại trước khi gửi**; thông tin vào hàng chờ HĐĐT để kế toán kiểm tra. Số điện thoại tuỳ chọn. **Ô đồng ý tiếp thị riêng, không tích sẵn** | S | S | S9-O3, F2, S10-R11 | BR-54, 30 | UC-38 |
| FR-GST-19 | **Trả trùng, trả thừa, hoàn tiền** cho khoản tự thanh toán: ghi tiền vào và tiền ra, tạo việc **hoàn về tài khoản gốc** sau khi xác minh; theo dõi hạn "ngày làm việc kế tiếp" | S | S | S9-F3, S10-R10 | BR-50 | UC-40 |
| FR-GST-20 | **Tiệc phòng riêng**: QR chỉ mở cho gọi thêm sau khi quản lý **xác nhận set đặt trước**; thanh toán tiệc lớn hoặc nhiều công ty **chỉ qua thu ngân** | — | S | S9-Q1, S10-Q3 | BR-58 | UC-34 |
| FR-GST-21 | **Chỉ số QR**: tỷ lệ món thêm qua QR ở bàn thí điểm (G6); thời gian chờ xác nhận; thời gian từ "yêu cầu tính tiền" tới xác nhận (G7); số ca ngân hàng xác nhận chậm **ghi riêng**; thời gian phản hồi gọi nhân viên | S | S | S9-O1, S10-G6, G7 | — | UC-27 |
| FR-GST-22 | **QR-2:** tự nhận món thêm thông thường (trừ nhóm ở BR-43) theo cấu hình từng quán; **mã ngồi bàn vẫn bắt buộc** | — | C | S10-Q3 | BR-42, 43, 57 | UC-34 |
| FR-GST-23 | **QR-2:** khách tự gọi từ **món đầu tiên** (không cần nhân viên nhận order đầu) | — | C | S9-O2, S10-Q3 | BR-42 | UC-34 |
| FR-GST-24 | **QR-2:** **tách tự thanh toán**, mỗi khách trả phần của mình; bill đóng khi đủ tiền | — | C | S9-C1, S10-Q3 | BR-48, 49 | UC-38 |
| FR-GST-25 | *(CR-02)* Sau khi quét bàn, phục vụ thấy **đơn QR đang chờ của bàn đó** và **xác nhận ngay tại bàn**. Kiểm tra tuổi khi gọi bia và trao đổi về dị ứng **không có đường tắt** | S | S | S11-L3, S12-V6 | BR-43, 64 | UC-35 |
| FR-GST-26 | *(CR-02)* Khách quét thẻ ở **bàn không thí điểm**: xem thực đơn **chỉ đọc**, kèm lời nhắn nhờ nhân viên gọi món; không có nút gửi món hay gọi nhân viên (trong QR-1) | S | S | S12-V9 | BR-67 | UC-33 |

---

## Không nằm trong phạm vi (Won't)

| Mục | Lý do | Nguồn |
|---|---|---|
| Chấm công, tính lương | Kế toán giữ ở Excel; chính sách lao động chưa chuẩn hoá | S4-F9 |
| Công nợ nhà cung cấp | Đang quản lý ở MISA, không muốn có số dư thứ hai | S4-F7 |
| Website đặt món riêng (đặt từ xa, giao hàng) | Không dùng tiền đợt thí điểm. *"Khách gọi món bằng QR tại bàn"* **đã được đưa vào phạm vi** qua CR-01 (module GST) | S4-O9, O10, CR-01 |
| Camera nhận diện đĩa | Đắt, không phải yêu cầu nghiêm túc | S4-O9, S5-C5 |
| Báo cáo lãi lỗ đầy đủ | Hoàn thiện ở phần mềm kế toán | S4-F10 |
