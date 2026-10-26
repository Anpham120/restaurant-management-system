# Phân công 5 thành viên

Phân công theo **vòng đời nghiệp vụ**, không máy móc theo package. Mỗi tính năng có **một** người chịu trách nhiệm chính (viết code + test của tính năng đó). Kiểm thử tích hợp liên phân hệ do nhóm trưởng điều phối. Hợp đồng API từng endpoint: [API_CONTRACT](API_CONTRACT.md); bảng/lớp thuộc ai: [MODULE_BOUNDARIES](MODULE_BOUNDARIES.md).

| Thành viên | Vai trò | Phạm vi nghiệp vụ (docs/03) | Package / lớp sở hữu |
| --- | --- | --- | --- |
| **Phạm Duy An** | Nhóm trưởng · Quản lý đơn hàng | Toàn bộ **vòng đời đơn hàng** từ khách vào bàn tới kết thúc phiên: bàn, phiên, đơn, gọi món/gọi thêm, bếp nhận/từ chối/nấu/xong, phục vụ, hủy món và hủy ngoại lệ, **chốt hóa đơn, thanh toán CASH, QR SePay, webhook**, WAIVED/UNCOLLECTED, đóng phiên, trả bàn (3.1–3.7) | `order/**`; trong `finance`: `InvoiceController/Service/Repository`, `PaymentController/Service/Repository`, `strategy/**`, `sepay/**`, DTO `InvoiceResponse`, `PaymentRequest/Response`, `ReasonRequest` |
| **Bùi Đào Đức Anh** | Quản lý tài chính | Khoản chi hoạt động, theo dõi thu chi, đối soát tiền mặt, thống kê doanh thu, báo cáo tài chính, tổng hợp bán hàng/thanh toán (5.1–5.5) | Trong `finance`: `ExpenseController/Service/Repository`, `CashReconciliationController/Service/Repository`, `FinanceReportController/Service/Repository` và DTO đi kèm (lớp mới, tên riêng) |
| **Đỗ Tuấn Anh** | Quản lý kho | Nguyên liệu, nhập theo lô, xuất cho bếp, bếp xác nhận nhận hàng, sơ chế/thành phẩm, hao hụt, tồn RAW và tồn bếp, hoàn trả, điều chỉnh, kiểm kê, hạn dùng và cảnh báo tồn (4.1–4.7) | `inventory/**` |
| **Lê Anh** | Quản lý thực đơn | Danh mục món, món, công thức và định lượng, giá bán, thực đơn ngày, hạn mức bán, trạng thái kinh doanh/tạm dừng món (2.1–2.4) | `menu/**` |
| **Nguyễn Quang Hiếu** | Quản lý nhân viên · Security | Hồ sơ nhân viên, tài khoản, Spring Security, JWT, RBAC, vai trò, ca làm, phân ca, chấm công; bảo trì Security Foundation và hỗ trợ module khác tích hợp xác thực (1.1–1.4) | `employee/**`, `security/**` |

`common/**` (phản hồi, lỗi, AOP, CORS), `pom.xml`, `application*.yml`, `compose.yaml`, `Dockerfile`, `docker/**`, `IntegrationTestBase`, `TestData`: **Phạm Duy An** duyệt mọi thay đổi (nhóm trưởng); riêng `security/**` do **Nguyễn Quang Hiếu** sửa, Phạm Duy An review.

## Ranh giới quan trọng

