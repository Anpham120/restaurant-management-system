# SRS — Phần 2: Yêu cầu phi chức năng và yêu cầu chuyển đổi

> Phương pháp: skill `functional-vs-nonfunctional-splitter`, `constraint-detector`, `quality-attribute-scenario-writer` (45ck/software-architecture-skills).
> **Tin cậy:** ✅ khách đã xác nhận · 🔵 BA đề xuất, cần xác nhận khi kiểm tra kỹ thuật.
> Mỗi yêu cầu có **cách kiểm chứng**. Không yêu cầu nào được viết kiểu "nhanh", "dễ dùng" mà không có số đo.

## 1. Hiệu năng và khả năng sử dụng

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-01 | Phục vụ đã được đào tạo thêm một món vào bàn đang mở và gửi bếp **nhanh như ghi giấy** | Mục tiêu **≤ 4 chạm, ~10 giây**. Đây là **mục tiêu thử nghiệm**, ngưỡng nghiệm thu chốt sau tối thử | Bấm giờ trên tình huống thật (món thêm, ghi chú đặc biệt) trong tối thử | S5-N1, S4-Z1 | ✅ |
| NFR-02 | Phiếu bếp **in hoặc hiện** sau khi phục vụ bấm gửi | ≤ **3 giây** (P95) trong mạng LAN của quán | So thời điểm gửi và thời điểm in trong nhật ký | S5-N1 | ✅ |
| NFR-03 | Phản hồi thao tác trên thiết bị trong quán | ≤ **1 giây** (P95) | Kiểm thử tải với 10 thiết bị đồng thời | — | 🔵 |
| NFR-04 | Dashboard của chủ cập nhật khi có mạng | Trễ ≤ **5 phút**. Báo cáo cuối ngày sẵn sàng ≤ **15 phút** sau khi chốt ca cuối | Kiểm thử đầu cuối | S4-O6 | 🔵 |
| NFR-05 | Màn hình bếp dùng được trong bếp nóng, ồn, tay ướt | Chữ đọc được từ **2m**; vùng chạm ≥ **15mm**; **không dựa vào âm thanh** để báo; có chế độ tương phản cao | Thử tại bếp Đống Đa với bếp trưởng | S3-K3 | ✅ (định tính) / 🔵 (số) |
| NFR-06 | Tách bill theo món **không chậm hơn** cách làm tay hiện tại | Đo trong tối thử với 3 tình huống: lẩu dùng chung, bia, chia 3 người | Bấm giờ | S2-C7 | ✅ |
| NFR-07 | Giao diện **tiếng Việt**, dùng đúng thuật ngữ trong [từ điển](../02-phan-tich-nghiep-vu/04-thuat-ngu.md). Tiền VND có dấu chấm phân cách nghìn; ngày dd/mm/yyyy; giờ 24h; múi giờ Asia/Ho_Chi_Minh | 100% màn hình | Duyệt giao diện | — | 🔵 |

## 2. Tính sẵn sàng, chạy khi mất mạng, khôi phục

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-08 | Khi **mất Internet**, các chức năng ở FR-OFF-01 vẫn chạy trong quán | ≥ **4 giờ** (nếu còn điện). **0 bản ghi trùng** sau đồng bộ | Kiểm thử ngắt mạng, **gồm cả phiếu đã in ngay trước khi mất mạng** | S5-N2, S6-R6 | ✅ |
| NFR-09 | **UPS** cho máy chủ tại quán, router và máy in bếp | Đủ để đóng các bill đang mở và chuyển sang quy trình giấy an toàn. Đề xuất **≥ 30 phút** | Thử ngắt điện | S5-D7 | 🔵 (số phút) |
| NFR-10 | **4G dự phòng** tự chuyển khi mất đường truyền chính | Chuyển trong ≤ **1 phút**; các chức năng cloud (QR, dashboard, duyệt từ xa) chạy tiếp | Thử rút dây Internet | S5-D7 | 🔵 |
| NFR-11 | Tính sẵn sàng trong giờ phục vụ **10:00–23:30** | Hệ thống tại quán ≥ **99,5%/tháng**; cloud ≥ **99,5%/tháng** | Giám sát | — | 🔵 |
| NFR-12 | Không có **một điểm hỏng** nào làm dừng hẳn việc thu tiền | Máy chủ tại quán hỏng thì chuyển sang phiếu giấy và khôi phục theo kịch bản TR-07. Có máy cầm tay dự phòng. Có đường in dự phòng | Diễn tập sự cố | S5 sign-off, S6-R9 | ✅ |
| NFR-13 | Mất dữ liệu và thời gian khôi phục | Khi online: dữ liệu lên cloud ≤ **5 phút** (RPO). Khi offline: dữ liệu lưu bền trên máy chủ tại quán. Thay máy chủ tại quán bằng máy dự phòng ≤ **2 giờ** (RTO) | Diễn tập khôi phục | — | 🔵 |

