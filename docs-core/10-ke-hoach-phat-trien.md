# 10. Kế hoạch phát triển tiếp (sau bản core)

## 10.1 Hiện trạng

Bản core đã chạy trọn luồng: gọi món (phục vụ và khách quét QR), bếp realtime, thanh toán tiền mặt và VietQR tự xác nhận, kho, báo cáo, tài khoản nhân viên. Có 64 test backend, CI/CD trên GitHub (`feature/*` → `develop` → `main`). Chi tiết ở tài liệu 01 → 09.

**Lịch.** Hạn nộp cuối tháng 11/2026 (lấy mốc **30/11**), nhóm **5 người**, chia **8 sprint**. Sprint 1 dài 11 ngày (01/10 → 11/10) để kịp thuê máy chủ, tên miền và nối SePay. Các sprint sau dài 1 tuần, từ thứ Hai đến Chủ nhật. Mỗi sprint ước khoảng 12–17 ngày người, tức mỗi người 2,5–3,5 ngày mỗi tuần, đã tính phần nhân sự.

Mã `FR-...` dưới đây trỏ tới yêu cầu **đã phân tích sẵn** trong bản mở rộng ([`../docs/03-dac-ta-yeu-cau/01-yeu-cau-chuc-nang.md`](../docs/03-dac-ta-yeu-cau/01-yeu-cau-chuc-nang.md)). Làm tới đâu thì rút gọn và chuyển yêu cầu đó sang bộ `docs-core` tới đó.

## 10.2 Nguyên tắc ưu tiên

1. **Chạy ổn định và demo được trước.** Có staging thật, test E2E, sao lưu dữ liệu, rồi mới thêm tính năng.
2. **Tính năng nhà hàng dùng hằng ngày**, như chuyển bàn, in bill, giảm giá, chốt ca, xếp trước các tính năng "cho đủ".
3. **Mỗi tính năng làm trọn một vòng**: tài liệu → CSDL → backend → frontend → test → demo trên staging.
4. **Việc lớn, rủi ro cao chỉ thiết kế**, không làm: nhiều chi nhánh, chạy offline.

## 10.3 Backlog theo giai đoạn

Công sức: **S** ≤ 2 ngày người, **M** 3–5 ngày người, **L** > 5 ngày người. Ưu tiên theo MoSCoW. Cột CSDL ghi migration dự kiến, số thật đánh khi làm.

### Giai đoạn 0 — Nền tảng vận hành (sprint 1)

| Mã | Việc | Công sức | Ưu tiên | CSDL | Xong khi |
|---|---|---|---|---|---|
| P0-01 | Bật rulesets cho `main`, `develop`; tạo environment `staging`, `production` | S | M | — | Không push thẳng được vào `main`, `develop` |
| P0-02 | Máy chủ staging (VPS 1–2 GB RAM) + tên miền + HTTPS bằng Caddy; bật `DEPLOY_ENABLED` | M | M | — | Push `develop` là staging tự cập nhật |
| P0-03 | Nối **SePay thật** trên staging, chuyển thử 2.000 đ | S | M | — | Tiền thật về, bàn tự đóng |
| P0-04 | Sao lưu CSDL hằng ngày (`pg_dump`, giữ 7 bản) và **thử khôi phục** | S | M | — | Khôi phục được bản hôm qua |
| P0-05 | **Test E2E Playwright** cho kịch bản nghiệm thu §1.5, chạy trong CI bằng Docker Compose | M | M | — | CI đỏ nếu luồng chính hỏng |
| P0-06 | Dependabot (Maven, npm, Actions, Docker), CodeQL, quét image bằng Trivy | S | S | — | Có cảnh báo lỗ hổng tự động |

### Giai đoạn 1 — Nghiệp vụ tại quán còn thiếu (sprint 2–3)

