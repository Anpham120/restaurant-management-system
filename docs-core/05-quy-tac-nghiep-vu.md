# 5. Quy tắc nghiệp vụ

Mỗi quy tắc được kiểm tra **ở backend**. Giao diện chỉ ẩn hoặc khoá nút để dễ dùng. Cột "Kiểm tra ở" ghi nơi mã nguồn thực thi quy tắc.

## 5.1 Tài khoản và phân quyền

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-01 | Tên đăng nhập **duy nhất**. Mật khẩu ≥ 6 ký tự, lưu băm **BCrypt**. Token hết hạn sau **12 giờ** | `EmployeeService`, `AuthService` |
| BR-02 | Quyền theo vai trò như ma trận ở [mô hình miền §6.4](06-mo-hinh-mien.md#64-ma-trận-quyền). `ADMIN` có mọi quyền | `@PreAuthorize` trên controller |
| BR-03 | Nhân viên **không bị xoá**, chỉ bị khoá. Tài khoản bị khoá không đăng nhập được, token cũ bị từ chối ngay | `AuthService`, `ActiveEmployeeJwtConverter` |
| BR-31 | Mỗi tên đăng nhập được thử **tối đa 10 lần mỗi phút**, tính cả lần đúng. Quá giới hạn thì trả 429 kèm số giây phải chờ. Giới hạn theo tên đăng nhập, không theo địa chỉ IP, vì IP sau nginx có thể bị giả | `AuthService`, `RateLimiter` |

## 5.2 Bàn, đơn và món

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-04 | Mỗi bàn có **tối đa một đơn đang mở**. Bàn trống là bàn không có đơn mở. Đơn mang về không gắn bàn | Unique index `ux_orders_open_table` |
| BR-05 | Tên và **đơn giá được chốt** khi gọi món. Sửa giá thực đơn không đổi đơn đã gọi | `OrderService.buildItems` |
| BR-06 | Chỉ gọi được món **đang bán**. Mỗi dòng có số lượng từ 1 đến 50 | `OrderService`, validation |
| BR-07 | Trạng thái món chỉ đi tiến: **Chờ xác nhận → Chờ làm → Đang làm → Xong → Đã ra**. Bếp đổi "Chờ làm → Đang làm → Xong"; phục vụ đổi "Xong → Đã ra" | `ItemStatus.canMoveTo`, `OrderItemService` |
| BR-08 | Huỷ món: "Chờ xác nhận" và "Chờ làm" thì **phục vụ** huỷ được; "Đang làm" và "Xong" thì chỉ **quản lý** huỷ và **bắt buộc lý do**; "Đã ra" thì không huỷ. Đơn chỉ huỷ được khi mọi món đã huỷ | `OrderItemService.cancel` |
| BR-28 | Món **chờ lâu** khi đã vào bếp được từ số phút ngưỡng trở lên mà chưa ra bàn. Ngưỡng là số phút nguyên từ 1 đến 120, mặc định 15 | `SettingsRequest`, `CHECK` trên `restaurant_settings`; màn hình bếp tô màu |

## 5.3 Khách gọi món qua QR

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-09 | Mã QR bàn là **chuỗi ngẫu nhiên 128 bit**, không đoán được. Tạo lại mã thì mã cũ **hết hiệu lực ngay** | `QrTokenGenerator`, `TableService` |
| BR-10 | Món khách gửi ở trạng thái **Chờ xác nhận** và **không hiện ở bếp**. Món chỉ vào bếp khi nhân viên xác nhận. Từ chối phải có lý do, và khách thấy lý do. Nếu bàn chưa có đơn, lần gửi đầu tiên tự mở đơn | `GuestOrderService`, `OrderItemService` |
| BR-11 | Khách chỉ thấy **đơn đang mở của bàn có mã QR đó**. Trang khách không hiện tên nhân viên | `PublicController` |
| BR-29 | Mỗi bàn có **tối đa một yêu cầu đang chờ cho mỗi loại** (gọi nhân viên, xin tính tiền). Khách bấm lại khi yêu cầu cũ chưa có người nhận thì không tạo yêu cầu mới, máy phục vụ cũng không kêu lại. Chỉ xin tính tiền được khi bàn có đơn đang mở. Mỗi yêu cầu chỉ một người nhận | Unique index `ux_service_request_open`, `GuestOrderService`, `ServiceRequestService` |
| BR-30 | Mỗi bàn gửi **tối đa 10 lần mỗi phút** qua trang QR, tính chung gửi món, gọi nhân viên và thanh toán, và có **tối đa 30 món chờ xác nhận**. Quá giới hạn thì từ chối kèm lời nhắn cho khách; bàn khác không bị ảnh hưởng. Giới hạn theo bàn, không theo IP, vì khách dùng chung Wi-Fi của quán | `GuestOrderService`, `RateLimiter` |

## 5.4 Thanh toán

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-12 | Tổng tiền = Σ (đơn giá × số lượng) của các món **đã xác nhận và không huỷ**. Giá đã gồm VAT. Bản 1 không có giảm giá | `Order.total()` |
| BR-13 | Chỉ thanh toán khi đơn **đang mở**, có ít nhất một món tính tiền, và **không còn món chờ xác nhận**. Trả **một lần đủ tổng** (không chia bill). Tiền mặt: tiền khách đưa ≥ tổng | `PaymentService` |
| BR-14 | Mỗi yêu cầu chuyển khoản có **mã thanh toán duy nhất** dạng `BNN` + 8 ký tự, và số tiền bằng tổng lúc tạo. Mỗi đơn có **tối đa một** yêu cầu đang chờ. Tạo lại thì dùng lại mã cũ nếu tổng không đổi, còn nếu đổi thì huỷ mã cũ. Đơn có thêm món thì mã đang chờ bị huỷ | `PaymentService`, unique index `ux_payment_pending_order` |
| BR-15 | Tự xác nhận khi: webhook có **đúng API key**, là **tiền vào**, trường `code` hoặc nội dung chứa **mã đang chờ**, và **số tiền đúng bằng** số yêu cầu | `SepayWebhookService` |
| BR-16 | Mỗi giao dịch SePay (`id`) chỉ xử lý **một lần**. Giao dịch sai tiền, không có mã hoặc mã đã huỷ được lưu **KHÔNG KHỚP** để thu ngân xử lý, và **không tự đóng đơn** | Unique `bank_transaction.provider_txn_id` |
| BR-17 | Chỉ thu ngân và quản lý được **xác nhận tay**. Hệ thống ghi người xác nhận và thời điểm | `PaymentService.confirmManually` |

## 5.5 Thực đơn, kho, báo cáo

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-18 | Món và bàn **đã có trong đơn** thì không xoá được; món chỉ có thể báo hết. Danh mục còn món thì không xoá được | `MenuService`, `TableService`; khoá ngoại chặn lần cuối |
| BR-19 | Tồn kho chỉ đổi qua **phiếu biến động** (nhập, xuất, kiểm kê), không sửa số tồn trực tiếp. **Xuất không vượt tồn**. Phiếu ghi người tạo | `InventoryService` |
| BR-20 | Nguyên liệu **sắp hết** khi tồn ≤ mức tối thiểu | `InventoryItem.isLowStock()` |
| BR-21 | Doanh thu tính theo **khoản thanh toán đã xác nhận**, theo **ngày xác nhận giờ Việt Nam**. Món bán chạy chỉ tính đơn đã thanh toán và bỏ món huỷ | `ReportService` |

## 5.6 Nhân sự

| Mã | Quy tắc | Kiểm tra ở |
|---|---|---|
| BR-22 | Hồ sơ chỉ lưu thông tin cần cho vận hành: số điện thoại, ngày vào làm, ngày nghỉ việc, hình thức và mức lương. **Không lưu** số CCCD, địa chỉ nhà. Mức lương là số nguyên VND không âm, **chỉ ADMIN** xem và sửa; dữ liệu đăng nhập không kèm lương | `@PreAuthorize` trên `EmployeeController`, `PayrollController`; `EmployeeDtos`; `CHECK` trên `employee` |
| BR-23 | Ca mẫu kết thúc sau giờ bắt đầu, **không qua nửa đêm**. Ca đã xếp cho ai thì không đổi giờ được nữa. Một người không bị xếp **hai ca trùng giờ** trong cùng ngày, không bị xếp vào ngày đã được duyệt nghỉ. Không xếp ca cho người đã nghỉ việc hoặc bị khoá | `ScheduleService`, `CHECK ck_work_shift_time`, unique `ux_shift_assignment` |
| BR-24 | Đơn nghỉ có lý do, từ ngày ≤ đến ngày, tối đa 31 ngày, không trùng đơn đang chờ hoặc đã duyệt. Chỉ đơn **chờ duyệt** mới được duyệt, từ chối, hoặc người gửi huỷ. Từ chối phải có lý do; không ai tự duyệt đơn của mình. Duyệt thì gỡ các ca đã xếp trong những ngày đó. Ngày đã có chấm công thì không duyệt nghỉ được | `LeaveService` |
| BR-25 | Mỗi người **tối đa một lượt chấm công đang mở**; mỗi ca đã xếp chỉ chấm một lần. Chỉ vào ca khi hôm nay có ca, từ 15 phút trước giờ bắt đầu đến giờ kết thúc. Đi muộn = giờ vào − giờ bắt đầu ca; về sớm = giờ kết thúc ca − giờ ra; không âm. Số phút làm = giờ ra − giờ vào. Giờ tính theo giờ Việt Nam | Unique index `ux_attendance_open`, `ux_attendance_assignment`; `AttendanceService`, `Attendance.recompute` |
| BR-26 | Lương theo giờ = số giờ làm × đơn giá giờ. Lương theo tháng = mức lương ÷ số ngày công chuẩn (mặc định 26) × (số ngày có chấm công + số ngày nghỉ có lương). Làm tròn xuống tới đồng. Thực nhận = lương theo công + thưởng − phạt, không âm. Phiếu lương **chốt hình thức và mức lương lúc tính**: sửa mức lương sau đó không đổi phiếu cũ | `PayCalculator`, `PayrollService`, `CHECK ck_payslip_net` |
| BR-27 | Chỉ chốt bảng lương khi tháng đã kết thúc và không còn ai chưa ra ca. Bảng lương **đã chốt** thì không tính lại, không sửa thưởng phạt, và **chấm công, nghỉ phép của tháng đó bị khoá**. Sửa chấm công phải có lý do; hệ thống ghi người sửa và thời điểm. Nhân viên chỉ xem **phiếu đã chốt của chính mình** | `PayrollLock`, `PayrollService`, `AttendanceService` |