## 3. Bảo mật và dữ liệu cá nhân

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-14 | **Đăng nhập cá nhân**: PIN trên thiết bị của quán; **không dùng điện thoại cá nhân** cho phục vụ. Tài khoản web của chủ và kế toán có **xác thực 2 lớp** | 100% thao tác gắn với một người | Kiểm thử phân quyền | S4-O7, S5-N7 | ✅ / 🔵 (2 lớp) |
| NFR-15 | **Mã hoá đường truyền** TLS 1.2 trở lên giữa quán, cloud và đối tác. **Wi-Fi nội bộ tách khỏi Wi-Fi khách** (WPA2/WPA3) | 100% kết nối | Rà cấu hình | — | 🔵 |
| NFR-16 | **Nhật ký kiểm toán không sửa được**, lưu cùng thời hạn với dữ liệu giao dịch | Không có API sửa hoặc xoá | Kiểm thử | S4-F11 | ✅ |
| NFR-17 | **Phân quyền** đúng ma trận FR-ADM-03 | Có ca kiểm thử cho từng vai trò | Kiểm thử phân quyền | S4-O5 | ✅ |
| NFR-18 | **Dữ liệu cá nhân** theo Luật Bảo vệ dữ liệu cá nhân và NĐ 356/2025: thu thập tối thiểu (tên, số điện thoại, thông tin HĐ khi cần); lưu **đồng ý**; đáp ứng **quyền của chủ thể dữ liệu**; ghi nhật ký truy cập dữ liệu cá nhân | Tuân thủ | Rà soát pháp lý | S5-N5 | ✅ |
| NFR-19 | **Sao lưu hằng ngày**, giữ bản sao lưu ≥ 30 ngày; **thử khôi phục mỗi quý** | Có biên bản thử khôi phục | Diễn tập | S5-N4 | ✅ / 🔵 (số) |

## 4. Dữ liệu và tính đúng

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-20 | **Lưu trữ ≥ 10 năm** dữ liệu bán hàng, HĐ, kho, nhật ký. Dữ liệu quá 2 năm có thể chuyển sang lưu trữ lạnh nhưng **truy xuất được trong ≤ 1 ngày làm việc** | Theo xác nhận của tư vấn (OI-07) | Thử truy xuất | S4-F11, S5-N4 | ✅ |
| NFR-21 | **Công ty sở hữu dữ liệu**; xuất toàn bộ CSV/Excel trong ≤ 1 ngày làm việc; khi chấm dứt hợp đồng thì bàn giao đầy đủ | Có điều khoản hợp đồng | Thử xuất | S4-O8 | ✅ |
| NFR-22 | **Tiền lưu dạng số nguyên VND**, không dùng số thực. Tổng các phần tách bill chính xác từng đồng | 0 lệch làm tròn | Kiểm thử đơn vị | S7-Q3 | ✅ |
| NFR-23 | **Thuế suất là dữ liệu có ngày hiệu lực** theo loại thuế. Mức 8% chỉ áp dụng tới **31/12/2026** trong khi thí điểm bắt đầu 02/2027, nên hệ thống phải đổi thuế suất bằng cấu hình, **không cần sửa phần mềm** | Đổi thuế suất không cần phát hành mới | Kiểm thử | S4-F1, khảo sát Codex | 🔵 (**cần kế toán xác nhận thuế suất 2027**) |
| NFR-24 | **Đồng bộ giờ** (NTP) trên máy chủ tại quán và thiết bị; mọi mốc thời gian lưu kèm múi giờ | Lệch ≤ 1 giây | Giám sát | — | 🔵 |

