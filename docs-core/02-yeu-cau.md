# 2. Yêu cầu

Ưu tiên theo MoSCoW. Cột "Quy tắc" trỏ tới [quy tắc nghiệp vụ](05-quy-tac-nghiep-vu.md).

## 2.1 Yêu cầu chức năng

### FR-01 Đăng nhập và phân quyền

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-01.1 | Nhân viên đăng nhập bằng tên đăng nhập và mật khẩu, nhận token có hạn 12 giờ | M | BR-01 |
| FR-01.2 | Mỗi API kiểm tra vai trò. Sai quyền trả 403 | M | BR-02 |
| FR-01.3 | Tài khoản bị khoá không đăng nhập được, token cũ mất hiệu lực ngay | M | BR-03 |
| FR-01.4 | Nhân viên tự đổi mật khẩu | S | BR-01 |
| FR-01.5 | **Chống dò mật khẩu**: đăng nhập quá 10 lần trong một phút với cùng tên đăng nhập thì bị chặn tạm thời | M | BR-31 |
| FR-01.6 | **Thu hồi token**: đổi mật khẩu, hoặc quản trị đặt lại mật khẩu, thì mọi phiên đăng nhập cũ của người đó hết hiệu lực; máy vừa đổi mật khẩu vẫn dùng tiếp | S | BR-41 |
| FR-01.7 | **Đăng xuất mọi thiết bị**: nhân viên tự kết thúc mọi phiên đăng nhập của mình, kể cả máy đang dùng | S | BR-41 |

### FR-02 Quản lý nhân viên (ADMIN)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-02.1 | Xem, thêm, sửa nhân viên: họ tên, tên đăng nhập, vai trò | M | BR-01 |
| FR-02.2 | Khoá hoặc mở khoá tài khoản. Không xoá nhân viên | M | BR-03 |
| FR-02.3 | Đặt lại mật khẩu cho nhân viên | M | BR-01 |

### FR-03 Thực đơn

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-03.1 | Thêm, sửa, xoá danh mục (tên, thứ tự hiển thị) | M | BR-18 |
| FR-03.2 | Thêm, sửa, xoá món (tên, giá, danh mục, mô tả) | M | BR-18 |
| FR-03.3 | Bếp hoặc quản lý bật, tắt **hết món**. Món hết không gọi được | M | BR-06 |

### FR-04 Bàn và mã QR

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-04.1 | Thêm, sửa, xoá bàn: tên, khu vực, số ghế | M | BR-18 |
| FR-04.2 | Mỗi bàn có **mã QR riêng**. Quản lý xem và in thẻ QR | M | BR-09 |
| FR-04.3 | Tạo lại mã QR khi thẻ mất hoặc bị lộ. Mã cũ hết hiệu lực ngay | M | BR-09 |
| FR-04.4 | Sơ đồ bàn hiện trạng thái: trống, có khách, có món chờ xác nhận, có món xong chờ ra | M | BR-04 |
| FR-04.5 | **Ghép bàn**: một đơn chiếm nhiều bàn cho một nhóm khách. Mỗi bàn vẫn hiện riêng trên sơ đồ, kèm nhóm bàn của đơn. Khách quét QR ở bàn nào trong nhóm cũng gọi món vào cùng đơn | M | BR-04, 36 |
| FR-04.6 | **Chuyển bàn**: chuyển cả đơn sang bàn khác, kể cả từ N bàn sang M bàn. **Bill giữ nguyên**. Bếp, phục vụ, thu ngân thấy tên bàn mới; bàn cũ trống ngay. Hệ thống lưu lịch sử bàn của đơn | M | BR-04, 36 |

