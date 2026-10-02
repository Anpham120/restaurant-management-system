# 15. Kịch bản demo 10 phút

Tài liệu này là việc P5-04 ([tài liệu 10](10-ke-hoach-phat-trien.md)). Demo chứng minh ba tiêu chí nghiệm thu ở mục 1.5 của [tài liệu 1](01-tam-nhin-du-an.md):
- Trọn luồng từ lúc khách quét QR tới lúc bàn trống.
- Mỗi vai trò chỉ làm việc của mình.
- CI tự chạy test.

Slide đi kèm có 9 trang, dàn ý ở mục 15.5. Các bước bấm trên từng màn hình có ở [tài liệu 14](14-huong-dan-su-dung.md).

## 15.1 Thời gian và người nói

Gợi ý chia người nói theo service ở mục 10.7: ai giữ service nào nói phần đó.

| Phút | Phần | Chiếu | Người nói |
|---|---|---|---|
| 0:00–1:00 | Mở đầu: nhóm, vấn đề, mục tiêu | Slide 1–3 | totototototoads |
| 1:00–4:00 | Demo 1: khách gọi món qua QR, tới lúc bàn trống | Slide 4, rồi trình duyệt và điện thoại | Anpham120 (gọi món, bếp), buidaoducanh1210 (chuyển khoản) |
| 4:00–5:15 | Demo 2: thu ngân tách bill | Thu ngân | buidaoducanh1210 |
| 5:15–6:45 | Demo 3: kho và báo cáo | Quản lý | Tanh2k8-123 |
| 6:45–7:45 | Demo 4: nhân sự | Quản lý, Của tôi | quanghieu1605 |
| 7:45–8:15 | Tính năng mở rộng | Slide 5 | totototototoads |
| 8:15–9:30 | Kiến trúc, quy trình, kiểm thử | Slide 6–8 | Anpham120 |
| 9:30–10:00 | Kết luận, hỏi đáp | Slide 9 | totototototoads |

## 15.2 Chuẩn bị

Làm xong trước giờ demo ít nhất 30 phút, rồi chạy thử trọn kịch bản một lần.

**Thiết bị.**
- Một laptop nối máy chiếu và một điện thoại làm máy của khách.
- Laptop và điện thoại dùng chung Wi-Fi.

**Chạy hệ thống.** Nên demo trên laptop: không phụ thuộc mạng ngoài, và tiền về được giả lập bằng một lệnh.

| | Trên laptop (nên dùng) | Trên máy chủ staging |
|---|---|---|
| Địa chỉ | `http://<IP-laptop>:8080` | `https://<tên miền staging>` ([tài liệu 11](11-trien-khai-van-hanh.md)) |
| Tài khoản | Tài khoản demo, mật khẩu `123456` | Quản trị tạo trước tài khoản quản lý, phục vụ, bếp, thu ngân ở **Nhân viên** |
| Tiền về | Gửi webhook giả bằng lệnh ở bước 6 dưới đây | Chuyển khoản thật một khoản nhỏ, SePay báo về |

**Các bước, trên laptop.**

1. Dựng hệ thống, với địa chỉ mà điện thoại mở được để mã QR in ra dùng được:

   ```powershell
   $env:APP_PUBLIC_BASE_URL = "http://192.168.1.10:8080"   # thay bằng IP Wi-Fi của laptop (ipconfig)
   docker compose up -d --build
   ```

   Điện thoại không mở được địa chỉ này thì cho Docker qua tường lửa Windows (mạng Private).

2. Nạp khoảng 6 tháng bán hàng, để báo cáo có số liệu:

   ```powershell
   Get-Content perf/seed-history.sql | docker compose exec -T db psql -U rms -d rms -v ON_ERROR_STOP=1
   ```