## 5. Quy mô

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-25 | Hỗ trợ **4 quán** (quán thứ 4 có thể mở năm 2027) mà không đổi kiến trúc | Thêm quán chỉ bằng cấu hình | Kiểm thử | S4-O9, S5-N6 | ✅ |
| NFR-26 | Định cỡ mỗi quán: ~5 máy cầm tay (Đống Đa), 1–2 máy thu ngân, 1–2 màn hình bếp, tối đa 4 máy in (chốt sau khảo sát). Toàn chuỗi ≤ **5.000 dòng order/ngày** (gấp ~3 lần ước tính cao điểm) | Kiểm thử tải | Kiểm thử | S5-N6 | 🔵 |
| NFR-27 | **Cách tính giá không phạt theo số thiết bị** (không tính tiền cho 20 thiết bị mỗi quán) | Có trong báo giá | Duyệt báo giá | S5-N6 | ✅ |

## 6. Vận hành, hỗ trợ, chi phí

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-28 | **Hỗ trợ 10:00–23:30 hằng ngày**. Khi ngừng gọi món hoặc thanh toán: **phản hồi đầu tiên ≤ 15 phút** kèm cách tiếp tục phục vụ. Sửa phần cứng có thể lâu hơn | Có trong SLA | Theo dõi phiếu hỗ trợ | S5-N3 | ✅ |
| NFR-29 | **Giám sát**: máy chủ tại quán gửi tín hiệu sống mỗi phút; mất tín hiệu > 15 phút trong giờ phục vụ thì cảnh báo chủ (BR-29) | Có | Thử ngắt mạng | S4-O6 | ✅ |
| NFR-30 | **Cập nhật phần mềm ngoài giờ phục vụ**. **Không cập nhật** trong cao điểm tất niên và Tết (16/01–09/02/2027) và trong **tháng 4** (trừ sửa lỗi khẩn) | Lịch phát hành | Duyệt lịch | S1-Q10, S7-Q1 | ✅ |
| NFR-31 | **Chi phí cố định** (hosting và hỗ trợ cho 3 quán): **8–12 triệu/tháng**. **Chi phí biến đổi** (phần cứng, HĐĐT theo số hóa đơn, SMS/Zalo, phí thanh toán, dịch vụ trung gian ngân hàng, hoa hồng app) được **ước tính theo mức dùng dự kiến** trước khi chủ duyệt | Có trong kế hoạch có chi phí | Chủ duyệt | S5-N8, S6-R7 | ✅ |
| NFR-32 | **Hosting ưu tiên tại Việt Nam**, có báo giá so sánh với phương án khác; không loại phương án khác trước khi hiểu tác động | Có bảng so sánh | Chủ duyệt | S5-N5 | ✅ |

## 7. Tuân thủ pháp lý

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-33 | Dữ liệu HĐĐT **đủ nội dung bắt buộc** theo **NĐ 254/2026/NĐ-CP, Điều 10** (người mua: tên, địa chỉ, MST hoặc số định danh; tên hàng, đơn vị, số lượng, đơn giá; thành tiền chưa thuế; thuế suất; tiền thuế theo từng thuế suất; tổng thanh toán). HĐ phát hành qua nhà cung cấp HĐĐT | 100% trường bắt buộc | Kế toán duyệt mẫu | S4-F2, NĐ 254/2026 | ✅ |
| NFR-34 | Không xoá HĐ đã phát hành; điều chỉnh theo quy trình của nhà cung cấp | Không có chức năng xoá | Kiểm thử | S4-F2 | ✅ |