### FR-05 Gọi món (phục vụ)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-05.1 | Mở đơn cho bàn trống, hoặc tạo đơn mang về | M | BR-04 |
| FR-05.2 | Chọn món theo danh mục, số lượng, ghi chú; gửi bếp một lượt | M | BR-05, 06 |
| FR-05.3 | Gọi thêm món vào đơn đang mở | M | BR-05 |
| FR-05.4 | Huỷ món theo quy tắc huỷ | M | BR-08 |
| FR-05.5 | Đánh dấu món **đã ra** | M | BR-07 |
| FR-05.6 | Huỷ đơn khi mọi món đã huỷ; bàn trở lại trống | S | BR-08 |

### FR-06 Khách gọi món qua QR

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-06.1 | Khách quét QR trên bàn, xem thực đơn đang bán, **không cần đăng nhập hay cài app** | M | BR-09 |
| FR-06.2 | Khách chọn món, ghi chú, gửi. Món ở trạng thái **Chờ xác nhận** | M | BR-10 |
| FR-06.3 | Nhân viên nhận thông báo ngay; **xác nhận** (món vào bếp) hoặc **từ chối** (kèm lý do) | M | BR-10 |
| FR-06.4 | Khách xem các món của bàn, **trạng thái từng món theo thời gian thực**, tổng tạm tính | M | BR-11 |
| FR-06.5 | Khách gọi thêm nhiều lần khi bàn đang mở | M | BR-10 |
| FR-06.6 | Khách bấm **Gọi nhân viên** hoặc **Yêu cầu tính tiền** trên trang QR. Máy phục vụ **kêu** và hiện yêu cầu, kèm số phút đã chờ | S | BR-29 |
| FR-06.7 | Phục vụ bấm **Đã nhận** thì yêu cầu đóng, trang khách báo "Nhân viên đang tới". Hệ thống ghi người nhận và lúc nhận để đo thời gian phản hồi | S | BR-29 |
| FR-06.8 | **Chống spam đơn QR**: mỗi bàn gửi tối đa 10 lần mỗi phút (gửi món, gọi nhân viên, thanh toán) và có tối đa 30 món chờ xác nhận | M | BR-30 |

### FR-07 Màn hình bếp

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-07.1 | Bếp xem món theo 3 cột: Chờ làm, Đang làm, Xong. Mỗi món ghi bàn, số lượng, ghi chú, thời gian chờ | M | BR-07 |
| FR-07.2 | Bếp chuyển trạng thái: Chờ làm → Đang làm → Xong | M | BR-07 |
| FR-07.3 | Món mới hiện trên màn hình bếp trong ≤ 2 giây, không cần tải lại trang | M | — |
| FR-07.4 | Món **chờ lâu** thì tô đỏ. Ngưỡng mặc định 15 phút, quản trị đổi ở Cài đặt | S | BR-28 |
| FR-07.5 | **Âm báo**: màn hình bếp kêu khi có món mới vào bếp. Sơ đồ bàn và trang đơn của phục vụ kêu khi có món xong hoặc khách gửi món qua QR. Mỗi máy tự bật, tắt âm báo | S | BR-10 |