| Mã | Việc | Nguồn | Công sức | Ưu tiên | CSDL |
|---|---|---|---|---|---|
| P1-01 | **Chuyển bàn và ghép bàn** (một đơn chiếm nhiều bàn) | FR-TBL-04, 05 | L | M | Bảng `order_table`; chuyển ràng buộc "một bàn một đơn mở" (BR-04) sang bảng này |
| P1-02 | **In bill tạm tính và hoá đơn** khổ 80 mm từ trình duyệt | FR-BIL-01 | S | M | — |
| P1-03 | **Nhật ký thao tác** cho huỷ món, giảm giá, xác nhận tay, sửa giá | FR-AUD-01, 04 | M | M | Bảng `audit_entry`, chỉ thêm, không sửa |
| P1-04 | **Giảm giá, tặng món** có lý do; vượt hạn mức thì quản lý duyệt | FR-BIL-04, 05 | M | M | Bảng `adjustment`; sửa BR-12 |
| P1-05 | **Khách bấm "Gọi nhân viên", "Yêu cầu tính tiền"** trên trang QR | FR-GST-11 | M | S | Bảng `service_request` |
| P1-06 | **Âm báo**: bếp có món mới, phục vụ có món xong hoặc đơn QR mới; tô đỏ món chờ quá lâu | FR-KIT-03, 08 | S | S | Ngưỡng chờ trong `restaurant_settings` |
| P1-07 | **Tách bill**: chia đều hoặc theo món, nhiều khoản thanh toán cho một đơn | FR-BIL-02, 07 | L | C | Bỏ `ux_payment_paid_order`; sửa BR-13 |

