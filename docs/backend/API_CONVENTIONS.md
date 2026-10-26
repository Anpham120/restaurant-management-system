# Quy ước API

## URL

- Tiền tố `/api`, danh từ số nhiều, kebab-case: `/api/tables`, `/api/daily-menus`, `/api/stock-receipts`.
- Tài nguyên con: `/api/tables/{tableId}/sessions`, `/api/invoices/{invoiceId}/payments`.
- Hành động không phải CRUD (chuyển trạng thái do hàm `rc_*` làm) dùng `POST` + động từ: `/api/order-items/{id}/accept`, `/api/stock-issues/{id}/issue`, `/api/invoices/{id}/waive`.
- Id là số (`BIGINT`) trong path, kiểm `@Positive`.

| Method | Dùng khi | Mã thành công |
| --- | --- | --- |
| `GET` | đọc | 200 |
| `POST` | tạo mới / hành động | 201 khi tạo bản ghi, 200 khi chỉ là hành động |
| `PUT` / `PATCH` | sửa danh mục (bảng `rc_app` được ghi trực tiếp) | 200 |
| `DELETE` | xóa nháp / danh mục chưa dùng | 200 |

## Phản hồi

Mọi phản hồi (kể cả lỗi 401/403/404) có cùng dạng:

```json
{ "success": true,  "data": { "sessionId": 12, "tableId": 3 }, "error": null }
{ "success": false, "data": null,
  "error": { "code": "RC409", "message": "bàn B01 đang có khách", "details": [], "path": "/api/tables/3/sessions", "timestamp": "2026-10-26T09:00:00Z" } }
```

- JSON field **camelCase**; thời điểm ISO-8601 UTC (`Instant`); ngày kinh doanh `yyyy-MM-dd`; tiền là số thập phân (`BigDecimal`), không `double`.
- `details` chỉ có khi lỗi validate: `[{ "field": "items[0].quantity", "message": "must be greater than 0" }]`.

## DTO

- `record` trong `<module>/dto`, tên `…Request` / `…Response`.
- Request dùng Bean Validation; **không** chứa người thao tác, số tiền phải thu, trạng thái đích.
- Response không trả `password_hash`, `raw_payload` webhook hay cột nội bộ không cần cho màn hình.
- Không trả thẳng `Map<String, Object>` từ SQL.

## Phân trang (danh sách có thể lớn)

Query `?page=0&size=20` (size tối đa 100), sắp xếp cố định theo khóa (`ORDER BY id DESC`), SQL `LIMIT :size OFFSET :page * :size`. Trả:

```json
{ "success": true, "data": { "items": [ ... ], "page": 0, "size": 20, "totalItems": 135 } }
```

Danh sách nhỏ cố định (bàn, vai trò, danh mục) trả mảng trực tiếp.

## Idempotency

Thao tác client có thể gửi lặp và DB hỗ trợ khóa yêu cầu (`rc_submit_batch`, `rc_create_stock_*`, sơ chế, hoàn trả, hủy hàng, khoản chi): client sinh `requestKey` (UUID) **một lần cho mỗi lần bấm** và gửi lại đúng khóa đó khi retry, trong body (`requestKey`). Backend truyền nguyên khóa xuống DB. Hành vi khi gửi lặp khác nhau theo bảng ([07](../07-kiem-toan-ham-rc.md)): `rc_create_stock_*` trả phiếu cũ (khác nội dung → RC409); `rc_submit_batch` trả đợt cũ với `replayed = true`; sơ chế, hoàn trả, hủy hàng, khoản chi (ghi trực tiếp, `request_key` UNIQUE) nhận `DUPLICATE` — backend nên đọc lại bản ghi theo khóa và trả về như thành công.

## Mã lỗi

| HTTP | `code` | Nguồn | Ý nghĩa |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bean Validation | Body/tham số sai; xem `details` |
| 400 | `BAD_REQUEST` | Spring | JSON hỏng, sai kiểu tham số |
| 401 | `UNAUTHORIZED` | Security | Thiếu/sai/hết hạn token |
| 401 | `INVALID_CREDENTIALS` | AuthService | Sai đăng nhập, tài khoản khóa hoặc nhân viên nghỉ |
| 403 | `FORBIDDEN` | `@PreAuthorize` | Vai trò không được gọi endpoint |
| 403 | `RC403` | DB `rc_authorize` | `p_actor` không còn vai trò/tài khoản hoạt động |
| 404 | `NOT_FOUND` | Spring / `ApiException` | Không có đường dẫn / bản ghi |
| 409 | `RC409` | DB | Xung đột trạng thái: gọi lặp, sai bước, đối tượng đã đổi |
| 409 | `RC001` | DB | Thiếu nguyên liệu |
| 409 | `DUPLICATE` | DB 23505 | Trùng khóa duy nhất |
| 409 | `OVERLAP` | DB 23P01 | Trùng khoảng thời gian (EXCLUDE — ví dụ chồng ca) |
| 422 | `RC422` | DB | Dữ liệu không hợp lệ (thiếu lý do, phiếu rỗng, không tồn tại…) |
| 422 | `INVALID_DATA`, `REFERENCE_NOT_FOUND` | DB 23514/23502/23503 | Vi phạm CHECK/NOT NULL/FK |
| 503 | `RETRY` | DB 40001/40P01 | Đụng độ đồng thời — client thử lại |
| 500 | `RC500`, `DB_PERMISSION`, `INTERNAL_ERROR` | DB / hệ thống | Lỗi hệ thống, không hiển thị chi tiết; xem log |

Thông điệp của RC001/RC403/RC409/RC422 lấy nguyên văn từ hàm `rc_*` (tiếng Việt, hiển thị được cho người dùng).