3. Dữ liệu tại quán, làm bằng tay khoảng 10 phút:
   - `quanly`, **Thực đơn**: món "Bia Hà Nội", **Định lượng** 1 chai "Bia Hà Nội". Kho sẽ tự trừ khi bia vào bếp.
   - `thungan`: **Mở ca**, quỹ đầu ca 2.000.000 đ.
   - `phucvu`: mở bàn B05, 4 khách. Gọi 1 Lẩu gà lá é, 3 Bia Hà Nội, 1 Rau muống xào tỏi, rồi **Gửi bếp**.
   - `bep`: các món của B05, **Bắt đầu** rồi **Xong**.
   - `phucvu`: **Đã ra** cả ba món. Bill B05 là 410.000 đ, dùng ở Demo 2.
   - `quanly`, **Xếp ca**: tạo **Ca mẫu** "Ca demo" bắt đầu trước giờ demo khoảng 30 phút, kết thúc sau giờ demo, không qua nửa đêm (demo lúc 14:00 thì ca 13:30–22:00). Xếp ca này cho `thungan` hôm nay. Dùng ở Demo 4: chỉ vào ca được từ 15 phút trước giờ bắt đầu tới giờ kết thúc (BR-25).

4. Mở cửa sổ trình duyệt:
   - Ba cửa sổ ở ba **hồ sơ Chrome** khác nhau: Phục vụ (`phucvu`), Bếp (`bep`), Thu ngân (`thungan`). Đăng nhập lưu theo trình duyệt, nên hai tab cùng một hồ sơ là cùng một tài khoản.
   - Cửa sổ thứ tư cho `quanly`, dùng ở Demo 3 và 4.
   - Ở Phục vụ và Bếp, bấm **Chạm để bật âm báo**. Bật loa laptop.

5. In hoặc mở sẵn thẻ QR của bàn **B02** (**Bàn và QR** → **Xem QR**). Điện thoại mở sẵn camera.

