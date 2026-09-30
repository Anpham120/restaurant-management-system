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

### FR-07 Màn hình bếp

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-07.1 | Bếp xem món theo 3 cột: Chờ làm, Đang làm, Xong. Mỗi món ghi bàn, số lượng, ghi chú, thời gian chờ | M | BR-07 |
| FR-07.2 | Bếp chuyển trạng thái: Chờ làm → Đang làm → Xong | M | BR-07 |
| FR-07.3 | Món mới hiện trên màn hình bếp trong ≤ 2 giây, không cần tải lại trang | M | — |

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

### FR-09 Kho nguyên liệu (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-09.1 | Thêm, sửa nguyên liệu: tên, đơn vị, mức tối thiểu | M | — |
| FR-09.2 | Nhập kho, xuất kho, kiểm kê (điều chỉnh về số thực tế), có ghi chú | M | BR-19 |
| FR-09.3 | Xem lịch sử biến động của từng nguyên liệu | M | BR-19 |
| FR-09.4 | Cảnh báo nguyên liệu có tồn ≤ mức tối thiểu | M | BR-20 |

### FR-10 Báo cáo (MANAGER)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-10.1 | Theo khoảng ngày: doanh thu, số đơn, trung bình mỗi đơn | M | BR-21 |
| FR-10.2 | Doanh thu theo ngày và theo phương thức (tiền mặt, chuyển khoản) | M | BR-21 |
| FR-10.3 | Top 10 món bán chạy theo số lượng | S | BR-21 |

### FR-11 Cài đặt (ADMIN)

| Mã | Yêu cầu | Ưu tiên | Quy tắc |
|---|---|---|---|
| FR-11.1 | Thông tin nhà hàng: tên, địa chỉ, điện thoại | M | — |
| FR-11.2 | Tài khoản nhận chuyển khoản: mã ngân hàng, số tài khoản, tên chủ tài khoản | M | BR-14 |

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