## 8. Kênh khách QR và tự thanh toán (CR-01)

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-35 | Trang khách **mở nhanh trên mạng di động** | Tải lần đầu ≤ **3 giây** trên 4G (P75); JavaScript tải lần đầu ≤ **300 KB** (nén gzip) | Đo bằng Lighthouse và điện thoại thật | S9-O1 | 🔵 |
| NFR-36 | Trạng thái món và thanh toán **tới điện thoại khách và nhân viên** | Trễ ≤ **5 giây** (P95) khi có mạng; nhân viên không phải chờ màn hình để nhả bàn cho khách đã trả | Kiểm thử đầu cuối | S10-R9 | 🔵 |
| NFR-37 | Thời gian xác nhận thanh toán **phía hệ thống** | Từ lúc nhận thông báo hợp lệ của ngân hàng tới lúc bill đổi trạng thái ≤ **10 giây** (P95). Độ trễ phía ngân hàng **đo và báo cáo riêng** (G7) | Nhật ký thời điểm | S10-G7 | 🔵 |
| NFR-38 | **Bảo mật kênh khách** | Token QR ≥ 128 bit, sinh bằng bộ sinh ngẫu nhiên an toàn. Mã ngồi bàn khoá sau 5 lần sai trong 15 phút. Giới hạn gửi đơn (ví dụ ≤ 10 lần/phút/điện thoại). Kênh realtime **phân quyền theo phiên** (khách chỉ nhận được sự kiện của phiên mình). **Không lộ dữ liệu bàn khác.** HTTPS, HSTS, CSP | Kiểm thử bảo mật, pentest nhẹ trước go-live | S9-L3, S10-R2 | 🔵 |
| NFR-39 | **Chống lừa đảo thanh toán** | Trang thanh toán chỉ trên **tên miền của quán**, hiện **tên công ty và số tài khoản**. Thông báo ngân hàng phải **xác thực chữ ký** và **chống gửi lại** (idempotency theo mã giao dịch). **Không bao giờ** xác nhận theo trang trả về (return URL) | Kiểm thử | S9-Z1, CR-01 §2 | ✅ |
| NFR-40 | **Dữ liệu cá nhân kênh khách** | Không bắt buộc đăng nhập. Chỉ thu dữ liệu tối thiểu. Có **thông báo xử lý dữ liệu** trên trang. Dữ liệu phiên khách (không phải giao dịch) xoá sau **30 ngày** | Rà soát pháp lý | S9-O3, CON-07 | 🔵 (số ngày) |
| NFR-41 | **Tương thích trình duyệt** | Chrome và Safari 2 phiên bản gần nhất; **trình duyệt trong app Zalo** và camera mặc định của iOS và Android; có phương án khi deeplink ngân hàng không chạy (lưu ảnh QR, nhờ nhân viên) | Thử ở S11, S12 | RSK-16 | 🔵 |
| NFR-42 | **Dễ dùng cho mọi lứa tuổi** | Chữ ≥ 16px, vùng chạm ≥ 44px, tương phản đạt WCAG AA; tiếng Việt (tiếng Anh: Could) | Thử với khách thật | S9-L7 | 🔵 |
| NFR-47 | *(CR-02)* **Quét QR trên máy nhân viên** nhanh và đọc được **trong điều kiện thật** | Nhận diện bàn ≤ **2 giây** (P90) trong ánh sáng buổi tối của quán; đọc được thẻ hơi ẩm; tên bàn trên màn hình chữ ≥ 24px | Thử ban đêm ở bàn phía trước Cầu Giấy, sân trong Đống Đa, sân thượng Hai Bà Trưng (TR-17) | S11-T1, S12-V3 | 🔵 |
| NFR-48 | *(CR-02)* **Camera trong app web cần HTTPS**: app nhân viên (PWA) phải chạy qua HTTPS **cả trong mạng nội bộ**. Máy chủ tại quán có chứng chỉ hợp lệ cho tên nội bộ (ví dụ `dda.edge.bnn.vn` trỏ về IP LAN, chứng chỉ cấp qua ACME DNS-01), hoặc dùng CA nội bộ cài trên thiết bị của quán | 100% thiết bị mở được camera, **kể cả khi mất Internet** | Kiểm tra kỹ thuật S13 | Kiến trúc | 🔵 |

## 9. Công nghệ và CI/CD (yêu cầu của nhóm dự án)

