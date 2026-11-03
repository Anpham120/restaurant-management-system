# Frontend — Atlas Restaurant

React 19 + TypeScript + Vite. Gọi REST API của backend (`/api/...`, hợp đồng: [API_CONTRACT](../docs/backend/API_CONTRACT.md)).

## Chạy

```bash
npm install
npm run dev                                   # http://localhost:5173, /api → http://127.0.0.1:8081
API_TARGET=http://127.0.0.1:8082 npm run dev  # trỏ sang backend khác
npm run build                                 # kiểm kiểu + build ra dist/
```

Dev server chuyển tiếp `/api` sang backend nên không cần CORS. Bản build là file tĩnh; nginx phục vụ `dist/` ở `/` và chuyển `/api/` vào backend (cùng domain).

## Cấu trúc

```text
src/
├── api/        client.ts (fetch + ApiResponse + lỗi), types.ts (khớp DTO backend)
├── auth/       AuthContext: đăng nhập, /me, bắt buộc đổi mật khẩu, trang đầu theo vai trò
├── components/ Shell (8 mục điều hướng có icon, số đang chờ), Section (tab trong một mục), Icon, ui.tsx (bảng, form trong hộp thoại, useAction), toast
├── lib/        định dạng tiền, giờ (luôn giờ Việt Nam), nhãn trạng thái
└── pages/      Login, ChangePassword, Dashboard, Tables, Order, Kitchen, Invoice
                menu/ inventory/ staff/ finance/ settings/ me/  (màn quản lý theo phân hệ)
```

## Giao diện

Theo bản thiết kế đã duyệt (canvas "Atlas Restaurant — Thiết kế lại giao diện", 03/11/2026): nền sáng, xanh dương `#175CD3` là màu
hành động, font Be Vietnam Pro, không dùng nền tối ở đâu cả. Màu trạng thái: xanh lá = sẵn sàng/xong, cam = đang phục vụ/đang nấu,
tím = chờ thanh toán, đỏ = quá giờ/hủy/thiếu hàng. Token màu ở đầu `src/styles.css`.

Điều hướng 8 mục (mỗi vai trò chỉ thấy mục của mình): Tổng quan · Bàn & gọi món · Bếp · Thực đơn · Kho · Nhân sự · Tài chính · Cài đặt.
Trang con thành tab trong mục (`components/Section.tsx`); URL cũ giữ nguyên. Lịch làm của tôi, Đổi mật khẩu, Đăng xuất ở cuối thanh trái.
Quản lý đăng nhập vào Tổng quan; phục vụ, thu ngân vào Sơ đồ bàn; bếp vào Bếp.

## Màn hình theo vai trò

| Nhóm | Màn hình | Vai trò |
| --- | --- | --- |
| Tổng quan | Doanh thu hôm nay, bàn có khách, món bếp đang làm, việc cần xử lý (món trễ > 15 phút, món xong chờ bưng, bàn chờ thanh toán, nguyên liệu dưới mức, lô hỏng, món hết suất, thực đơn chưa công bố), bán chạy, món bán so với hạn mức | Quản lý |
| Bàn & gọi món | Sơ đồ bàn (lọc theo trạng thái, tiến độ món, món xong chờ bưng, khung chi tiết bàn: mở bàn, gọi thêm, thanh toán, đã bưng) → gọi món → hóa đơn (tiền mặt, QR SePay tự cập nhật) | Phục vụ, thu ngân, quản lý |
| Bếp | 3 cột Chờ nhận → Đang làm → Xong chờ bưng; màu thời gian chờ (< 10 phút, 10–15, > 15); tổng "Cần làm" theo món; từ chối, lấy lô khác khi lô hỏng | Bếp, quản lý |
| Thực đơn | Thực đơn ngày: tạo theo ngày, thêm món + hạn mức, công bố/đóng, tạm dừng/mở bán | Quản lý (bếp: tạm dừng/mở bán) |
| | Món và công thức: danh mục, món, giá, công thức theo phiên bản | Quản lý |
| Kho | Tồn kho (kho, bếp, đang bàn giao, lô hỏng; đánh dấu lô hỏng) | Quản lý, bếp (không xem kho nguyên liệu) |
| | Nhập kho, Kiểm kê, Lịch sử kho (sổ kho + hiệu suất sơ chế) | Quản lý |
| | Xuất cho bếp (bếp xác nhận nhận), Sơ chế, Hoàn trả và hủy hàng (bếp lập, quản lý xác nhận), Nguyên liệu | Quản lý, bếp |
| Nhân sự | Nhân viên (hồ sơ, tài khoản, vai trò, khóa, mật khẩu tạm, nghỉ việc; phòng ban), Ca và phân ca (lịch tuần), Chấm công (+ điều chỉnh có lịch sử) | Quản lý |
| Tài chính | Khoản chi (ghi, hủy), Đối soát tiền mặt (phiên bản, cảnh báo số liệu đổi sau đối soát), Báo cáo | Quản lý |
| Cài đặt | Bàn | Quản lý |
| Cá nhân | Lịch làm của tôi, Đổi mật khẩu | Mọi vai trò |

Route và menu đều lọc theo vai trò; backend vẫn kiểm quyền ở mọi API.

## Quy ước

- Không gửi người thao tác, tổng tiền hay trạng thái đích lên server: backend tự lấy từ token và tính trong DB.
- Thao tác có thể gửi lặp (gửi bếp) dùng `requestKey` sinh một lần cho mỗi lần bấm, giữ nguyên khi thử lại.
- Thông điệp lỗi `RC*` của backend là tiếng Việt, hiển thị nguyên văn.
- Dữ liệu vận hành tự làm mới định kỳ (bếp 4 giây, phiếu bàn 5 giây, sơ đồ bàn 10 giây, QR đang chờ 3 giây).
