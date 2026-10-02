# 1. Tầm nhìn dự án

| Mục | Nội dung |
|---|---|
| Dự án | Hệ thống quản lý nhà hàng **Khói Bếp** (KB-RMS), **bản core** cho đồ án môn học |
| Địa điểm | Một nhà hàng ở Đống Đa, Hà Nội, khoảng 24 bàn |
| Mô hình | Quán **lẩu nướng và cơm nhà** kiểu Hà Nội, phục vụ tại bàn: khách gọi món qua phục vụ hoặc tự quét QR, ăn xong mới trả tiền. Có bán mang về |
| Công nghệ | Backend **Java 21 + Spring Boot**, frontend **React + TypeScript**, CSDL **PostgreSQL**, **Docker**, CI/CD **GitHub Actions** |
| Repo tham khảo chính | [nazrul-ancala/Restaurant-Management-System](https://github.com/nazrul-ancala/Restaurant-Management-System): khung tài liệu, vai trò, chia module |

## 1.1 Vấn đề hiện tại

- Phục vụ ghi order ra giấy rồi chạy xuống bếp, nên dễ sai món hoặc sót món.
- Bếp không biết thứ tự món. Phục vụ không biết món nào đã xong.
- Giờ cao điểm khách phải chờ lâu mới gọi được món, chờ lâu mới trả được tiền.
- Khách chuyển khoản thì thu ngân phải mở app ngân hàng dò từng giao dịch.
- Kho ghi sổ tay, hết nguyên liệu mới biết.
- Chủ quán không có số liệu doanh thu theo ngày và món bán chạy.

## 1.2 Mục tiêu

| Mã | Mục tiêu | Đo bằng |
|---|---|---|
| G1 | Phục vụ tạo đơn và gửi bếp nhanh | ≤ 30 giây cho đơn 5 món |
| G2 | Bếp, phục vụ và khách thấy trạng thái món ngay | ≤ 2 giây sau khi đổi trạng thái |
| G3 | Khách tự gọi món bằng QR trên bàn, không cần cài app | 100% đơn QR được nhân viên xác nhận trước khi vào bếp |
| G4 | Chuyển khoản được tự xác nhận | ≤ 10 giây sau khi ngân hàng báo có, không dò tay |
| G5 | Biết trước nguyên liệu sắp hết | Cảnh báo khi tồn ≤ mức tối thiểu |
| G6 | Có số liệu kinh doanh | Doanh thu theo ngày, theo phương thức, top món |

## 1.3 Người dùng

| Vai trò | Mã | Việc chính |
|---|---|---|
| Quản trị | `ADMIN` | Tài khoản và hồ sơ nhân viên, bảng lương, cài đặt nhà hàng. Có mọi quyền |
| Quản lý | `MANAGER` | Thực đơn, loại thuế, giá app; bàn và QR; kho, nhà cung cấp, định lượng; báo cáo, nhật ký; duyệt giảm giá; ca két; xếp ca, chấm công, nghỉ phép; khách hàng; hoá đơn điện tử; huỷ món đang làm |
| Phục vụ | `WAITER` | Mở đơn, gọi món, xác nhận đơn QR, ra món, chuyển và ghép bàn; đặt bàn; đơn app và giao shipper |
| Bếp | `CHEF` | Màn hình bếp, đổi trạng thái món, báo hết món |
| Thu ngân | `CASHIER` | Tính tiền: tiền mặt, VietQR, xác nhận tay, tách bill; giảm giá, tặng món; mở và chốt ca; gắn khách; người mua trên hoá đơn điện tử |
| Khách | (không đăng nhập) | Quét QR, gọi món, xem trạng thái, gọi nhân viên, xin tính tiền, tự chuyển khoản |

## 1.4 Phạm vi

**Bản core**

1. Đăng nhập, phân quyền 5 vai trò, quản lý tài khoản nhân viên.
2. Thực đơn: danh mục, món, báo hết món.
3. Bàn và **mã QR riêng từng bàn**.
4. Gọi món bởi phục vụ. **Khách gọi món qua QR**, nhân viên xác nhận.
5. Màn hình bếp và **trạng thái từng món theo thời gian thực** cho nhân viên và khách.
6. Thanh toán tiền mặt, **chuyển khoản VietQR tự xác nhận** qua webhook SePay. Khách tự trả trên điện thoại.
7. Kho nguyên liệu: nhập, xuất, kiểm kê, lịch sử, cảnh báo sắp hết.
8. Báo cáo doanh thu, món bán chạy.
9. Cài đặt nhà hàng và tài khoản nhận tiền.

**Làm thêm sau bản core** (giai đoạn 1 → 4 trong [tài liệu 10](10-ke-hoach-phat-trien.md))

- Tại quán: chuyển và ghép bàn; in phiếu tạm tính và phiếu thanh toán khổ 80 mm; nhật ký thao tác; giảm giá, tặng món có quản lý duyệt; khách gọi nhân viên, xin tính tiền; âm báo, món chờ lâu; tách bill.
- Ca và két; định lượng món và trừ kho tự động; nhà cung cấp, phiếu nhập có giá, giá vốn; báo cáo lãi gộp, báo cáo ngoại lệ, xuất Excel.
- Nhân sự: hồ sơ, xếp ca, chấm công, nghỉ phép, bảng lương. Môn yêu cầu quản lý nhân viên đầy đủ nên làm thêm phần này.
- Bảo mật, vận hành: thu hồi token, đăng xuất mọi thiết bị; giới hạn tần suất; log JSON, cảnh báo webhook lỗi; sao lưu, HTTPS.
- Mở rộng: đặt bàn và cọc VietQR; khách hàng theo số điện thoại, đồng ý nhận tin; dữ liệu hoá đơn điện tử, xuất file cho MISA; đơn app giao hàng nhập tay.

**Ngoài phạm vi**

- Nhiều chi nhánh, chạy khi mất mạng: chỉ có thiết kế ([tài liệu 12](12-thiet-ke-mo-rong.md)).
- Tích điểm, voucher, gửi tin tiếp thị thật.
- Kết nối API với nhà cung cấp hoá đơn điện tử, với app giao hàng (nhận đơn tự động, đối soát).
- Hoàn tiền, đổi phương thức thanh toán sau khi chốt bill.
- In phiếu bếp bằng máy in nhiệt (dùng màn hình bếp).

## 1.5 Tiêu chí nghiệm thu

1. Demo trọn luồng: khách quét QR gọi món → phục vụ xác nhận → bếp làm → điện thoại khách hiện "Xong" → khách chuyển khoản → hệ thống tự xác nhận → bàn trống.
2. Mỗi vai trò chỉ làm được việc của mình (API trả 403 nếu sai quyền).
3. Mỗi lần push, CI tự chạy test. Khi merge vào `main`, pipeline build image rồi triển khai.

## 1.6 Tham khảo repo nazrul-ancala

| Lấy từ repo | Thay đổi so với repo | Không lấy |
|---|---|---|
| Bộ tài liệu 9 phần (vision → kiến trúc) | Trạng thái **theo từng món** thay vì cả đơn, vì khách gọi thêm nhiều lượt | Redux (dùng TanStack Query) |
| 5 vai trò, phân quyền theo API | Đơn QR phải **được nhân viên xác nhận**; repo cho vào thẳng bếp | Hoàn tiền (refund) |
| Mỗi bàn một mã QR, trang gọi món không cần đăng nhập | Khách **gọi thêm** và **xem trạng thái** được; repo báo lỗi 409 khi bàn đã có đơn | Tải ảnh món |
| Màn hình bếp realtime | Realtime dùng **WebSocket STOMP** của Spring thay cho Socket.IO | Nhật ký thao tác của repo: bản core tự làm nhật ký chỉ thêm, không sửa, xoá được (P1-03) |
| Kho: nguyên liệu, mức đặt lại, lịch sử biến động | Thanh toán **VietQR + webhook SePay** tự xác nhận | |
| Báo cáo doanh thu | Backend **Spring Boot + JPA + Flyway** thay cho Express + Prisma | |

> Repo nazrul-ancala **không có giấy phép** (license), nên chỉ tham khảo cách tổ chức, không chép mã.
>
> Bản phân tích mở rộng (chuỗi quán, máy chủ tại quán, chạy offline) nằm ở [`../docs/`](../docs/README.md), chỉ dùng để tham khảo.