| ID | Yêu cầu | Chỉ số / ngưỡng | Kiểm chứng | Nguồn | Tin cậy |
|---|---|---|---|---|---|
| NFR-43 | **Công nghệ bắt buộc**: backend **Java 21 LTS + Spring Boot 3.x**; frontend **React + TypeScript**; CSDL **PostgreSQL 16**; đóng gói **Docker** | 100% thành phần | Duyệt kiến trúc | DEC-26 | ✅ |
| NFR-44 | **CI chạy cho mọi pull request**: build; test đơn vị và tích hợp (PostgreSQL bằng Testcontainers); lint; kiểm tra ranh giới mô-đun; SAST; quét phụ thuộc và image | Không merge khi pipeline đỏ. Độ phủ dòng ≥ **70%** cho mô-đun Billing, Outlet Ops, Payments. **0 lỗ hổng mức Critical** | Báo cáo CI | DEC-26 | 🔵 (số) |
| NFR-45 | **CD**: image có phiên bản (semver + SHA); **staging tự động**; **production cần duyệt**; migration Flyway tương thích ngược; **rollback ≤ 15 phút**. Edge được cập nhật theo lịch ngoài giờ phục vụ và tôn trọng lịch đóng băng NFR-30 | Có quy trình và đã diễn tập | Diễn tập rollback | DEC-26, NFR-30 | 🔵 |
| NFR-46 | **E2E**: bộ Playwright cho các AC then chốt (gọi món, huỷ, tách bill, QR, tự thanh toán, offline) chạy trên staging trước mỗi lần lên production | 100% kịch bản then chốt đạt | Báo cáo CI | DEC-26 | 🔵 |

---

## 10. Yêu cầu chuyển đổi (Transition requirements)

Đây là những việc **phải làm để đưa hệ thống vào dùng**, không phải tính năng phần mềm.