### FR-08 Thanh toán

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-08.1 | Xem bill: món, số lượng, đơn giá, thành tiền, tổng | M | BR-12 |
| FR-08.2 | Thu tiền mặt: nhập tiền khách đưa, hệ thống tính tiền thối | M | BR-13 |
| FR-08.3 | Chuyển khoản: tạo mã **VietQR** đúng số tiền, nội dung chứa **mã thanh toán duy nhất** | M | BR-14 |
| FR-08.4 | Tự xác nhận khi nhận **webhook SePay** hợp lệ. Màn hình thu ngân và điện thoại khách cập nhật ngay. Đơn đóng, bàn trống | M | BR-15, 16 |
| FR-08.5 | **Khách tự thanh toán** trên trang QR: bấm "Thanh toán" → hiện VietQR → tự xác nhận | M | BR-13, 14 |
| FR-08.6 | Thu ngân **xác nhận tay** khi webhook không tới. Hệ thống ghi người xác nhận | M | BR-17 |
| FR-08.7 | Xem danh sách giao dịch ngân hàng **không khớp** để kiểm tra | S | BR-16 |
| FR-08.8 | **Cảnh báo webhook lỗi**: webhook SePay bị từ chối hoặc xử lý lỗi **3 lần liên tiếp** thì màn hình thu ngân hiện cảnh báo trong ≤ 2 giây, kèm lý do và lúc bắt đầu lỗi, để thu ngân kiểm tra app ngân hàng rồi xác nhận tay. Nhận được một webhook hợp lệ thì cảnh báo tự tắt | C | BR-32 |
| FR-08.9 | **In phiếu khổ 80 mm** từ trình duyệt. Phục vụ và thu ngân in **phiếu tạm tính** của đơn đang mở để khách kiểm tra. Thu ngân in **phiếu thanh toán** sau khi đơn đã trả, in lại được. Phiếu ghi tên, địa chỉ, điện thoại quán, bàn, các món tính tiền và tổng; phiếu thanh toán ghi thêm cách trả, tiền khách đưa và tiền thối, hoặc mã chuyển khoản | M | BR-12, 33 |
| FR-08.10 | **Giảm giá, tặng món**: thu ngân hoặc quản lý giảm một số tiền trên cả bill, hoặc tặng nguyên một dòng món, kèm lý do. Bill, trang khách và phiếu in ghi tiền món, từng khoản giảm và tổng sau giảm. Khoản giảm còn huỷ được khi đơn chưa trả | M | BR-12, 35 |
| FR-08.11 | **Duyệt giảm giá**: thu ngân giảm vượt hạn mức thì khoản giảm chờ quản lý duyệt. Quản lý thấy yêu cầu ở đầu trang trong ≤ 2 giây, duyệt hoặc từ chối từ máy của mình. Còn khoản chờ duyệt thì chưa thanh toán được | M | BR-13, 35 |

### FR-09 Kho nguyên liệu (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-09.1 | Thêm, sửa nguyên liệu: tên, đơn vị, mức tối thiểu | M | — |
| FR-09.2 | Nhập kho, xuất kho, kiểm kê (điều chỉnh về số thực tế), có ghi chú | M | BR-19 |
| FR-09.3 | Xem lịch sử biến động của từng nguyên liệu | M | BR-19 |
| FR-09.4 | Cảnh báo nguyên liệu có tồn ≤ mức tối thiểu | M | BR-20 |
| FR-09.5 | **Nhà cung cấp**: tên, điện thoại, địa chỉ, mã số thuế, ghi chú. Ngừng giao dịch thì không chọn được khi lập phiếu, nhưng phiếu cũ vẫn giữ | S | BR-37 |
| FR-09.6 | **Phiếu nhập có giá**: chọn nhà cung cấp, các dòng nguyên liệu với số lượng và đơn giá; tổng tiền phiếu tự tính. Lưu phiếu thì tồn tăng, lịch sử kho ghi số phiếu. Xem lại phiếu theo khoảng ngày | S | BR-19, 37 |
| FR-09.7 | **Giá vốn nguyên liệu** tính lại theo bình quân gia quyền mỗi lần nhập theo phiếu. Màn hình kho hiện giá vốn và giá trị tồn | S | BR-37 |
| FR-09.8 | **Định lượng món**: mỗi món ghi các nguyên liệu và lượng dùng cho một phần. Chỉ quản lý, quản trị xem và sửa | S | BR-38 |
| FR-09.9 | **Trừ kho tự động** theo định lượng khi món vào bếp; huỷ món còn Chờ làm thì hoàn kho. Lịch sử kho ghi bàn, số đơn, tên món | S | BR-19, 38 |
| FR-09.10 | **Tiêu hao theo định lượng** của từng nguyên liệu trong khoảng ngày, đặt cạnh lượng xuất tay và chênh lệch kiểm kê | S | BR-38 |