P1-05 và P1-06 **đã làm xong** (issue #12, #13), trước lịch:
- P1-05: yêu cầu FR-06.6, FR-06.7, US-27, BR-29; CSDL là migration `V9` (bảng `service_request`). Bản core có 2 loại yêu cầu. Danh sách yêu cầu nhanh (đá, khăn giấy...) và cờ khẩn của FR-GST-11 chưa làm.
- P1-06: yêu cầu FR-07.4, FR-07.5, FR-11.3 và BR-28; CSDL là migration `V8`.

### Giai đoạn 2 — Ca két, kho, nhân sự, báo cáo (sprint 1–6)

| Mã | Việc | Nguồn | Công sức | Ưu tiên | CSDL |
|---|---|---|---|---|---|
| P2-01 | **Ca và két**: mở ca, phiếu chi, chốt ca đối chiếu tiền mặt thực đếm | FR-SHF-01, 02, 04 | L | M | Bảng `cash_shift`, `cash_expense`; `payment.cash_shift_id` |
| P2-02 | **Định lượng món** và **trừ kho tự động** khi món vào bếp | FR-INV-03, 04 | L | S | Bảng `recipe_line`; thêm loại phiếu `SALE` |
| P2-03 | **Nhà cung cấp và phiếu nhập có giá**, cập nhật giá vốn | FR-PUR-01, FR-INV-10 | M | S | Bảng `supplier`, `goods_receipt`, `receipt_line` |
| P2-04 | **Báo cáo** lãi gộp theo món, báo cáo ngoại lệ (huỷ, giảm giá, xác nhận tay), biểu đồ, xuất Excel | FR-RPT-06, 07, FR-INT-05 | M | S | View cho báo cáo |
| P2-05 | **Hồ sơ nhân viên**: số điện thoại, ngày vào làm, ngày nghỉ việc, hình thức và mức lương | FR-12 | S | M | Thêm 5 cột vào `employee` |
| P2-06 | **Xếp ca**: ca mẫu, lịch tuần, sao chép tuần trước; nhân viên xem lịch của mình | FR-13.1 → 13.4 | M | M | Bảng `work_shift`, `shift_assignment` |
| P2-07 | **Chấm công**: vào ca, ra ca trên app; đi muộn, về sớm; quản lý sửa có lý do | FR-14 | M | M | Bảng `attendance` |
| P2-08 | **Nghỉ phép**: gửi đơn, quản lý duyệt, gỡ ca đã xếp | FR-13.5, 13.6 | M | S | Bảng `leave_request` |
| P2-09 | **Bảng lương tháng**: tính từ chấm công, thưởng phạt, chốt, phiếu lương, xuất Excel | FR-15 | L | M | Bảng `payroll`, `payslip`, `pay_adjustment` |

P2-05 → P2-09 là phần **nhân sự**, thêm theo yêu cầu của môn. Yêu cầu nằm ở tài liệu 02 (FR-12 → FR-15, không phải bản mở rộng), CSDL ở [tài liệu 07, mục 7.5](07-erd.md#75-nhân-sự). **Đã làm xong** cả 5 việc (issue #35 → #39), trước lịch.

### Giai đoạn 3 — Chất lượng, bảo mật, hiệu năng (sprint 6)

| Mã | Việc | Công sức | Ưu tiên |
|---|---|---|---|
| P3-01 | **Giới hạn tần suất** API công khai (Bucket4j), giới hạn số món chờ xác nhận mỗi bàn: chống spam đơn QR | S | M |
| P3-02 | Thu hồi token khi đổi mật khẩu, "đăng xuất mọi thiết bị" | M | S |
| P3-03 | **Kiểm thử tải k6** theo NFR-02 (30 người, p95 ≤ 500 ms); sửa truy vấn chậm | S | M |
| P3-04 | Test component cho frontend (Testing Library); đo độ phủ backend bằng JaCoCo, mục tiêu ≥ 70% | M | S |
| P3-05 | Log JSON, số liệu Actuator; cảnh báo khi webhook SePay lỗi liên tục | S | C |

### Giai đoạn 4 — Mở rộng, chọn theo thời gian còn lại (sprint 7)

| Mã | Việc | Nguồn | Công sức | Ưu tiên |
|---|---|---|---|---|
| P4-01 | **Đặt bàn và cọc** qua VietQR, cấn trừ cọc vào bill | FR-RSV-01 → 04 | L | S |
| P4-02 | **Khách hàng** theo số điện thoại, lịch sử ghé, đồng ý nhận tin | FR-CUS-01 → 03 | M | C |
| P4-03 | **Hoá đơn điện tử**: hàng chờ dữ liệu HĐĐT, xuất file theo mẫu nhà cung cấp; tích hợp API sau | FR-BIL-12, 15, FR-INT-02, 03 | L | C |
| P4-04 | **Đơn app giao hàng** nhập tay (GrabFood, ShopeeFood), chống trùng mã đơn | FR-DLV-02, 03 | M | C |
| P4-05 | Nhiều chi nhánh; chạy offline khi mất mạng | FR-OFF | XL | W, chỉ thiết kế |

### Giai đoạn 5 — Hồ sơ nộp môn (làm song song, chốt ở sprint 8)

| Mã | Việc |
|---|---|
| P5-01 | **Kế hoạch và báo cáo kiểm thử**: test case theo AC của US-01 → US-19, kết quả CI, E2E, kiểm thử tải |
| P5-02 | **Hướng dẫn sử dụng** theo vai trò, có ảnh màn hình |
| P5-03 | **Hướng dẫn triển khai, vận hành**: máy chủ, SePay, sao lưu và khôi phục |
| P5-04 | **Kịch bản demo 10 phút** và slide |
| P5-05 | Cập nhật tài liệu 01 → 09 theo tính năng mới (FR, US, BR, ERD, API) |

## 10.4 Lịch theo sprint

| Sprint | Thời gian | Việc xong trong sprint | Demo cuối sprint |
|---|---|---|---|
| 1 | 01/10 → 11/10 | Giai đoạn 0, P2-05 hồ sơ nhân viên | Staging HTTPS; chuyển khoản thật tự xác nhận; E2E chạy trong CI; hồ sơ nhân viên có mức lương |
| 2 | 12/10 → 18/10 | P1-02 in bill, P1-03 nhật ký, P1-06 âm báo | In bill 80 mm; nhật ký ghi lại món bị huỷ; bếp kêu khi có món mới |
| 3 | 19/10 → 25/10 | P1-01 chuyển và ghép bàn, P1-04 giảm giá có duyệt, P1-05 gọi nhân viên, P2-03 nhà cung cấp và phiếu nhập, P2-06 xếp ca, P2-08 nghỉ phép | Ghép 2 bàn cho một nhóm; quản lý duyệt giảm giá; khách gọi nhân viên từ điện thoại; nhập hàng có giá; xếp lịch tuần, duyệt đơn nghỉ |
| 4 | 26/10 → 01/11 | P2-07 chấm công, P3-04 khung test frontend và đo độ phủ | Vào ca, ra ca, bảng công; báo cáo độ phủ test |
| 5 | 02/11 → 08/11 | P2-01 ca và két, P2-02 định lượng và trừ kho, P3-03 kiểm thử tải | Chốt ca có chênh lệch; bán món thì kho tự trừ; báo cáo k6 |
| 6 | 09/11 → 15/11 | P2-04 báo cáo, P2-09 bảng lương, P3-01 chống spam đơn QR, P3-05 log và cảnh báo | Bảng lương tháng tính từ chấm công; báo cáo lãi gộp; chặn spam đơn QR |
| 7 | 16/11 → 22/11 | P4-01 đặt bàn và cọc; P3-02 thu hồi token và P1-07 tách bill nếu còn sức; việc tuỳ chọn giai đoạn 4 nếu kịp | Đặt bàn, cọc qua VietQR, cấn trừ vào bill |
| 8 | 23/11 → 29/11 | Giai đoạn 5; **ngừng thêm tính năng** | Demo toàn bộ trên production |

Mỗi người làm song song trên service của mình (mục 10.7), nên các giai đoạn gối lên nhau. Việc cỡ M, L bắt đầu sớm hơn một sprint:
- Từ sprint 2: P1-01, P2-03, P2-06, P2-08.
- Từ sprint 4: P2-01, P2-02.
- Từ sprint 5: P2-09.
- Từ sprint 6: P4-01, P1-07.

Mỗi cuối sprint: PR `develop` → `main` (merge commit), gắn tag phiên bản `v1.1`, `v1.2`...

Bảng việc nằm trên GitHub: mỗi sprint là một [milestone](https://github.com/Anpham120/restaurant-management-system/milestones), hạn là ngày cuối sprint; mỗi mã `P…` là một [issue](https://github.com/Anpham120/restaurant-management-system/issues).

## 10.5 Quy trình làm một tính năng (Definition of Done)

1. **Tài liệu trước**: thêm hoặc sửa FR (02), user story và AC (03), quy tắc (05). Đổi CSDL thì **sửa ERD (07) trước**.
2. **Migration mới** `V{n}__<tên>.sql`, **không sửa** migration đã chạy. `node scripts/check-erd.mjs` phải báo 0 lệch; CI cũng chạy script này. Nhiều người cùng thêm migration thì số `n` chốt lúc merge: nếu `develop` đã có số đó thì đổi tên file lên số kế tiếp.
3. Backend có test tích hợp cho từng AC; frontend có test cho phần tính toán.
4. Tạo nhánh `feature/<mã>-<tên>` từ `develop`, mở PR vào `develop`. CI xanh và 1 người review thì merge. Đụng service của người khác thì người đó review (mục 10.7).
5. Thử trên staging, rồi đánh dấu xong trên bảng việc.

## 10.6 Rủi ro

| Rủi ro | Ảnh hưởng | Cách xử lý |
|---|---|---|
| Chưa có tài khoản ngân hàng hoặc SePay để nối thật | Không demo được tiền thật | Giữ giả lập webhook (README); demo bằng lệnh `curl` |
| Ghép bàn và tách bill đổi quy tắc cốt lõi (BR-04, BR-13) | Dễ làm hỏng luồng đang chạy | Làm sau khi có E2E (P0-05); đổi ràng buộc bằng migration có test |
| Quy định HĐĐT thay đổi, cần tài khoản thử của nhà cung cấp | Tích hợp tốn thời gian | Chỉ làm hàng chờ và xuất file; API để sau |
| Chi phí VPS và tên miền | Nhỏ nhưng phát sinh | VPS 1–2 GB đủ cho staging và production chạy chung, cách nhau bằng `HTTP_PORT` |
| Thành viên bận thi hoặc bận môn khác | Trễ lịch | Cắt từ dưới lên: giai đoạn 4 trước, rồi P1-07, P3-02, P3-04, P3-05. Nhân sự là yêu cầu của môn, không cắt |
| Nhân sự nhiều việc, dồn vào một người | Trễ phần môn bắt buộc | Làm theo thứ tự hồ sơ → xếp ca → chấm công → lương. Trễ thì lùi P2-08 nghỉ phép trước |
| Dữ liệu lương nhạy cảm, repo để công khai | Lộ lương thật | Chỉ ADMIN xem mức lương; nhân viên chỉ xem phiếu của mình; dữ liệu mẫu và demo dùng số giả |

## 10.7 Phân công theo service

Theo yêu cầu của môn, mỗi người giữ **một service** và làm cả backend lẫn frontend của service đó, gồm cả test và tài liệu. Hạ tầng dùng chung không phải service, nên Anpham120 (mạnh hạ tầng) giữ thêm.

| Service | Người | Backend (`vn.bnn.rms.*`) | Frontend | Việc |
|---|---|---|---|---|
| Gọi món, bếp, QR | Anpham120 | `order` | `/orders/:id`, `/kitchen`, `/q/:token` | P1-05, P1-06, P3-01; tuỳ chọn P4-04 |
| Bàn và đặt bàn | totototototoads | `table` | `/tables`, `/admin/tables` | P1-01, P4-01; tuỳ chọn P4-02 |
| Thanh toán | buidaoducanh1210 | `payment`, `settings` | `/cashier`, `/admin/settings` | P0-03, P1-02, P1-03, P1-04, P2-01; P1-07 nếu còn sức; tuỳ chọn P4-03 |
| Thực đơn, kho, báo cáo | Tanh2k8-123 | `menu`, `inventory`, `report` | `/admin/menu`, `/admin/inventory`, `/admin/reports` | P2-02, P2-03, P2-04 |
| Nhân sự | quanghieu1605 | `auth`, `employee`, `schedule`, `attendance`, `leave`, `payroll` | `/login`, `/me`, `/admin/employees`, `/admin/schedule`, `/admin/attendance`, `/admin/leave`, `/admin/payroll` | P2-05 → P2-09 (đã xong); P3-02 nếu còn sức |
| Hạ tầng (dùng chung) | Anpham120 | `config`, `common`, Docker, CI/CD | — | P0-01, P0-02, P0-05, P3-03, P3-04, P3-05 |

Phần nhân sự nhiều việc, nên service Nhân sự chỉ giữ tài khoản và nhân sự. Ba phần khác chuyển đi:
- Cài đặt nhà hàng sang Thanh toán, vì chủ yếu là tài khoản nhận tiền.
- Báo cáo sang Thực đơn và kho.
- Nhật ký thao tác (P1-03) sang Thanh toán, vì phần lớn thao tác cần ghi là giảm giá, xác nhận tay, huỷ món.

Việc chung chia thêm cho đều tải:
- Giai đoạn 0: P0-04 và P0-06 (Tanh2k8-123), kịch bản E2E của P0-05 (totototototoads).
- Hồ sơ: P5-01 (Tanh2k8-123), P5-02 và P5-04 (totototototoads), P5-03 (Anpham120), P5-05 (buidaoducanh1210). Người được giao gom bài; phần của service nào do chủ service viết.
- P4-05 chỉ thiết kế, chưa giao.

Tải ước tính mỗi người khoảng 20–22 ngày người; Anpham120 cao nhất vì giữ thêm hạ tầng. Sprint nào service của mình không có việc mới thì viết thêm test, hướng dẫn, hoặc nhận việc tuỳ chọn.

Sửa code, bảng hoặc API thuộc service nào thì chủ service đó review PR. Các chỗ đã biết:
- P1-01 và P2-02 đụng `order`.
- P4-01 đụng `payment`.
- P1-06 thêm cột vào `restaurant_settings`.
- P1-03 và P2-04 đọc, ghi dữ liệu của nhiều service.
- P2-05 thêm cột vào `employee`, bảng mà mọi service đều trỏ tới.
