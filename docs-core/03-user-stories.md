# 3. User stories

Mẫu: *Là [vai trò], tôi muốn [việc] để [lợi ích]*. Tiêu chí chấp nhận (AC) viết dạng **Khi … thì …**. Mỗi AC ứng với ít nhất một test (xem [README](README.md#truy-vết)).

## Quản trị (ADMIN)

### US-01 Đăng nhập — FR-01.1, FR-01.3
Là **nhân viên**, tôi muốn đăng nhập bằng tài khoản riêng để dùng đúng chức năng của vai trò mình.
- AC1: Khi nhập đúng tên và mật khẩu thì vào màn hình theo vai trò: phục vụ → sơ đồ bàn; bếp → màn hình bếp; thu ngân → quầy thu ngân; quản lý và quản trị → báo cáo.
- AC2: Khi sai mật khẩu thì báo "Sai tên đăng nhập hoặc mật khẩu", không nói rõ sai phần nào.
- AC3: Khi tài khoản bị khoá thì không đăng nhập được.

### US-02 Quản lý tài khoản nhân viên — FR-02.1, FR-02.3
Là **quản trị**, tôi muốn tạo tài khoản và gán vai trò để mỗi người chỉ làm việc của mình.
- AC1: Khi tạo tài khoản trùng tên đăng nhập thì báo lỗi.
- AC2: Khi đặt lại mật khẩu thì nhân viên đăng nhập được bằng mật khẩu mới.
- AC3: Khi phục vụ gọi API tạo nhân viên thì nhận 403.

### US-03 Khoá tài khoản — FR-02.2, FR-01.3
Là **quản trị**, tôi muốn khoá tài khoản người đã nghỉ để họ không vào hệ thống được nữa.
- AC1: Khi khoá tài khoản đang đăng nhập thì yêu cầu kế tiếp của người đó bị từ chối (401).
- AC2: Nhân viên không có nút xoá, chỉ có khoá và mở khoá.

## Quản lý (MANAGER)

### US-04 Quản lý thực đơn — FR-03.1, FR-03.2
Là **quản lý**, tôi muốn sửa món và giá để thực đơn luôn đúng.
- AC1: Khi đổi giá món thì đơn mới dùng giá mới, đơn cũ giữ giá cũ.
- AC2: Khi xoá món đã từng được gọi thì báo "Món đã có trong đơn, hãy đánh dấu hết món".

### US-05 Quản lý bàn và in QR — FR-04.1, FR-04.2, FR-04.3
Là **quản lý**, tôi muốn in thẻ QR cho từng bàn để khách tự gọi món.
- AC1: Mỗi bàn có một mã QR khác nhau, in ra có tên bàn.
- AC2: Khi tạo lại mã QR thì quét thẻ cũ báo "Mã QR không còn hiệu lực".

### US-06 Quản lý kho — FR-09.1 → FR-09.4
Là **quản lý**, tôi muốn ghi nhập, xuất và kiểm kê để biết tồn kho thật.
- AC1: Khi xuất nhiều hơn số tồn thì báo lỗi, tồn không đổi.
- AC2: Khi kiểm kê nhập số thực tế thì hệ thống ghi phiếu điều chỉnh bằng phần chênh lệch.
- AC3: Khi tồn ≤ mức tối thiểu thì nguyên liệu hiện nhãn "Sắp hết".

### US-07 Xem báo cáo — FR-10.1 → FR-10.3
Là **quản lý**, tôi muốn xem doanh thu và món bán chạy để quyết định nhập hàng và thực đơn.
- AC1: Doanh thu chỉ tính khoản đã xác nhận, theo ngày giờ Việt Nam.
- AC2: Món bị huỷ không tính vào món bán chạy.

## Phục vụ (WAITER)

### US-08 Mở đơn và gọi món — FR-05.1 → FR-05.3
Là **phục vụ**, tôi muốn gọi món trên điện thoại để bếp nhận ngay, không phải chạy xuống bếp.
- AC1: Khi mở đơn cho bàn đang có khách thì báo "Bàn đã có đơn đang mở".
- AC2: Khi gửi món thì món vào cột Chờ làm ở bếp trong ≤ 2 giây.
- AC3: Món đang hết không chọn được.

### US-09 Xác nhận đơn QR của khách — FR-06.3
Là **phục vụ**, tôi muốn kiểm tra đơn khách tự gọi trước khi vào bếp để tránh đơn nhầm hoặc đơn phá.
- AC1: Khi khách gửi món thì sơ đồ bàn hiện dấu "chờ xác nhận" trong ≤ 2 giây.
- AC2: Món chờ xác nhận **không hiện ở bếp**.
- AC3: Khi xác nhận thì món vào bếp. Khi từ chối thì khách thấy lý do.

### US-10 Ra món — FR-05.5
Là **phục vụ**, tôi muốn biết món nào đã xong để mang ra ngay.
- AC1: Khi bếp bấm Xong thì sơ đồ bàn hiện dấu "có món xong".
- AC2: Khi bấm Đã ra thì món biến khỏi cột Xong của bếp.

### US-11 Huỷ món — FR-05.4, FR-05.6
Là **phục vụ**, tôi muốn huỷ món khách đổi ý khi bếp chưa làm.
- AC1: Món Chờ làm: phục vụ huỷ được.
- AC2: Món Đang làm hoặc Xong: chỉ quản lý huỷ được và phải nhập lý do.
- AC3: Món đã ra thì không huỷ được.

## Bếp (CHEF)

### US-12 Màn hình bếp — FR-07.1 → FR-07.3
Là **bếp**, tôi muốn thấy món cần làm theo thứ tự để làm đúng và đủ.
- AC1: Món sắp theo thời gian gửi, món chờ lâu nhất ở trên.
- AC2: Chỉ đổi trạng thái theo chiều tiến: Chờ làm → Đang làm → Xong.

### US-13 Báo hết món — FR-03.3
Là **bếp**, tôi muốn báo hết món để phục vụ và khách không gọi món đó nữa.
- AC1: Khi báo hết thì món biến khỏi thực đơn của khách và bị khoá trên máy phục vụ.

## Khách (không đăng nhập)

### US-14 Gọi món bằng QR — FR-06.1, FR-06.2, FR-06.5
Là **khách**, tôi muốn quét QR trên bàn để tự xem thực đơn và gọi món, không phải chờ nhân viên.
- AC1: Quét QR mở trang có tên bàn, không cần đăng nhập hay cài app.
- AC2: Khi gửi món thì các món hiện "Chờ xác nhận".
- AC3: Khi bàn đang có đơn thì món mới được thêm vào đơn đó.

### US-15 Theo dõi món — FR-06.4
Là **khách**, tôi muốn biết món của mình đang ở đâu để khỏi phải hỏi nhân viên.
- AC1: Khi bếp đổi trạng thái thì điện thoại khách cập nhật trong ≤ 2 giây, không cần tải lại.
- AC2: Trang chỉ hiện đơn của bàn này.

### US-16 Tự thanh toán chuyển khoản — FR-08.5, FR-08.4
Là **khách**, tôi muốn tự quét VietQR để trả tiền mà không phải ra quầy.
- AC1: Nút "Thanh toán" chỉ bật khi không còn món chờ xác nhận.
- AC2: Mã VietQR có đúng số tiền và nội dung là mã thanh toán.
- AC3: Khi ngân hàng báo có đúng số tiền thì trang hiện "Đã thanh toán" và bàn trở lại trống.

## Thu ngân (CASHIER)

### US-17 Thu tiền mặt — FR-08.1, FR-08.2
Là **thu ngân**, tôi muốn nhập tiền khách đưa để biết tiền thối và đóng bàn.
- AC1: Khi tiền khách đưa nhỏ hơn tổng thì không cho xác nhận.
- AC2: Khi thanh toán xong thì đơn đóng, bàn trống.

### US-18 Thu chuyển khoản tự xác nhận — FR-08.3, FR-08.4
Là **thu ngân**, tôi muốn hệ thống tự báo khi tiền về để không phải dò app ngân hàng.
- AC1: Khi webhook hợp lệ tới với đúng mã và đúng tiền thì màn hình thu ngân hiện "Đã nhận tiền" trong ≤ 2 giây.
- AC2: Khi webhook gửi lặp cùng một giao dịch thì không ghi nhận hai lần.
- AC3: Khi webhook sai API key thì bị từ chối (401).

### US-19 Xác nhận tay và giao dịch không khớp — FR-08.6, FR-08.7
Là **thu ngân**, tôi muốn xử lý khi tiền về nhưng hệ thống không tự khớp.
- AC1: Khi khách chuyển sai số tiền thì giao dịch vào danh sách "Không khớp", đơn chưa đóng.
- AC2: Khi thu ngân xác nhận tay thì hệ thống ghi tên người xác nhận.