### FR-10 Báo cáo (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-10.1 | Theo khoảng ngày: doanh thu, số đơn, trung bình mỗi đơn | M | BR-21 |
| FR-10.2 | Doanh thu theo ngày và theo phương thức (tiền mặt, chuyển khoản) | M | BR-21 |
| FR-10.3 | Top 10 món bán chạy theo số lượng | S | BR-21 |
| FR-10.4 | **Lãi gộp theo món**: số lượng, doanh thu, giá vốn theo định lượng, lãi gộp và tỷ lệ; tổng giảm giá, tặng món của kỳ | S | BR-40 |
| FR-10.5 | **Báo cáo ngoại lệ**: số lần và số tiền huỷ món, giảm giá và tặng món, xác nhận tay chuyển khoản, theo loại và theo người làm | S | BR-34, 40 |
| FR-10.6 | **Biểu đồ** doanh thu theo ngày | S | BR-21 |
| FR-10.7 | **Xuất Excel** báo cáo của khoảng ngày đang xem | S | — |

### FR-11 Cài đặt (ADMIN)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-11.1 | Thông tin nhà hàng: tên, địa chỉ, điện thoại | M | — |
| FR-11.2 | Tài khoản nhận chuyển khoản: mã ngân hàng, số tài khoản, tên chủ tài khoản | M | BR-14 |
| FR-11.3 | Ngưỡng **món chờ lâu** (số phút) cho màn hình bếp | S | BR-28 |

### FR-12 Hồ sơ nhân viên (ADMIN)

