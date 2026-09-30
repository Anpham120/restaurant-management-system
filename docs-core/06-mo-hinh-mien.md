# 6. Mô hình miền

## 6.1 Sơ đồ lớp

```mermaid
classDiagram
    class Employee {
        Long id
        String fullName
        String username
        String passwordHash
        Role role
        boolean active
    }
    class Category {
        Long id
        String name
        int sortOrder
    }
    class MenuItem {
        Long id
        String name
        long price
        String description
        boolean available
    }
    class DiningTable {
        Long id
        String name
        String area
        int seats
        String qrToken
    }
    class Order {
        Long id
        OrderType type
        OrderStatus status
        Integer guestCount
        Instant openedAt
        Instant closedAt
        total() long
    }
    class OrderItem {
        Long id
        String itemName
        long unitPrice
        int quantity
        String note
        ItemStatus status
        ItemSource source
        String cancelReason
    }
    class Payment {
        Long id
        PaymentMethod method
        PaymentStatus status
        long amount
        String reference
        Long receivedAmount
        Confirmation confirmation
        Instant paidAt
    }
    class BankTransaction {
        Long id
        String providerTxnId
        long amount
        String content
        MatchStatus matchStatus
    }
    class InventoryItem {
        Long id
        String name
        String unit
        BigDecimal quantity
        BigDecimal minQuantity
        isLowStock() boolean
    }
    class StockMovement {
        Long id
        MovementType type
        BigDecimal quantityChange
        String note
    }
    class RestaurantSettings {
        String name
        String address
        String phone
        String bankCode
        String bankAccountNo
        String bankAccountName
    }
    Category "1" --> "0..*" MenuItem
    DiningTable "1" --> "0..*" Order : tối đa 1 đơn mở
    Order "1" *-- "1..*" OrderItem
    OrderItem "0..*" --> "1" MenuItem
    Order "1" --> "0..*" Payment
    Payment "0..1" <-- "0..*" BankTransaction : khớp với
    InventoryItem "1" *-- "0..*" StockMovement
    Employee "1" --> "0..*" Order : mở đơn
    Employee "0..1" --> "0..*" Payment : xác nhận
    Employee "1" --> "0..*" StockMovement : lập phiếu
```

Các kiểu liệt kê:

| Kiểu | Giá trị |
|---|---|
| `Role` | `ADMIN`, `MANAGER`, `WAITER`, `CHEF`, `CASHIER` |
| `OrderType` | `DINE_IN` (tại bàn), `TAKEAWAY` (mang về) |
| `OrderStatus` | `OPEN`, `PAID`, `CANCELLED` |
| `ItemStatus` | `PENDING` (chờ xác nhận), `WAITING` (chờ làm), `COOKING` (đang làm), `READY` (xong), `SERVED` (đã ra), `CANCELLED` |
| `ItemSource` | `STAFF` (phục vụ gọi), `GUEST` (khách gọi qua QR) |
| `PaymentMethod` | `CASH`, `BANK_TRANSFER` |
| `PaymentStatus` | `PENDING`, `PAID`, `CANCELLED` |
| `Confirmation` | `AUTO` (webhook), `MANUAL` (xác nhận tay) |
| `MatchStatus` | `MATCHED`, `UNMATCHED`, `IGNORED` (tiền ra) |
| `MovementType` | `IN` (nhập), `OUT` (xuất), `ADJUST` (kiểm kê) |
| `PayType` (nhân sự) | `HOURLY` (theo giờ), `MONTHLY` (theo tháng) |
| `LeaveType` (nhân sự) | `PAID` (có lương), `UNPAID` (không lương) |
| `LeaveStatus` (nhân sự) | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `PayrollStatus` (nhân sự) | `DRAFT` (nháp), `FINALIZED` (đã chốt) |

Ghi chú thiết kế:
- **Trạng thái đặt ở từng món**, không đặt ở cả đơn, vì một bàn gọi nhiều lượt và mỗi món xong vào lúc khác nhau. Repo tham khảo đặt trạng thái ở cả đơn.
- `OrderItem` lưu **tên và đơn giá lúc gọi** (BR-05), nên báo cáo và bill không đổi khi thực đơn đổi.
- Trạng thái bàn **không lưu** mà tính từ đơn đang mở (BR-04), nên không bao giờ lệch.

## 6.2 Trạng thái món

```mermaid
stateDiagram-v2
    [*] --> PENDING : Khách gửi qua QR
    [*] --> WAITING : Phục vụ gửi bếp
    PENDING --> WAITING : Nhân viên xác nhận
    PENDING --> CANCELLED : Nhân viên từ chối (có lý do)
    WAITING --> COOKING : Bếp bắt đầu
    COOKING --> READY : Bếp báo xong
    READY --> SERVED : Phục vụ ra món
    WAITING --> CANCELLED : Phục vụ huỷ
    COOKING --> CANCELLED : Quản lý huỷ (có lý do)
    READY --> CANCELLED : Quản lý huỷ (có lý do)
    SERVED --> [*]
    CANCELLED --> [*]
```

## 6.3 Trạng thái đơn và thanh toán

```mermaid
stateDiagram-v2
    state "Đơn" as O {
        [*] --> OPEN : Mở bàn hoặc khách gửi QR đầu tiên
        OPEN --> PAID : Có khoản thanh toán PAID
        OPEN --> CANCELLED : Mọi món đã huỷ
        PAID --> [*]
        CANCELLED --> [*]
    }
```

```mermaid
stateDiagram-v2
    state "Chuyển khoản" as P {
        [*] --> PENDING : Tạo mã VietQR
        PENDING --> PAID : Webhook khớp hoặc xác nhận tay
        PENDING --> CANCELLED : Tạo mã mới, đơn thêm món, hoặc trả tiền mặt
        PAID --> [*]
        CANCELLED --> [*]
    }
```

Tiền mặt được ghi thẳng là `PAID` khi thu ngân xác nhận.

## 6.4 Ma trận quyền

| Chức năng | ADMIN | MANAGER | WAITER | CHEF | CASHIER |
|---|:-:|:-:|:-:|:-:|:-:|
| Quản lý nhân viên | ✅ | | | | |
| Sửa cài đặt nhà hàng | ✅ | | | | |
| Sửa thực đơn, bàn, tạo lại QR | ✅ | ✅ | | | |
| Báo hết món | ✅ | ✅ | | ✅ | |
| Xem sơ đồ bàn, mở đơn, gọi món | ✅ | ✅ | ✅ | | |
| Xác nhận hoặc từ chối món QR | ✅ | ✅ | ✅ | | |
| Màn hình bếp: Đang làm, Xong | ✅ | ✅ | | ✅ | |
| Ra món | ✅ | ✅ | ✅ | | |
| Huỷ món Chờ làm | ✅ | ✅ | ✅ | | |
| Huỷ món Đang làm hoặc Xong | ✅ | ✅ | | | |
| Xem bill | ✅ | ✅ | ✅ | | ✅ |
| Thu tiền, tạo VietQR, xác nhận tay | ✅ | ✅ | | | ✅ |
| Kho | ✅ | ✅ | | | |
| Báo cáo | ✅ | ✅ | | | |
| Hồ sơ, mức lương, bảng lương | ✅ | | | | |
| Ca mẫu, xếp ca, duyệt nghỉ, sửa chấm công | ✅ | ✅ | | | |
| Xem lịch, chấm công, xin nghỉ, xem phiếu lương của mình | ✅ | ✅ | ✅ | ✅ | ✅ |