| ID | Yêu cầu | Người chịu trách nhiệm | Mốc | Nguồn |
|---|---|---|---|---|
| TR-01 | **Đo baseline**: sổ lỗi món sót và trùng kèm nguyên nhân (Lan); kiểm kê và luân chuyển 15 mặt hàng ưu tiên (Minh, Thảo, các bếp); bấm giờ khớp số hằng ngày (Hạnh, báo cáo tháng) | Lan, Minh, Thảo, Hạnh | 10–12/2026 | S5-D10 |
| TR-02 | **Làm sạch và chuyển đổi dữ liệu**: sơ đồ bàn (Lan); menu, đơn vị, quy đổi (Minh cùng bếp); loại thuế (Hạnh); khách hàng trùng. Đống Đa xong **15/12/2026**; Cầu Giấy và Hai Bà Trưng xong **31/01/2027** | Lan, Minh, Hạnh | 15/12, 31/01 | S5-D9, S4-Z1 |
| TR-03 | **Kiểm tra kỹ thuật 2 tuần** kèm chi phí và **thử phần mềm có sẵn cho phần sảnh** (xem [kiến trúc §9](../04-thiet-ke-he-thong/01-kien-truc.md)) | Tech lead, BA | 05–16/10/2026 | S5-B, D5 |
| TR-04 | **Khảo sát hiện trường**: bếp Đống Đa (nhiệt, dầu mỡ, dây điện, vị trí phiếu) cùng bếp trưởng; **Wi-Fi ở bàn mặt tiền** Cầu Giấy; phòng riêng và sân thượng Hai Bà Trưng | Tech lead, Đức, Thanh, Quyên | Trong TR-03 | S5-D8, S8 |
| TR-05 | **Đào tạo theo vai trò**, gồm **part-time chỉ làm cuối tuần** (không chỉ một buổi chiều); có hướng dẫn ngắn theo vai trò | Lan và các quản lý | Trước mỗi go-live | S5, S6-R9 |
| TR-06 | **Tối thử có giám sát** vào tối đông thật, chạy song song giấy; **diễn tập mất mạng và khôi phục** (gồm phiếu đã in ngay trước khi mất mạng) với **cả đội** (thu ngân, phục vụ, bếp) | Lan, Đức, Thanh | Đầu 01/2027 (Đống Đa); trước go-live Cầu Giấy | S5-A4, S6-R6, S8-B2 |
| TR-07 | **Kịch bản sự cố** (playbook): máy cầm tay dự phòng; đường in dự phòng; **phiếu giấy đánh số in sẵn**; chế độ khôi phục; một người đối chiếu, quản lý ký; số điện thoại hỗ trợ | Tech lead, Lan | Trước go-live Đống Đa | S6-R9 |
| TR-08 | **Tiêu chí go-live (go/no-go)**. Đống Đa: đạt TR-05, TR-06, TR-07; UPS và 4G đã lắp. Cầu Giấy: **cả đội đã diễn tập sự cố**. Hai Bà Trưng: **thử một booking tiệc thật** của Hai Bà Trưng (phòng riêng, chuyển bàn sân thượng, nhiều bên trả tiền). Không go-live quán tiếp theo khi thí điểm còn lỗi nghiêm trọng về gọi món hoặc thanh toán | Chủ | Mỗi go-live | S7-Q1, S8-B2 |
| TR-09 | **Văn bản chính sách**: chính sách giảm giá và huỷ món (BR-01 đến BR-08); **điều khoản cọc** (kế toán duyệt câu chữ); quy định nhận hàng (ngưỡng nhiệt độ, OI-01) | Chủ, Hạnh, Minh, Đức | Trước go-live Đống Đa | S5-D3, S6-R10 |
| TR-10 | **Đóng băng thay đổi** trong cao điểm tất niên, Tết và tháng 4 (NFR-30) | Tech lead | 2027 | S7-Q1 |
| TR-11 | **Thử tuyến ngân hàng thật** (Vietcombank hoặc nhà cung cấp được duyệt) với thu ngân và kế toán **trước khi bật tự thanh toán** (BR-59). Thử cả trả đủ, trả thiếu, trả thừa, sai mã, trả trùng, thông báo trễ | Huy, Hạnh, Tech lead | Trước khi bật QR tự thanh toán | S10-Q2 |
| TR-12 | **Đo baseline 1–2 tuần** cho G6 và G7 trước QR-1: số món gọi thêm ở 6 bàn, thời gian từ "tính tiền" tới xác nhận thanh toán | Lan, Huy | Trước QR-1 | S9-O1, S10-G7 |
| TR-13 | **Thử khối lượng việc của mã ngồi bàn** (đưa thẻ, đổi mã khi dọn bàn) trong buổi trưa và tối thử | Lan | Buổi thử QR-1 | S10-R2 |
| TR-14 | **Thử trên điện thoại khách thật**: mở bằng camera và bằng Zalo; deeplink tới các app ngân hàng phổ biến; lưu ảnh QR (S11, S12) | Tech lead | Kiểm tra kỹ thuật | CR-01 §2 |
| TR-15 | **Đào tạo phục vụ** về hàng chờ xác nhận QR, nhóm món luôn chờ người (BR-43), kiểm tra tuổi khi mang bia, xử lý gọi nhân viên; **không bỏ bê bàn dùng QR** | Lan | Trước QR-1 | S9-L7 |
| TR-16 | **Quy trình kiểm tra thẻ QR** mỗi lần mở bàn; kho thẻ dự phòng; cách báo và thay thẻ nghi bị giả | Lan | Trước QR-1 | S10-R13 |
| TR-17 | *(CR-02)* **Thử quét thẻ ban đêm** ở bàn phía trước Cầu Giấy, sân trong Đống Đa, sân thượng Hai Bà Trưng; thử thẻ ẩm; chọn vị trí đặt thẻ để không phải dời dĩa | Thanh, Lan, Quyên, tech lead | Kiểm tra kỹ thuật (S13) | S11-T1, S12-V3 |
| TR-18 | *(CR-02)* **In và gắn thẻ QR cho đủ 24 bàn Đống Đa** (kèm thẻ dự phòng) **trước khi tập quét**. Cầu Giấy và Hai Bà Trưng gắn khi go-live. Phòng riêng mỗi bàn một thẻ | Lan, Thanh, Quyên | Trước buổi tập dượt tháng 1 | S12-V3 |
| TR-19 | *(CR-02)* **Cập nhật địa chỉ pháp lý theo phường hiện hành** trên hồ sơ nhà cung cấp HĐĐT, ngân hàng và dữ liệu quán | Hạnh | Trước go-live Đống Đa | S12-V1 |