FR-12 → FR-15 là phần **nhân sự**, thêm theo yêu cầu của môn sau bản core (P2-05 → P2-09 trong [kế hoạch](10-ke-hoach-phat-trien.md)). CSDL ở [7.5](07-erd.md#75-nhân-sự).

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-12.1 | Lưu thêm số điện thoại, ngày vào làm, ngày nghỉ việc | M | BR-22 |
| FR-12.2 | Đặt hình thức lương (theo giờ hoặc theo tháng) và mức lương | M | BR-22, 26 |
| FR-12.3 | Cho nghỉ việc: ghi ngày nghỉ và khoá tài khoản. Hồ sơ, chấm công, phiếu lương vẫn giữ | M | BR-03 |

### FR-13 Xếp ca và nghỉ phép (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-13.1 | Thêm, sửa ca mẫu: tên, giờ bắt đầu, giờ kết thúc (ví dụ Sáng 07:00–14:00) | M | BR-23 |
| FR-13.2 | Xếp nhân viên vào ca theo ngày trên lịch tuần | M | BR-23 |
| FR-13.3 | Sao chép lịch tuần trước sang tuần mới | S | BR-23 |
| FR-13.4 | Nhân viên xem lịch làm của mình | M | — |
| FR-13.5 | Nhân viên gửi đơn nghỉ: từ ngày, đến ngày, có lương hoặc không lương, lý do. Quản lý duyệt hoặc từ chối | M | BR-24 |
| FR-13.6 | Duyệt đơn nghỉ thì gỡ các ca đã xếp trong những ngày đó | S | BR-24 |

### FR-14 Chấm công

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-14.1 | Nhân viên bấm **Vào ca**, **Ra ca** trên app | M | BR-25 |
| FR-14.2 | Hệ thống tính số phút đi muộn, về sớm, số phút làm | M | BR-25 |
| FR-14.3 | Quản lý xem bảng công theo ngày và theo tháng; thêm hoặc sửa bản ghi, **bắt buộc lý do** | M | BR-25, 27 |
| FR-14.4 | Nhân viên xem lịch sử chấm công của mình | S | — |

### FR-15 Tính lương (ADMIN)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-15.1 | Tạo bảng lương tháng, tính cho từng người từ chấm công và nghỉ phép | M | BR-26 |
| FR-15.2 | Thêm thưởng hoặc phạt cho từng người, có lý do | M | BR-26 |
| FR-15.3 | Chốt bảng lương. Đã chốt thì không sửa được bảng lương và chấm công của tháng đó | M | BR-27 |
| FR-15.4 | Nhân viên xem phiếu lương đã chốt của mình | M | BR-27 |
| FR-15.5 | Xuất bảng lương ra Excel | S | — |

### FR-16 Nhật ký thao tác (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-16.1 | Hệ thống tự **ghi nhật ký** khi có người huỷ hoặc từ chối món, xác nhận tay chuyển khoản, đổi giá món. Mỗi dòng ghi người làm, lúc nào, đơn liên quan, món hoặc mã thanh toán, giá trị trước và sau, số tiền, lý do | M | BR-34 |
| FR-16.2 | Quản lý **tra cứu nhật ký** theo khoảng ngày, lọc theo người, loại thao tác, đơn hoặc bàn | M | BR-34 |
| FR-16.3 | Nhật ký **không sửa, không xoá được**, kể cả khi thao tác thẳng trong CSDL | M | BR-34 |

### FR-17 Ca và két (CASHIER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-17.1 | **Mở ca** với quỹ đầu ca (mặc định 1.000.000 đ). Két chỉ có một ca mở; chưa mở ca thì không thu tiền mặt | M | BR-39 |
| FR-17.2 | **Phiếu chi** tiền mặt từ két: số tiền, lý do, người chi. Trên 300.000 đ chỉ quản lý lập. Không sửa, không xoá | M | BR-39 |
| FR-17.3 | **Chốt ca**: hệ thống tính tiền mặt dự kiến, thu ngân nhập số đếm thực tế, hệ thống tính chênh lệch; lệch thì bắt buộc lý do. Ca đã chốt không sửa được | M | BR-39 |
| FR-17.4 | Quản lý xem **danh sách ca** theo khoảng ngày: quỹ đầu ca, tiền mặt thu, phiếu chi, dự kiến, thực đếm, chênh lệch, lý do | M | BR-39 |

## 2.2 Yêu cầu phi chức năng

| Mã | Yêu cầu | Cách kiểm tra |
|---|---|---|
| NFR-01 | Cập nhật realtime (bếp, phục vụ, khách, thu ngân) ≤ 2 giây | Demo hai trình duyệt; đo thời gian |
| NFR-02 | API trả lời ≤ 500 ms (p95) với 30 người dùng cùng lúc | k6 (`perf/load-test.js`) sau khi nạp 6 tháng bán hàng; workflow Load test chạy tay trên GitHub |
| NFR-03 | Mật khẩu băm **BCrypt**. API dùng **JWT**. Triển khai thật phải có **HTTPS** | Xem mã, kiểm tra cấu hình |
| NFR-04 | Webhook chỉ nhận khi header `Authorization: Apikey …` đúng | Test tích hợp |
| NFR-05 | API công khai chỉ trả dữ liệu **của bàn có mã QR đó**, không lộ thông tin nhân viên | Test tích hợp |
| NFR-06 | Một bàn chỉ một đơn đang mở; một giao dịch ngân hàng chỉ xử lý một lần. Cả hai **ràng buộc trong CSDL** | Test tích hợp |
| NFR-07 | Giao diện tiếng Việt. Trang khách dùng tốt trên màn hình rộng 360px | Kiểm tra trên điện thoại |
| NFR-08 | Tiền là số nguyên VND. Giờ theo múi giờ Việt Nam (`Asia/Ho_Chi_Minh`) | Test báo cáo |
| NFR-09 | Test tích hợp chạy với **PostgreSQL thật** (Testcontainers). CI chạy test mỗi lần push | Xem pipeline |
| NFR-10 | Triển khai bằng **Docker Compose**. Quay về phiên bản trước bằng tag image | Làm thử một lần |
| NFR-11 | Trên staging và production, backend ghi **log dạng JSON** (chuẩn ECS). Số liệu vận hành xem ở `/actuator/metrics`, **chỉ ADMIN**, và Nginx không mở đường dẫn này ra ngoài | Xem `docker compose logs backend`; test tích hợp phân quyền |
