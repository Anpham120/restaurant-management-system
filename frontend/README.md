# Frontend — Restaurant Core

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
├── components/ Shell (điều hướng theo nhóm), ui.tsx (bảng, form trong hộp thoại, tab, useAction), toast, chip
├── lib/        định dạng tiền, giờ (luôn giờ Việt Nam), nhãn trạng thái
└── pages/      Login, ChangePassword, Tables, Order, Kitchen, Invoice
                menu/ inventory/ staff/ finance/ settings/ me/  (màn quản lý theo phân hệ)
```

## Màn hình theo vai trò

| Nhóm | Màn hình | Vai trò |
| --- | --- | --- |
| Vận hành | Sơ đồ bàn → gọi món → hóa đơn (tiền mặt, QR SePay tự cập nhật) | Phục vụ, thu ngân, quản lý |
| | Bếp: Chờ nhận → Đã nhận → Đang nấu → Xong; từ chối, lấy lô khác khi lô hỏng | Bếp, quản lý |
| Thực đơn | Thực đơn ngày: tạo theo ngày, thêm món + hạn mức, công bố/đóng, tạm dừng/mở bán | Quản lý (bếp: tạm dừng/mở bán) |
| | Món và công thức: danh mục, món, giá, công thức theo phiên bản | Quản lý |
| Kho | Tồn kho (kho, bếp, đang bàn giao, lô hỏng; đánh dấu lô hỏng) | Quản lý, bếp (không xem kho nguyên liệu) |
| | Nhập kho, Kiểm kê, Lịch sử kho (sổ kho + hiệu suất sơ chế) | Quản lý |
| | Xuất cho bếp (bếp xác nhận nhận), Sơ chế, Hoàn trả và hủy hàng (bếp lập, quản lý xác nhận), Nguyên liệu | Quản lý, bếp |
| Nhân sự | Nhân viên (hồ sơ, tài khoản, vai trò, khóa, mật khẩu tạm, nghỉ việc; phòng ban), Ca và phân ca (lịch tuần), Chấm công (+ điều chỉnh có lịch sử) | Quản lý |
| Tài chính | Khoản chi (ghi, hủy), Đối soát tiền mặt (phiên bản, cảnh báo số liệu đổi sau đối soát), Báo cáo | Quản lý |
| Cá nhân | Quản lý bàn (quản lý), Lịch làm của tôi, Đổi mật khẩu | Mọi vai trò |

Route và menu đều lọc theo vai trò; backend vẫn kiểm quyền ở mọi API.

## Quy ước

- Không gửi người thao tác, tổng tiền hay trạng thái đích lên server: backend tự lấy từ token và tính trong DB.
- Thao tác có thể gửi lặp (gửi bếp) dùng `requestKey` sinh một lần cho mỗi lần bấm, giữ nguyên khi thử lại.
- Thông điệp lỗi `RC*` của backend là tiếng Việt, hiển thị nguyên văn.
- Dữ liệu vận hành tự làm mới định kỳ (bếp 4 giây, phiếu bàn 5 giây, sơ đồ bàn 10 giây, QR đang chờ 3 giây).
