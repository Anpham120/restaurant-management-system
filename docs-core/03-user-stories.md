# 3. User stories

Mẫu: *Là [vai trò], tôi muốn [việc] để [lợi ích]*. Tiêu chí chấp nhận (AC) viết dạng **Khi … thì …**. Mỗi AC ứng với ít nhất một test (xem [README](README.md#truy-vết)).

## Quản trị (ADMIN)

### US-01 Đăng nhập — FR-01.1, FR-01.3, FR-01.5
Là **nhân viên**, tôi muốn đăng nhập bằng tài khoản riêng để dùng đúng chức năng của vai trò mình.
- AC1: Khi nhập đúng tên và mật khẩu thì vào màn hình theo vai trò: phục vụ → sơ đồ bàn; bếp → màn hình bếp; thu ngân → quầy thu ngân; quản lý và quản trị → báo cáo.
- AC2: Khi sai mật khẩu thì báo "Sai tên đăng nhập hoặc mật khẩu", không nói rõ sai phần nào.
- AC3: Khi tài khoản bị khoá thì không đăng nhập được.
- AC4: Khi một tên đăng nhập bị thử quá 10 lần trong một phút thì lần tiếp theo bị chặn, kèm thời gian phải chờ.

### US-02 Quản lý tài khoản nhân viên — FR-02.1, FR-02.3
Là **quản trị**, tôi muốn tạo tài khoản và gán vai trò để mỗi người chỉ làm việc của mình.
- AC1: Khi tạo tài khoản trùng tên đăng nhập thì báo lỗi.
- AC2: Khi đặt lại mật khẩu thì nhân viên đăng nhập được bằng mật khẩu mới.
- AC3: Khi phục vụ gọi API tạo nhân viên thì nhận 403.

### US-03 Khoá tài khoản — FR-02.2, FR-01.3
Là **quản trị**, tôi muốn khoá tài khoản người đã nghỉ để họ không vào hệ thống được nữa.
- AC1: Khi khoá tài khoản đang đăng nhập thì yêu cầu kế tiếp của người đó bị từ chối (401).
- AC2: Nhân viên không có nút xoá, chỉ có khoá và mở khoá.

### US-36 Thu hồi phiên đăng nhập — FR-01.6, FR-01.7
Là **nhân viên**, tôi muốn đổi mật khẩu hoặc đăng xuất mọi thiết bị, để không ai dùng được phiên cũ, ví dụ khi mất điện thoại.
- AC1: Đổi mật khẩu ở máy A thì máy B đang đăng nhập bị từ chối (401) ở yêu cầu kế tiếp và về trang đăng nhập; máy A vẫn dùng tiếp.
- AC2: Quản trị đặt lại mật khẩu thì mọi phiên của nhân viên đó hết hiệu lực.
- AC3: Bấm "Đăng xuất mọi thiết bị" thì mọi phiên, kể cả máy đang dùng, hết hiệu lực; đăng nhập lại thì dùng bình thường.
- AC4: Token đã thu hồi cũng không kết nối realtime được.

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

### US-32 Nhập hàng có giá — FR-09.5 → FR-09.7
Là **quản lý**, tôi muốn ghi phiếu nhập kèm nhà cung cấp và giá mua, để biết đã chi bao nhiêu và giá vốn từng nguyên liệu.
- AC1: Lưu phiếu nhập 10 kg ba chỉ, giá 120.000 đ/kg, từ nhà cung cấp A: tồn ba chỉ tăng 10 kg, lịch sử kho có dòng nhập ghi số phiếu, phiếu có tổng 1.200.000 đ.
- AC2: Ba chỉ đang tồn 12 kg, giá vốn 110.000 đ; nhập thêm 10 kg giá 120.000 đ thì giá vốn mới là (12 × 110.000 + 10 × 120.000) ÷ 22 = 114.545 đ.
- AC3: Phiếu không có dòng nào, có số lượng ≤ 0 hoặc đơn giá âm thì bị từ chối; nhà cung cấp đã ngừng giao dịch thì không lập phiếu được.
- AC4: Phiếu đã lưu không sửa, không xoá được. Nhập, xuất, kiểm kê bằng tay không đổi giá vốn.
- AC5: Chỉ quản lý và quản trị xem, lập phiếu và sửa nhà cung cấp.

### US-33 Định lượng và trừ kho tự động — FR-09.8 → FR-09.10
Là **quản lý**, tôi muốn ghi định lượng cho món để kho tự trừ khi món vào bếp, và so tiêu hao theo định lượng với số kiểm kê.
- AC1: Phở bò định lượng 0,15 kg bắp bò và 0,3 kg bánh phở; phục vụ gọi 2 bát thì bắp bò giảm 0,3 kg, bánh phở giảm 0,6 kg, lịch sử kho có dòng "Bán món" ghi bàn, số đơn và "Phở bò × 2".
- AC2: Khách gọi qua QR thì kho chưa trừ; nhân viên xác nhận món thì mới trừ.
- AC3: Huỷ món còn Chờ làm thì kho được hoàn đúng lượng đã trừ; huỷ món Đang làm thì không hoàn.
- AC4: Tồn không đủ thì món vẫn vào bếp, tồn thành số âm và hiện "Sắp hết". Xuất tay vẫn không được vượt tồn.
- AC5: Định lượng có nguyên liệu trùng hoặc lượng ≤ 0 thì bị từ chối. Sửa định lượng không đổi các lần trừ đã có.
- AC6: Báo cáo tiêu hao trong khoảng ngày cho từng nguyên liệu: lượng trừ theo định lượng (đã bớt phần hoàn), lượng xuất tay, chênh lệch kiểm kê.
- AC7: Chỉ quản lý và quản trị xem, sửa định lượng và xem tiêu hao.

### US-07 Xem báo cáo — FR-10.1 → FR-10.3
Là **quản lý**, tôi muốn xem doanh thu và món bán chạy để quyết định nhập hàng và thực đơn.
- AC1: Doanh thu chỉ tính khoản đã xác nhận, theo ngày giờ Việt Nam.
- AC2: Món bị huỷ không tính vào món bán chạy.

### US-35 Lãi gộp và báo cáo ngoại lệ — FR-10.4 → FR-10.7
Là **quản lý**, tôi muốn biết mỗi món lãi bao nhiêu, và ai huỷ món, giảm giá, xác nhận tay nhiều, để chỉnh giá và chặn thất thoát.
- AC1: Phở bò bán 2 bát giá 65.000 đ, mỗi bát trừ 0,15 kg bắp bò lúc giá vốn 200.000 đ/kg: doanh thu 130.000 đ, giá vốn 60.000 đ, lãi gộp 70.000 đ (54%).
- AC2: Giá vốn tính theo giá lúc món vào bếp: nhập hàng giá khác sau đó không đổi lãi gộp đã có.
- AC3: Món chưa có định lượng, hoặc dùng nguyên liệu chưa có giá vốn, hiện giá vốn "chưa đủ" và không tính lãi gộp. Món huỷ và món của đơn chưa trả không tính.
- AC4: Báo cáo ngoại lệ đếm số lần và cộng số tiền theo loại thao tác và theo người làm, trong khoảng ngày.
- AC5: Doanh thu theo ngày hiện thành biểu đồ cột. Nút "Xuất Excel" tải về một file mở được bằng Excel, có đủ các bảng của khoảng ngày đang xem.
- AC6: Chỉ quản lý và quản trị xem được.

### US-29 Xem nhật ký thao tác — FR-16.1 → FR-16.3
Là **quản lý**, tôi muốn biết ai đã huỷ món, xác nhận tay hay đổi giá, để phát hiện sai sót và gian lận.
- AC1: Khi quản lý huỷ một món đang làm kèm lý do thì nhật ký có một dòng ghi người huỷ, món và số lượng, trạng thái trước khi huỷ, số tiền của món, lý do.
- AC2: Khi thu ngân xác nhận tay một khoản chuyển khoản thì nhật ký ghi người xác nhận, mã thanh toán và số tiền.
- AC3: Khi quản lý đổi giá món thì nhật ký ghi giá cũ và giá mới. Sửa món mà giá không đổi thì không ghi.
- AC4: Thao tác bị từ chối (sai quyền, thiếu lý do) thì không có dòng nhật ký.
- AC5: Không ai sửa hay xoá được nhật ký: API không có chức năng đó, và CSDL chặn lệnh sửa, xoá.
- AC6: Phục vụ, bếp, thu ngân không xem được nhật ký (403).

## Phục vụ (WAITER)

### US-08 Mở đơn và gọi món — FR-05.1 → FR-05.3
Là **phục vụ**, tôi muốn gọi món trên điện thoại để bếp nhận ngay, không phải chạy xuống bếp.
- AC1: Khi mở đơn cho bàn đang có khách thì báo "Bàn đã có đơn đang mở".
- AC2: Khi gửi món thì món vào cột Chờ làm ở bếp trong ≤ 2 giây.
- AC3: Món đang hết không chọn được.

### US-09 Xác nhận đơn QR của khách — FR-06.3, FR-07.5
Là **phục vụ**, tôi muốn kiểm tra đơn khách tự gọi trước khi vào bếp để tránh đơn nhầm hoặc đơn phá.
- AC1: Khi khách gửi món thì sơ đồ bàn hiện dấu "chờ xác nhận" trong ≤ 2 giây.
- AC2: Món chờ xác nhận **không hiện ở bếp**.
- AC3: Khi xác nhận thì món vào bếp. Khi từ chối thì khách thấy lý do.
- AC4: Máy phục vụ đang mở sơ đồ bàn hoặc một đơn thì **kêu** khi khách gửi món.

### US-10 Ra món — FR-05.5, FR-07.5
Là **phục vụ**, tôi muốn biết món nào đã xong để mang ra ngay.
- AC1: Khi bếp bấm Xong thì sơ đồ bàn hiện dấu "có món xong".
- AC2: Khi bấm Đã ra thì món biến khỏi cột Xong của bếp.
- AC3: Khi bếp bấm Xong thì máy phục vụ đang mở sơ đồ bàn hoặc một đơn **kêu**.

### US-11 Huỷ món — FR-05.4, FR-05.6
Là **phục vụ**, tôi muốn huỷ món khách đổi ý khi bếp chưa làm.
- AC1: Món Chờ làm: phục vụ huỷ được.
- AC2: Món Đang làm hoặc Xong: chỉ quản lý huỷ được và phải nhập lý do.
- AC3: Món đã ra thì không huỷ được.

### US-31 Chuyển bàn và ghép bàn — FR-04.5, FR-04.6
Là **phục vụ**, tôi muốn ghép thêm bàn khi nhóm khách đông, và chuyển bàn khi khách đổi chỗ, mà không phải lập đơn mới.
- AC1: Đơn ở B05, ghép thêm B06: cả hai bàn hiện "có khách" trên sơ đồ, cùng một đơn và ghi nhóm "B05 + B06"; khách quét QR ở B06 gọi món vào đơn đó.
- AC2: Chuyển đơn từ B05 + B06 sang S01: B05, B06 trở lại trống, S01 có khách. Bill giữ nguyên: món, tổng, khoản giảm, mã chuyển khoản đang chờ.
- AC3: Không ghép hay chuyển vào bàn đang có đơn khác (409), và đơn mang về không gắn bàn được.
- AC4: Màn hình bếp, trang đơn và thu ngân thấy tên bàn mới.
- AC5: Thanh toán xong thì mọi bàn của đơn trở lại trống.
- AC6: Lịch sử bàn của đơn được lưu: bàn nào, từ lúc nào tới lúc nào. Bếp không chuyển bàn được (403).

### US-37 Đặt bàn và cọc — FR-18.1 → FR-18.5
Là **phục vụ**, tôi muốn ghi lịch đặt bàn và nhận cọc qua VietQR, để giữ bàn cho khách và trừ cọc vào bill khi khách tới.
- AC1: Đặt bàn cho 6 khách lúc 19:00 ngày mai, cọc 500.000 đ: booking có mã dạng KB + 8 ký tự, nằm trong danh sách ngày mai, cọc ở trạng thái chờ.
- AC2: Tin xác nhận có mã booking, ngày giờ, số khách, số tiền cọc, tài khoản nhận và nội dung chuyển khoản; bấm "Đã gửi" thì lưu nội dung và giờ gửi.
- AC3: Khách chuyển đúng 500.000 đ với nội dung là mã booking thì cọc tự sang đã nhận; sai số tiền thì không, giao dịch nằm ở danh sách không khớp. Quản lý xác nhận tay được, và việc đó vào nhật ký.
- AC4: Khách tới, nhận khách ở bàn dự kiến: đơn mở ở bàn đó; bill 1.800.000 đ hiện "Cọc đã trả −500.000 đ", còn phải trả 1.300.000 đ. Trả xong, doanh thu ngày đó là 1.800.000 đ, trong đó 500.000 đ là cọc.
- AC5: Cọc lớn hơn bill thì chỉ trừ bằng bill, còn phải trả 0 đ. Booking đã huỷ, không tới hoặc đã nhận khách thì không sửa, không nhận khách được nữa.
- AC6: Bếp và thu ngân không xem, không tạo được booking. Chỉ quản lý xác nhận cọc tay.

## Bếp (CHEF)

### US-12 Màn hình bếp — FR-07.1 → FR-07.5
Là **bếp**, tôi muốn thấy món cần làm theo thứ tự để làm đúng và đủ.
- AC1: Món sắp theo thời gian gửi, món chờ lâu nhất ở trên.
- AC2: Chỉ đổi trạng thái theo chiều tiến: Chờ làm → Đang làm → Xong.
- AC3: Món chờ tới ngưỡng ở Cài đặt (mặc định 15 phút) mà chưa ra thì phiếu **tô đỏ**.
- AC4: Có món mới vào bếp thì màn hình bếp **kêu**. Món khách gọi qua QR chỉ kêu khi phục vụ đã xác nhận.

### US-13 Báo hết món — FR-03.3
Là **bếp**, tôi muốn báo hết món để phục vụ và khách không gọi món đó nữa.
- AC1: Khi báo hết thì món biến khỏi thực đơn của khách và bị khoá trên máy phục vụ.

## Khách (không đăng nhập)

### US-14 Gọi món bằng QR — FR-06.1, FR-06.2, FR-06.5, FR-06.8
Là **khách**, tôi muốn quét QR trên bàn để tự xem thực đơn và gọi món, không phải chờ nhân viên.
- AC1: Quét QR mở trang có tên bàn, không cần đăng nhập hay cài app.
- AC2: Khi gửi món thì các món hiện "Chờ xác nhận".
- AC3: Khi bàn đang có đơn thì món mới được thêm vào đơn đó.
- AC4: Khi bàn đã có 30 món chờ xác nhận thì khách được báo chờ nhân viên xác nhận rồi mới gọi thêm.
- AC5: Khi một bàn gửi quá 10 lần trong một phút thì lần tiếp theo bị chặn, kèm thời gian phải chờ. Bàn khác không bị ảnh hưởng.

### US-15 Theo dõi món — FR-06.4
Là **khách**, tôi muốn biết món của mình đang ở đâu để khỏi phải hỏi nhân viên.
- AC1: Khi bếp đổi trạng thái thì điện thoại khách cập nhật trong ≤ 2 giây, không cần tải lại.
- AC2: Trang chỉ hiện đơn của bàn này.

### US-27 Gọi nhân viên từ bàn — FR-06.6, FR-06.7
Là **khách**, tôi muốn gọi nhân viên hoặc xin tính tiền ngay trên điện thoại, không phải vẫy tay chờ.
- AC1: Khi khách bấm Gọi nhân viên thì máy phục vụ đang mở sơ đồ bàn hoặc một đơn **kêu**, và yêu cầu hiện ở nút chuông đầu trang trong ≤ 2 giây.
- AC2: Khách bấm lại khi chưa ai nhận thì không có yêu cầu mới; nút hiện "Đang chờ nhân viên".
- AC3: Bàn chưa gọi món thì chưa xin tính tiền được.
- AC4: Phục vụ bấm Đã nhận thì yêu cầu biến khỏi danh sách, và trang khách hiện "Nhân viên đang tới".

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

### US-19 Xác nhận tay và giao dịch không khớp — FR-08.6, FR-08.7, FR-08.8
Là **thu ngân**, tôi muốn xử lý khi tiền về nhưng hệ thống không tự khớp.
- AC1: Khi khách chuyển sai số tiền thì giao dịch vào danh sách "Không khớp", đơn chưa đóng.
- AC2: Khi thu ngân xác nhận tay thì hệ thống ghi tên người xác nhận.
- AC3: Khi webhook SePay lỗi 3 lần liên tiếp thì màn hình thu ngân hiện cảnh báo "Chuyển khoản đang không tự xác nhận" trong ≤ 2 giây, kèm lý do và lúc bắt đầu lỗi.
- AC4: Khi lại nhận được một webhook hợp lệ thì cảnh báo tắt.

### US-28 In phiếu — FR-08.9
Là **thu ngân** hoặc **phục vụ**, tôi muốn in phiếu tạm tính để khách kiểm tra trước khi trả, và in phiếu thanh toán sau khi trả.
- AC1: Phiếu tạm tính chỉ có các món tính tiền, tổng bằng tổng trên màn hình. Món khách gửi còn chờ xác nhận được ghi riêng, không cộng vào tổng.
- AC2: Phiếu thanh toán của đơn trả tiền mặt ghi tiền khách đưa và tiền thối; đơn trả chuyển khoản ghi mã chuyển khoản.
- AC3: Đơn chưa trả thì không in được phiếu thanh toán. Phục vụ không lấy được thông tin thanh toán (403).
- AC4: Phiếu vừa khổ giấy 80 mm và ghi rõ không thay hoá đơn GTGT.

### US-30 Giảm giá và tặng món — FR-08.10, FR-08.11
Là **thu ngân**, tôi muốn giảm giá hoặc tặng món cho khách khi có lý do, và nhờ quản lý duyệt khi vượt hạn mức.
- AC1: Bill 600.000 đ, thu ngân giảm 50.000 đ lý do "chờ lâu" thì có hiệu lực ngay, tổng còn 550.000 đ.
- AC2: Thu ngân giảm vượt 10% tiền món hoặc vượt 150.000 đ thì khoản giảm **chờ duyệt**, tổng chưa đổi, và bill chưa thanh toán được.
- AC3: Quản lý thấy yêu cầu ở nút "Duyệt" đầu trang trong ≤ 2 giây. Duyệt thì tổng giảm; từ chối thì tổng giữ nguyên và bill thanh toán được.
- AC4: Tặng một dòng món thì tổng giảm đúng tiền dòng đó. Món tặng bị huỷ thì khoản tặng tự huỷ; tặng lại cùng dòng khi khoản cũ còn hiệu lực thì bị từ chối.
- AC5: Không có lý do, hoặc lý do "khác" mà không ghi chú, thì bị từ chối. Phục vụ không giảm giá được (403).
- AC6: Khoản giảm có hiệu lực được ghi vào nhật ký thao tác.

### US-38 Tách bill — FR-08.12, FR-08.13
Là **thu ngân**, tôi muốn thu tiền một bàn thành nhiều lần khi khách muốn trả riêng, để mỗi người trả phần của mình mà tổng vẫn khớp bill.
- AC1: Bill 1.000.001 đ chia đều 3 người: các phần 333.334 đ, 333.334 đ, 333.333 đ. Thu đủ 3 phần thì đơn đóng, bàn trống.
- AC2: Thu một phần bằng tiền mặt, phần còn lại bằng VietQR: bill hiện đã thu và còn phải thu; webhook xác nhận phần chuyển khoản; đơn chỉ đóng khi thu đủ.
- AC3: Theo món: tiền món 600.000 đ, giảm 60.000 đ; khách chọn các món 200.000 đ thì trả 180.000 đ.
- AC4: Khoản thu lớn hơn số còn phải thu, hoặc bằng 0 khi bill chưa hết, thì bị từ chối. Đơn đã thu một phần thì không huỷ món, không giảm giá được nữa.
- AC5: Phiếu thanh toán liệt kê từng khoản: phương thức, số tiền, tiền khách đưa và tiền thối, hoặc mã chuyển khoản. Báo cáo đếm một đơn dù thu nhiều khoản.

### US-34 Ca và két — FR-17.1 → FR-17.4
Là **thu ngân**, tôi muốn mở ca, ghi phiếu chi và chốt ca bằng số đếm thực tế, để két khớp và mọi chênh lệch có lý do.
- AC1: Chưa mở ca thì thu tiền mặt bị từ chối. Mở ca với quỹ đầu ca 1.000.000 đ; đang có ca mở thì không mở thêm được.
- AC2: Thu tiền mặt bill 350.000 đ, khách đưa 500.000 đ: tiền mặt dự kiến tăng 350.000 đ. Chuyển khoản không đổi tiền mặt dự kiến.
- AC3: Phiếu chi 120.000 đ "Mua đá": tiền mặt dự kiến giảm 120.000 đ. Phiếu chi trên 300.000 đ thì thu ngân bị từ chối, quản lý lập được. Không chi quá tiền mặt dự kiến.
- AC4: Chốt ca với số đếm khác dự kiến mà không ghi lý do thì bị từ chối. Ca đã chốt không thêm phiếu chi được; muốn thu tiền mặt tiếp thì mở ca mới.
- AC5: Quản lý xem danh sách ca theo ngày với quỹ đầu ca, tiền mặt thu, phiếu chi, dự kiến, thực đếm, chênh lệch và lý do.
- AC6: Phục vụ và bếp không mở ca, không chi, không chốt ca được; chỉ quản lý và quản trị xem danh sách ca.

### US-39 Khách hàng — FR-19.1 → FR-19.4
Là **thu ngân**, tôi muốn ghi khách quen theo số điện thoại, để biết họ ghé bao nhiêu lần và ai đã đồng ý nhận tin.
- AC1: Gắn khách 0912 345 678 vào đơn: chưa có thì tạo khách mới; lần sau nhập "+84912345678" thì ra đúng khách đó.
- AC2: Đơn có khách được thanh toán thì lịch sử của khách có đơn đó, số lần ghé tăng 1, tổng chi cộng đúng tiền đã trả. Booking cùng số điện thoại cũng nằm trong lịch sử.
- AC3: Ghi đồng ý nhận tin qua Zalo, cách thu thập "hỏi tại quầy": khách được nhận tin. Ghi từ chối thì không còn được nhận tin, cho tới khi đồng ý lại.
- AC4: Số điện thoại không đủ 10 số thì bị từ chối.
- AC5: Phục vụ và bếp không xem được danh sách khách; bếp không gắn khách được.

### US-40 Hoá đơn điện tử — FR-20.1 → FR-20.4
Là **quản lý**, tôi muốn mỗi bill đã thanh toán có sẵn dữ liệu hoá đơn điện tử, để nhập vào MISA meInvoice mà không gõ lại.
- AC1: Bill có lẩu 329.000 đ (loại "Ăn uống", 8%) và 2 bia 22.000 đ (loại "Rượu, bia", 10%), giá đã gồm thuế. Thanh toán xong thì hàng chờ có 1 hoá đơn: thuế suất 8% có tiền chưa thuế 304.630 đ, tiền thuế 24.370 đ; thuế suất 10% có 40.000 đ và 4.000 đ; tổng 373.000 đ.
- AC2: "Ăn uống" đang 8% và được đặt 10% từ 01/01/2027. Bill thanh toán lúc 23:50 ngày 31/12/2026 dùng 8%; bill thanh toán lúc 00:10 ngày 01/01/2027 (giờ Việt Nam) dùng 10%.
- AC3: Bill có tặng món và giảm giá: hoá đơn có dòng chiết khấu theo thuế suất của món, tổng hoá đơn bằng tiền món trừ giảm giá; cọc đã trừ vẫn tính là đã trả.
- AC4: Thu ngân ghi người mua công ty: tên, mã số thuế 0101234567, địa chỉ, email. Mã số thuế sai dạng thì bị từ chối; hoá đơn đã có số thì không sửa được người mua.
- AC5: Quản lý xuất file các hoá đơn chưa có số trong ngày, rồi ghi ký hiệu 1C26MKB, số 123: hoá đơn chuyển sang "Đã có số", và ký hiệu, số đó không ghi được cho hoá đơn khác. Phục vụ và bếp không xem được hàng chờ; thu ngân chỉ ghi người mua.

### US-41 Đơn app giao hàng — FR-21.1 → FR-21.3
Là **phục vụ quầy**, tôi muốn nhập đơn GrabFood, ShopeeFood vào hệ thống, để bếp làm như đơn thường và doanh thu app không bị sót hay nhập trùng.
- AC1: Nhập đơn GrabFood mã "gf-8812" gồm 2 phần nem rán giá app 79.000 đ: đơn lưu mã "GF-8812", tiền 158.000 đ, món vào bếp.
- AC2: Nhập lại mã "GF-8812" cho GrabFood thì bị báo trùng và không tạo đơn; cùng mã cho ShopeeFood thì được; đơn GrabFood đó đã huỷ thì nhập lại được.
- AC3: Món chưa có giá ShopeeFood thì không gọi được trong đơn ShopeeFood.
- AC4: Còn món chưa xong thì chưa giao shipper được. Mọi món xong, bấm Giao shipper: đơn đóng, doanh thu có 158.000 đ ở dòng GrabFood, tiền mặt trong két không đổi.
- AC5: Đơn app không thu tiền mặt hay VietQR được; bếp không tạo được đơn app.

## Nhân sự

Phần thêm theo yêu cầu của môn sau bản core (P2-05 → P2-09 trong [kế hoạch](10-ke-hoach-phat-trien.md)).

### US-20 Hồ sơ và mức lương — FR-12.1 → FR-12.3
Là **quản trị**, tôi muốn lưu ngày vào làm và mức lương của từng người để tính lương đúng.
- AC1: Khi nhập mức lương âm thì báo lỗi.
- AC2: Khi cho nghỉ việc thì tài khoản bị khoá; hồ sơ, chấm công và phiếu lương cũ vẫn xem được.
- AC3: Khi quản lý gọi API xem hoặc sửa mức lương thì nhận 403.

### US-21 Xếp ca — FR-13.1 → FR-13.4
Là **quản lý**, tôi muốn xếp lịch làm tuần tới để ca nào cũng đủ người.
- AC1: Khi xếp một người vào hai ca trùng giờ trong cùng ngày thì báo lỗi.
- AC2: Khi sao chép lịch tuần trước thì tuần mới có đủ các ca, trừ người đã nghỉ việc.
- AC3: Nhân viên chỉ thấy lịch của chính mình.

### US-22 Xin nghỉ — FR-13.5, FR-13.6
Là **nhân viên**, tôi muốn xin nghỉ trên app để quản lý duyệt và xếp người thay.
- AC1: Khi đơn được duyệt thì các ca của tôi trong những ngày đó biến khỏi lịch.
- AC2: Tôi huỷ được đơn khi còn chờ duyệt, không huỷ được khi đã duyệt.
- AC3: Ngày tôi đã chấm công thì không duyệt nghỉ được.

### US-23 Chấm công — FR-14.1, FR-14.2, FR-14.4
Là **nhân viên**, tôi muốn bấm vào ca, ra ca trên điện thoại để công của mình được ghi đúng.
- AC1: Khi hôm nay không có ca, hoặc còn sớm hơn 15 phút trước giờ bắt đầu, thì không vào ca được.
- AC2: Khi đang trong ca mà bấm vào ca lần nữa thì báo "Bạn đang trong ca".
- AC3: Khi vào ca muộn 10 phút thì bản ghi có đi muộn 10 phút.

### US-24 Sửa bảng công — FR-14.3
Là **quản lý**, tôi muốn sửa giờ khi nhân viên quên bấm ra ca để công không bị thiếu.
- AC1: Khi sửa mà không nhập lý do thì báo lỗi.
- AC2: Bản ghi đã sửa hiện người sửa và lý do.
- AC3: Khi bảng lương tháng đó đã chốt thì không sửa được.

### US-25 Bảng lương tháng — FR-15.1 → FR-15.3, FR-15.5
Là **quản trị**, tôi muốn tính lương tháng từ bảng công để không phải cộng tay trên Excel.
- AC1: Người làm theo giờ, làm 100 giờ, đơn giá 25.000 đ thì lương theo công là 2.500.000 đ.
- AC2: Người lương tháng 8.000.000 đ, công chuẩn 26 ngày, đi làm 24 ngày và nghỉ có lương 1 ngày thì lương theo công là 7.692.307 đ (8.000.000 × 25 ÷ 26, làm tròn xuống).
- AC3: Khi đã chốt thì không tính lại và không sửa thưởng phạt được.

### US-26 Xem phiếu lương — FR-15.4
Là **nhân viên**, tôi muốn xem phiếu lương của mình để biết lương được tính thế nào.
- AC1: Chỉ thấy phiếu đã chốt của chính mình. Gọi API xem phiếu người khác thì nhận 403.
- AC2: Phiếu ghi số giờ hoặc số ngày công, mức lương, từng khoản thưởng phạt kèm lý do.