- **Thanh toán thuộc Phạm Duy An**, kể cả lớp nằm trong `finance`. Bùi Đào Đức Anh **không** sửa `PaymentService`, `PaymentRepository`, `strategy/**`, `sepay/**`, `InvoiceRepository`; báo cáo đọc dữ liệu thanh toán qua hàm `rpt_*` và bảng `payments`, `invoices`, `operating_expenses`, `daily_cash_reconciliations` (hợp đồng ở [API_CONTRACT §5](API_CONTRACT.md#5-quản-lý-tài-chính--bùi-đào-đức-anh)).
- Order gọi `rc_accept_item`, `rc_start_cooking`, `rc_cancel_item`… — các hàm này tự đặt giữ/tiêu hao nguyên liệu trong bảng kho. Đó là **một lời gọi** của Order; Đỗ Tuấn Anh không viết lại, Phạm Duy An không tự cập nhật bảng kho.
- Thực đơn ngày (`daily_menu_items.id`) là đầu vào của gọi món: Lê Anh cung cấp `GET /api/daily-menus/today` để màn hình gọi món dùng; Order không đọc thẳng `menu_items` để định giá (giá do `rc_submit_batch` chụp).
- Không ai lấy `p_actor` từ request; luôn `CurrentUser.employeeId()` (Nguyễn Quang Hiếu giữ cơ chế này).

## Trạng thái hiện tại

| Thành viên | Đã chạy (có integration test) | Tiếp theo |
| --- | --- | --- |
| Phạm Duy An | Toàn bộ API vòng đời đơn hàng + thanh toán + webhook SePay ([API_CONTRACT §1](API_CONTRACT.md#1-quản-lý-đơn-hàng--phạm-duy-an)) | Điều phối tích hợp với menu (danh sách món hôm nay cho Waiter), review PR, kiểm thử E2E với frontend |
| Nguyễn Quang Hiếu | `POST /api/auth/login`, `GET /api/auth/me`, Security Foundation | Đổi mật khẩu bắt buộc lần đầu; CRUD nhân viên/tài khoản/vai trò; ca, phân ca, chấm công |
| Lê Anh | — | Danh mục, món, công thức (version), thực đơn ngày + hạn mức + tạm dừng |
| Đỗ Tuấn Anh | — | Danh mục nguyên liệu/loại bếp; phiếu nhập/xuất/kiểm kê (`rc_create_*`), nhận bàn giao, sơ chế, hoàn trả, hủy hàng, báo cáo kho |
| Bùi Đào Đức Anh | — | Khoản chi, đối soát tiền mặt, báo cáo doanh thu (`rpt_revenue_*`, `rpt_unpaid_closures`, `rpt_cash_reconciliation`) |

## Trách nhiệm kiểm thử

| Loại | Người viết | Người chạy/duyệt |
| --- | --- | --- |
| Unit + integration test của endpoint | Người sở hữu tính năng (bắt buộc: 1 ca thành công, 1 ca sai vai trò, 1 ca lỗi nghiệp vụ từ DB) | Phạm Duy An review PR |
| Kiểm thử tích hợp liên phân hệ (gọi món dùng thực đơn của Lê Anh, bếp tiêu hao kho của Đỗ Tuấn Anh, báo cáo của Bùi Đào Đức Anh đọc thanh toán) | Phạm Duy An điều phối, mỗi chủ module cung cấp dữ liệu test | Cả nhóm trước mỗi lần tích hợp |
| Quyền (RBAC, `@PreAuthorize` khớp ma trận docs/07 §10) | Người sở hữu endpoint | Nguyễn Quang Hiếu kiểm chéo |

## Bắt đầu

1. Đọc [ARCHITECTURE](ARCHITECTURE.md), [DEVELOPMENT_GUIDE](DEVELOPMENT_GUIDE.md), [API_CONVENTIONS](API_CONVENTIONS.md), phần của mình trong [API_CONTRACT](API_CONTRACT.md) và thẻ chức năng ở docs/03.
2. `./mvnw test` phải xanh; `docker compose up -d db`; tạo Manager bằng `docker/bootstrap-manager.sql`.
3. Làm theo mẫu `order/` (Controller → Service → Repository, test kế thừa `order/OrderApiTestBase` hoặc `IntegrationTestBase`).
4. Mỗi chức năng một nhánh Git, PR nhỏ; không sửa lớp của người khác trong cùng PR.