6. Mở sẵn PowerShell có lệnh dưới, chỉ còn thay mã và số tiền. Mỗi lần gửi dùng một `id` mới.

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/webhooks/sepay `
     -Headers @{ Authorization = "Apikey dev-sepay-key" } -ContentType "application/json" `
     -Body '{"id": 2001, "gateway": "Vietcombank", "content": "KBXXXXXXXX", "transferType": "in", "transferAmount": 139000}'
   ```

7. Dự phòng: quay video một lần chạy thử; ảnh của từng màn hình có ở tài liệu 14.

## 15.3 Kịch bản từng phần

### Mở đầu (0:00–1:00), slide 1–3

- Khói Bếp là quán lẩu nướng ở Đống Đa, khoảng 24 bàn.
- Hiện nay phục vụ ghi order ra giấy, nên sót món. Bếp không biết thứ tự món. Khách chuyển khoản thì thu ngân dò app ngân hàng.
- Nhóm làm một hệ thống chạy trên trình duyệt, cho 5 vai trò nhân viên. Khách không cần cài app.
- Sáu mục tiêu G1–G6. Phần demo đi qua các mục tiêu đó.

### Demo 1: khách gọi món qua QR, tới lúc bàn trống (1:00–4:00)

| # | Làm | Nói |
|---|---|---|
| 1 | Điện thoại quét QR bàn B02. Nhóm "Lẩu và nướng": 1 Ba chỉ nướng; nhóm "Đồ uống": 2 Trà đá. Bấm **Xem 3 món đã chọn**, rồi **Gửi món** | Không cần đăng nhập. Mã QR là chuỗi ngẫu nhiên 128 bit, không đoán được (BR-09) |
| 2 | Máy phục vụ kêu; B02 có nhãn vàng "món QR chờ xác nhận". Mở B02, bấm **Xác nhận tất cả** | Món khách gửi phải qua nhân viên mới vào bếp, để tránh gọi đùa (G3, BR-10) |
| 3 | Bếp kêu, món hiện ở **Chờ làm**. Bấm **Bắt đầu**, rồi **Xong** | Không ai tải lại trang: trạng thái tới các máy trong khoảng 2 giây (G2) |
| 4 | Điện thoại, tab **Món đã gọi**: món hiện "Xong". Máy phục vụ kêu, B02 có nhãn xanh "món xong, mang ra". Bấm **Đã ra** | Phục vụ biết món nào xong mà không phải xuống bếp hỏi |
| 5 | Điện thoại: **Thanh toán chuyển khoản**, hiện mã VietQR. Đọc mã `KB…` và số tiền dưới mã QR, gửi lệnh webhook ở mục 15.2 | Trên máy chủ thật, bước này là khách quét mã bằng app ngân hàng. SePay báo tiền về cho hệ thống |
| 6 | Vài giây sau: điện thoại hiện "Đã thanh toán", B02 trên sơ đồ bàn chuyển **Trống** | Thu ngân không phải làm gì. Hệ thống chỉ tự xác nhận khi đúng mã và đúng số tiền (G4, BR-15) |

### Demo 2: thu ngân tách bill (4:00–5:15)

1. Ở Thu ngân, chọn **Bàn B05**. Nói: bill hiện từng món, khoản giảm giá và cọc nếu có; B05 tổng 410.000 đ.
2. **Tách bill** → **Chia đều**, 2 người.
3. Phần 1 (205.000 đ) bấm **Tiền mặt**, bấm số gợi ý 300.000 đ. Nói: màn hình tính tiền thối 95.000 đ. Bấm **Xác nhận đã thu**.
4. Bill hiện phần đã thu và phần còn lại. Bấm **Tiền mặt** của bill để thu nốt 205.000 đ. Bill đóng, bấm **In phiếu thanh toán** để xem phiếu khổ 80 mm.
5. Nói thêm, không cần bấm:
   - Giảm giá quá 10% tiền món hoặc quá 150.000 đ thì phải chờ quản lý duyệt.
   - Tiền mặt nằm trong ca két, cuối ca phải chốt và giải trình chênh lệch.

### Demo 3: kho và báo cáo (5:15–6:45)

1. Cửa sổ `quanly`, **Kho**:
   - "Bia Hà Nội" còn 117 chai. Bấm **Lịch sử**: dòng **Bán món** ghi bàn B05, trừ 3 chai. Nói: món vào bếp thì kho tự trừ theo định lượng (BR-38).
   - "Cua đồng xay" có nhãn **Sắp hết**, vì tồn 1,5 kg dưới mức tối thiểu 2 kg (G5).
2. **Báo cáo**, 7 ngày gần nhất:
   - Doanh thu theo ngày.
   - Theo phương thức: tiền mặt, chuyển khoản.
   - Top 10 món bán chạy.
   - Bấm **Xuất Excel** (G6).
3. **Nhật ký**: mọi lần huỷ món, xác nhận tay, đổi giá, giảm giá đều ghi người làm và lý do; không ai sửa hay xoá được (BR-34).

### Demo 4: nhân sự (6:45–7:45)

1. Cửa sổ `quanly`, **Xếp ca**: lịch tuần có "Ca demo" của thu ngân. Nói: **Chép tuần trước** để xếp nhanh cả tuần.
2. Cửa sổ Thu ngân, **Của tôi**: bấm **Vào ca**. Quay lại `quanly`, **Chấm công**: có giờ vào, và số phút đi muộn tự tính (ca bắt đầu trước đó khoảng 30 phút).
3. `quanly` không vào được **Bảng lương**: trang này chỉ quản trị dùng. Nói: mỗi vai trò chỉ thấy trang của mình, API trả 403 nếu gọi sai quyền (tiêu chí 2). Có thời gian thì đăng nhập `admin` để mở **Bảng lương**: tạo bảng lương tháng từ chấm công, **Chốt**, nhân viên xem phiếu lương ở **Của tôi**.

### Tính năng mở rộng (7:45–8:15), slide 5

Chỉ chiếu ảnh, không demo trực tiếp:
- Đặt bàn và cọc qua VietQR: cọc tự trừ vào bill.
- Đơn GrabFood, ShopeeFood: giá theo app, giao shipper.
- Khách hàng theo số điện thoại.
- Dữ liệu hoá đơn điện tử, xuất file cho MISA.

### Kiến trúc, quy trình, kiểm thử (8:15–9:30), slide 6–8

- Một ứng dụng Spring Boot chia 17 module nghiệp vụ, một ứng dụng React, PostgreSQL. Realtime qua WebSocket (STOMP). Chạy bằng Docker Compose, HTTPS bằng Caddy.
- Database-first: thiết kế ERD rồi mới viết migration. CI so ERD với migration ở mỗi PR.
- Nhánh `feature` → `develop` → `main`. Mỗi PR chạy test backend, frontend và E2E; xanh mới được merge. Merge vào `main` thì build image và triển khai.
- Kết quả kiểm thử ([tài liệu 13](13-kiem-thu.md)):
  - 192 test backend, độ phủ 91,9%.
  - 83 test frontend.
  - 3 kịch bản E2E.
  - 56 test case theo tiêu chí chấp nhận.
  - Tải 30 người dùng: p95 23,8 ms, 0% lỗi.

### Kết luận (9:30–10:00), slide 9

- Đạt cả ba tiêu chí nghiệm thu: trọn luồng QR tới bàn trống, phân quyền theo vai trò, CI tự chạy test.
- Việc tiếp theo:
  - Đưa lên máy chủ thật, nối SePay thật.
  - Nhiều chi nhánh, chạy khi mất mạng: đã có thiết kế ở [tài liệu 12](12-thiet-ke-mo-rong.md).
- Mời hỏi đáp.

## 15.4 Khi có sự cố

| Sự cố | Cách xử lý |
|---|---|
| Điện thoại không mở được trang QR | Mở đường link của thẻ QR trên laptop, ở chế độ điện thoại của Chrome (F12, biểu tượng điện thoại) |
| Gửi webhook mà bill không đóng | Kiểm tra mã và số tiền trong lệnh. Vẫn không được thì thu ngân bấm **Xác nhận tay**: đây cũng là cách quán xử lý khi SePay lỗi |
| Không nghe âm báo | Bấm **Chạm để bật âm báo**, kiểm tra loa |
| Báo cáo trống | Chưa nạp `perf/seed-history.sql`, hoặc khoảng ngày không có đơn |
| Docker hoặc laptop lỗi | Chiếu video quay sẵn |

Câu hỏi hay gặp:

| Câu hỏi | Trả lời |
|---|---|
| Khách gửi món đùa thì sao? | Món chờ nhân viên xác nhận mới vào bếp. Mỗi bàn gửi tối đa 10 lần mỗi phút, tối đa 30 món chờ (BR-30). Lộ mã thì tạo lại QR, mã cũ hết hiệu lực ngay |
| Ai đó gửi webhook giả thì sao? | Webhook phải có đúng khoá API. Sai tiền, sai mã thì giao dịch vào danh sách "không khớp", không tự đóng bill (BR-16) |
| Mất mạng thì sao? | Bản này cần mạng. Chạy khi mất mạng đã có thiết kế ở tài liệu 12, chưa làm |
| Sao không chia microservice? | Một nhà hàng không cần. Monolith chia module dễ chạy, dễ sao lưu; mỗi người vẫn giữ một module |

## 15.5 Dàn ý slide

| # | Tiêu đề | Nội dung |
|---|---|---|
| 1 | Khói Bếp: hệ thống quản lý nhà hàng | Tên đồ án, 5 thành viên |
| 2 | Vấn đề ở quán | Order giấy, bếp không biết thứ tự, dò chuyển khoản, kho sổ tay, không có số liệu |
| 3 | Mục tiêu và người dùng | G1–G6 kèm cách đo; 5 vai trò nhân viên và khách |
| 4 | Demo | Bốn phần demo, theo bảng ở mục 15.1 |
| 5 | Tính năng mở rộng | Đặt bàn và cọc, đơn app, khách hàng, hoá đơn điện tử, kèm ảnh |
| 6 | Kiến trúc | Sơ đồ ở mục 9.1; 17 module nghiệp vụ, 35 bảng, 23 migration |
| 7 | Quy trình và CI/CD | Database-first, nhánh, pipeline; 49 PR đã merge |
| 8 | Kiểm thử | Số test, độ phủ, E2E, kiểm thử tải |
| 9 | Kết luận | Ba tiêu chí nghiệm thu, việc tiếp theo, hỏi đáp |
