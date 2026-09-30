# Thiết kế — Phần 2: Mô hình miền (Domain Class Model)

> Phương pháp: skill `domain-class-modeler`, `multiplicity-checker`, `boundary-control-entity-mapper`, `domain-vs-design-model-differentiator` (45ck/uml-analysis-modelling-skills).
> Đây là **mô hình phân tích**: chỉ mô tả khái niệm nghiệp vụ, thuộc tính chính và quan hệ, **chưa** đưa chi tiết cài đặt. Tên lớp dùng tiếng Anh để khớp với mã nguồn sau này; bảng từ vựng ở mục 6.
> `Money` là **số nguyên VND** (ADR-06). `UUID` sinh tại thiết bị hoặc edge (ADR-04).

## 1. Tổ chức, truy cập, thực đơn

```mermaid
classDiagram
    direction LR
    class Outlet {
        +String code
        +String name
        +String address
    }
    class Area {
        +String name
        +AreaType type
    }
    class DiningTable {
        +String code
        +String displayName
        +String zoneLabel
        +int seats
    }
    class TableGroupTemplate {
        +String name
    }
    class Station {
        +String name
    }
    class Device {
        +DeviceType type
        +DeviceStatus status
    }
    class Staff {
        +String fullName
        +String pinHash
        +StaffStatus status
    }
    class RoleAssignment {
        +Role role
    }
    Outlet "1" *-- "1..*" Area : gồm
    Area "1" *-- "1..*" DiningTable : gồm
    Outlet "1" *-- "0..*" TableGroupTemplate : nhóm bàn định sẵn
    TableGroupTemplate "0..*" --> "2..*" DiningTable : ghép
    Outlet "1" *-- "1..*" Station : khu chế biến
    Outlet "1" *-- "0..*" Device : sở hữu
    Station "0..*" --> "0..2" Device : máy in chính và dự phòng
    Staff "1" *-- "1..*" RoleAssignment : được gán
    RoleAssignment "0..*" --> "0..1" Outlet : phạm vi quán
```

```mermaid
classDiagram
    direction LR
    class MenuItem {
        +String code
        +String name
        +ItemStatus status
    }
    class ItemVariant {
        +String name
    }
    class ModifierOption {
        +String name
        +Money priceDelta
    }
    class SetComponent {
        +decimal qty
        +bool swappable
    }
    class PriceList {
        +Channel channel
        +Date effectiveFrom
        +Date effectiveTo
        +PriceListKind kind
    }
    class PriceEntry {
        +Money price
    }
    class TaxCategory {
        +String name
    }
    class TaxRate {
        +decimal rate
        +Date effectiveFrom
    }
    class ChannelMapping {
        +Channel channel
        +String externalCode
    }
    class OutletItemState {
        +bool available
        +bool soldOut
    }
    MenuItem "1" *-- "1..*" ItemVariant : có size
    MenuItem "0..*" --> "0..*" ModifierOption : cho phép
    MenuItem "1" *-- "0..*" SetComponent : set gồm
    SetComponent "0..*" --> "1" ItemVariant : thành phần
    MenuItem "0..*" --> "0..1" TaxCategory : loại thuế
    TaxCategory "1" *-- "1..*" TaxRate : thuế suất theo ngày
    MenuItem "0..*" --> "1" Station : định tuyến
    PriceList "1" *-- "1..*" PriceEntry : gồm
    PriceEntry "0..*" --> "1" ItemVariant : giá của
    PriceList "0..*" --> "1..*" Outlet : áp dụng cho
    MenuItem "1" *-- "0..*" ChannelMapping : mã trên app
    MenuItem "1" *-- "0..*" OutletItemState : trạng thái theo quán
```

- `MenuItem → TaxCategory` có bội số `0..1` vì món **mới đề xuất** chưa có loại thuế. Ràng buộc INV-10 chặn bán món chưa có loại thuế.
- `PriceList.kind` gồm: chuẩn, lễ hoặc Tết, set tiệc. Bảng giá có ngày hiệu lực và **kênh** (FR-MNU-07, 08).

## 2. Phục vụ và bếp

```mermaid
classDiagram
    direction LR
    class TableSession {
        +UUID id
        +int guests
        +DateTime openedAt
        +OpenMethod openedVia
        +SessionStatus status
    }
    class TableAssignment {
        +DateTime fromAt
        +DateTime toAt
    }
    class OrderRound {
        +UUID id
        +DateTime sentAt
        +bool isAddition
        +bool recoveryEntry
        +OrderSource source
    }
    class OrderItem {
        +UUID id
        +decimal qty
        +String note
        +ItemState state
        +bool held
        +int course
    }
    class AllergyNote {
        +List~String~ allergens
        +String verbatim
        +DateTime chefReviewedAt
    }
    class ChangeRequest {
        +ChangeType type
        +String reasonCode
        +DateTime kitchenAckAt
    }
    class KitchenTicket {
        +UUID id
        +TicketType type
        +DateTime printedAt
    }
    class ItemIncident {
        +IncidentType type
        +String cause
    }
    TableSession "1" *-- "1..*" TableAssignment : chiếm bàn
    TableAssignment "0..*" --> "1" DiningTable
    TableSession "0..1" --> "0..1" Booking : từ booking
    TableSession "1" *-- "0..*" OrderRound : các lượt gửi
    OrderRound "1" *-- "1..*" OrderItem : gồm
    OrderItem "0..*" --> "1" ItemVariant : món
    OrderItem "0..*" --> "0..*" ModifierOption : tuỳ chọn
    OrderItem "1" *-- "0..1" AllergyNote : dị ứng
    OrderItem "1" *-- "0..*" ChangeRequest : huỷ hoặc đổi
    ChangeRequest "0..1" --> "0..1" ApprovalRequest : cần duyệt
    OrderRound "1" --> "0..*" KitchenTicket : in theo khu
    KitchenTicket "0..*" --> "1" Station
    OrderItem "0..*" --> "0..*" KitchenTicket : xuất hiện trên
    ItemIncident "0..*" --> "0..1" OrderItem : liên quan
```

- **Lượt phục vụ** (`TableSession`) tách khỏi **bàn**. Một lượt chiếm **nhiều bàn** theo thời gian (`TableAssignment`), nên chuyển từ N bàn sang M bàn **không phá bill** (FR-TBL-05, I31).
- `OrderRound.recoveryEntry = true` là lượt nhập bù sau sự cố, **mặc định không tạo phiếu bếp**. Vì vậy `OrderRound → KitchenTicket` có bội số `0..*` (BR-38).
- `KitchenTicket.type` gồm: MỚI, THÊM, THAY THẾ, HUỶ, GỌI RA, IN LẠI.

## 3. Bill, thanh toán, ca, phê duyệt

```mermaid
classDiagram
    direction LR
    class Bill {
        +UUID id
        +String code
        +BillStatus status
        +Money subtotal
        +Money total
    }
    class BillShare {
        +Money amount
        +decimal qtyShare
    }
    class Adjustment {
        +AdjustmentType type
        +Money amount
        +String reasonCode
        +String note
    }
    class Payment {
        +UUID id
        +PaymentMethod method
        +Money amount
        +PaymentStatus status
        +String reference
    }
    class InvoiceRecipient {
        +String companyName
        +String taxCode
        +String address
        +String email
    }
    class InvoiceRecord {
        +InvoiceStatus status
        +String invoiceNo
    }
    class Shift {
        +UUID id
        +Money openingFloat
        +Money expectedCash
        +Money countedCash
        +ShiftStatus status
    }
    class CashMovement {
        +CashMoveType type
        +Money amount
        +String reason
        +String receiptPhotoUrl
    }
    class ApprovalRequest {
        +ApprovalType type
        +Money amount
        +ApprovalStatus status
        +DateTime expiresAt
    }
    class AuditEntry {
        +String action
        +String beforeValue
        +String afterValue
        +String prevHash
    }
    class BankTransaction {
        +Money amount
        +String description
        +String bankRef
        +MatchStatus matchStatus
    }
    Bill "0..*" --> "1..*" TableSession : của lượt phục vụ
    Bill "1" *-- "1..*" BillShare : phân bổ món
    BillShare "0..*" --> "1" OrderItem
    Bill "1" *-- "0..*" Adjustment : giảm giá, làm tròn
    Bill "1" *-- "0..*" Payment : trả bằng
    Bill "0..*" --> "0..1" InvoiceRecipient : xuất HĐ cho
    Bill "1" --> "0..1" InvoiceRecord : dữ liệu HĐĐT
    Bill "0..*" --> "0..*" Deposit : cấn trừ cọc
    Payment "0..1" --> "0..1" BankTransaction : xác nhận bởi
    Payment "0..*" --> "1" Shift : thu trong ca
    Shift "1" *-- "0..*" CashMovement : phiếu chi, hoàn tiền
    Adjustment "0..1" --> "0..1" ApprovalRequest : duyệt
    CashMovement "0..1" --> "0..1" ApprovalRequest : duyệt
    ApprovalRequest "0..*" --> "1" Staff : người yêu cầu
```

- **Tách bill** = nhiều `Bill` cùng tham chiếu một lượt phục vụ. `BillShare` phân bổ **từng món** (hoặc một phần món) cho từng bill. **Gộp bill** = một `Bill` tham chiếu nhiều lượt phục vụ.
- `Adjustment.type` gồm: giảm giá, tặng món, xoá món tính nhầm, **làm tròn tiền mặt**, cấn trừ cọc.
- `Payment.status` gồm: DỰ ĐỊNH (chưa xác nhận), ĐÃ XÁC NHẬN, CẦN DUYỆT, TỪ CHỐI, ĐÃ HOÀN (xem [máy trạng thái](04-tuong-tac-trang-thai.md)).
- `InvoiceRecord` là **dữ liệu HĐĐT**, tách khỏi `Bill` (ADR-08). Vòng đời gồm: chờ xuất → đã xuất file hoặc gửi API → đã phát hành (có số HĐ) → điều chỉnh do kế toán thực hiện.

## 4. Đặt bàn, tiệc, khách hàng

```mermaid
classDiagram
    direction LR
    class Booking {
        +String code
        +String guestName
        +String phone
        +DateTime startAt
        +int partySize
        +BookingChannel channel
        +BookingStatus status
    }
    class EventDetail {
        +Money minimumSpend
        +String notes
    }
    class HeadcountChange {
        +int partySize
        +DateTime changedAt
    }
    class PreOrderLine {
        +decimal qty
        +String serveAt
    }
    class EventPayer {
        +String label
    }
    class Deposit {
        +Money amount
        +DepositStatus status
        +String bankRef
        +bool toPersonalAccount
    }
    class TermsNotice {
        +String content
        +DateTime sentAt
        +String channel
    }
    class WaitlistEntry {
        +String name
        +String phone
        +int partySize
        +WaitStatus status
    }
    class Customer {
        +String phoneNormalized
        +String name
    }
    class ConsentRecord {
        +String channel
        +DateTime grantedAt
        +String method
        +DateTime revokedAt
    }
    Booking "1" *-- "0..1" EventDetail : là tiệc
    Booking "1" *-- "0..*" HeadcountChange : lịch sử số khách
    EventDetail "1" *-- "0..*" PreOrderLine : set đặt trước
    PreOrderLine "0..*" --> "1" ItemVariant
    EventDetail "1" *-- "0..*" EventPayer : các bên trả tiền
    EventPayer "0..*" --> "0..1" InvoiceRecipient
    Booking "1" *-- "0..*" Deposit : cọc
    Booking "1" *-- "0..*" TermsNotice : bằng chứng điều khoản
    Booking "0..*" --> "0..*" DiningTable : giữ bàn
    Booking "0..*" --> "0..1" Customer
    Customer "1" *-- "0..*" ConsentRecord : đồng ý tiếp thị
    WaitlistEntry "0..*" --> "1" Outlet
```

## 5. Kho, sơ chế, mua hàng

```mermaid
classDiagram
    direction LR
    class Warehouse {
        +String name
        +WarehouseType type
    }
    class StockItem {
        +String code
        +String name
        +StockItemType type
        +String baseUnit
        +bool priority
    }
    class UnitConversion {
        +String unit
        +decimal factorToBase
    }
    class Recipe {
        +bool confidential
    }
    class RecipeLine {
        +decimal qtyBase
    }
    class StockMovement {
        +MovementType type
        +decimal qtyBase
        +Money unitCost
        +DateTime at
    }
    class ProductionBatch {
        +decimal inputQty
        +decimal outputQty
        +decimal yieldRate
        +Date expiry
    }
    class TransferNote {
        +String code
        +TransferStatus status
    }
    class TransferLine {
        +decimal sentQty
        +decimal receivedQty
        +String discrepancyReason
    }
    class Supplier {
        +String name
        +bool issuesVatInvoice
    }
    class SupplierPrice {
        +Money agreedPrice
        +Date effectiveFrom
    }
    class GoodsReceipt {
        +String deliveryNoteNo
        +ReceiptStatus status
    }
    class ReceiptLine {
        +decimal deliveredQty
        +decimal acceptedQty
        +Money deliveredPrice
        +decimal temperatureC
    }
    class StockCount {
        +Date countDate
        +CountStatus status
    }
    class CountLine {
        +decimal countedQty
        +String countedUnit
    }
    Outlet "1" *-- "1" Warehouse : kho quán
    StockItem "1" *-- "0..*" UnitConversion : quy đổi
    ItemVariant "1" --> "0..1" Recipe : định lượng
    Recipe "1" *-- "1..*" RecipeLine
    RecipeLine "0..*" --> "1" StockItem
    Warehouse "1" *-- "0..*" StockMovement : sổ kho
    StockMovement "0..*" --> "1" StockItem
    ProductionBatch "1" --> "2..*" StockMovement : xuất nguyên liệu, nhập thành phẩm
    TransferNote "0..*" --> "1" Warehouse : từ kho
    TransferNote "0..*" --> "1" Warehouse : đến kho
    TransferNote "1" *-- "1..*" TransferLine
    TransferLine "0..*" --> "1" StockItem
    Supplier "1" *-- "0..*" SupplierPrice
    SupplierPrice "0..*" --> "1" StockItem
    GoodsReceipt "0..*" --> "1" Supplier
    GoodsReceipt "1" *-- "1..*" ReceiptLine
    ReceiptLine "0..*" --> "1" StockItem
    StockCount "0..*" --> "1" Warehouse
    StockCount "1" *-- "1..*" CountLine
    CountLine "0..*" --> "1" StockItem
```

- **Bếp sơ chế là một `Warehouse` riêng** (loại: sơ chế), đặt tại Đống Đa, **không** gộp vào kho quán Đống Đa (FR-INV-02).
- **Sổ kho (`StockMovement`) chỉ thêm, không sửa.** Mọi con số tồn kho đều tính từ sổ kho. `MovementType` gồm: nhập mua, xuất chuyển, nhận chuyển, bán (tính theo định lượng), hao hỏng, bữa nhân viên, khuyến mãi, bể vỡ, điều chỉnh, sản xuất (xuất nguyên liệu), sản xuất (nhập thành phẩm).
- **Két và vỏ ký cược** là `StockItem` loại "vật chứa" (FR-INV-09).

## 5b. Kênh khách QR và tự thanh toán (CR-01)

```mermaid
classDiagram
    direction LR
    class TableQrCode {
        +String tokenHash
        +QrStatus status
        +DateTime issuedAt
    }
    class SeatingCode {
        +String codeHash
        +int failedAttempts
        +DateTime lockedUntil
    }
    class GuestSession {
        +UUID id
        +GuestSessionStatus status
        +DateTime lastActivity
        +DateTime expiresAt
        +String phone
    }
    class GuestOrderRequest {
        +UUID id
        +GuestOrderStatus status
        +List~String~ holdFlags
        +DateTime submittedAt
        +DateTime decidedAt
        +String rejectReason
    }
    class GuestOrderLine {
        +decimal qty
        +String note
    }
    class ServiceRequest {
        +ServiceType type
        +boolean urgent
        +ServiceStatus status
        +DateTime ackAt
    }
    class PaymentIntent {
        +UUID id
        +Money amount
        +String reference
        +IntentStatus status
        +String provider
        +DateTime expiresAt
    }
    class ItemStatusHistory {
        +ItemState fromState
        +ItemState toState
        +DateTime at
    }
    DiningTable "1" *-- "0..*" TableQrCode : thẻ QR theo thời gian
    TableSession "1" *-- "0..1" SeatingCode : mã ngồi bàn
    TableSession "1" *-- "0..*" GuestSession : điện thoại tham gia
    GuestSession "1" *-- "0..*" GuestOrderRequest : gửi
    GuestOrderRequest "1" *-- "1..*" GuestOrderLine : gồm
    GuestOrderLine "0..*" --> "1" ItemVariant
    GuestOrderRequest "0..1" --> "0..1" OrderRound : khi được xác nhận
    GuestOrderRequest "0..*" --> "0..1" Staff : người xác nhận
    TableSession "1" *-- "0..*" ServiceRequest : gọi nhân viên
    Bill "1" *-- "0..*" PaymentIntent : lệnh thanh toán
    PaymentIntent "0..1" --> "0..*" BankTransaction : giao dịch khớp
    PaymentIntent "0..*" --> "0..1" GuestSession : tạo bởi
    OrderItem "1" *-- "0..*" ItemStatusHistory : lịch sử trạng thái
```

- **Mỗi thẻ QR là một bản ghi** (`TableQrCode`). Cấp lại thẻ thì bản cũ chuyển **REVOKED** (BR-53).
- **Đơn QR tách khỏi lượt gửi món.** `GuestOrderRequest` chỉ sinh ra `OrderRound` (nguồn: QR) khi **được xác nhận** (BR-42). Nhờ vậy đơn bị từ chối hoặc rút lại **không bao giờ** chạm tới bếp.
- **`ItemStatusHistory`** (học từ order_by_qr) cho phép vẽ **dòng thời gian trạng thái** cho khách (FR-GST-10) và đo thời gian.
- **Một lệnh thanh toán** có thể nhận **nhiều giao dịch** (trả thiếu rồi trả tiếp, hoặc trả trùng). Giao dịch **không khớp** thì không gắn lệnh nào (BR-49).

## 6. Ràng buộc luôn phải đúng (invariants)

| Mã | Ràng buộc | Lớp | Quy tắc |
|---|---|---|---|
| INV-01 | Tổng `BillShare.amount` của một `OrderItem` = thành tiền của món đó (chính xác từng đồng) | BillShare | BR-21 |
| INV-02 | Bill chỉ được **đóng** khi: tổng thanh toán ĐÃ XÁC NHẬN + cọc cấn trừ = tổng bill − giảm giá − làm tròn | Bill | BR-19, BR-20 |
| INV-03 | `Adjustment` loại giảm giá hoặc tặng món do quản lý tạo: ≤ min(10% × bill, 150.000đ) **và** tổng trong ca ≤ 600.000đ, **nếu không** thì phải có `ApprovalRequest` ở trạng thái ĐÃ DUYỆT hoặc DỰ PHÒNG | Adjustment | BR-01, 02, 03 |
| INV-04 | `OrderItem` ở trạng thái đã gửi trở đi: **không sửa trực tiếp**; mọi thay đổi đi qua `ChangeRequest` có duyệt và `kitchenAckAt` | OrderItem | BR-07 |
| INV-05 | `Payment` chuyển khoản ở trạng thái ĐÃ XÁC NHẬN phải có `reference` (mã ngân hàng) hoặc liên kết `BankTransaction` | Payment | BR-19 |
| INV-06 | `Deposit` **không bao giờ** tạo doanh thu; chỉ được cấn trừ, hoàn, giữ lại (theo BR-11) hoặc chuyển sang booking khác | Deposit | BR-10, 11 |
| INV-07 | `TransferLine.receivedQty ≤ sentQty`. Khi chênh lệch thì phiếu ở trạng thái TRANH CHẤP; **giá chuyển chỉ tính trên phần đã chấp nhận** | TransferNote | BR-34 |
| INV-08 | `GoodsReceipt` chỉ được xác nhận khi **mọi dòng có `acceptedQty`**, và dòng thịt lạnh hoặc hải sản có `temperatureC` | GoodsReceipt | BR-32, 33 |
| INV-09 | `StockMovement`, `AuditEntry`: **chỉ thêm**. `AuditEntry.prevHash` = băm của bản ghi trước | — | BR-27, NFR-16 |
| INV-10 | `MenuItem` chỉ được bán khi có `TaxCategory` **và** giá đã được chủ duyệt | MenuItem | BR-15 |
| INV-11 | `OrderRound.recoveryEntry = true` ⇒ không tạo `KitchenTicket`, trừ khi được xác nhận lần hai | OrderRound | BR-38 |
| INV-12 | Ca chỉ được chốt khi: không còn bill mở; danh sách đối chiếu khôi phục (nếu có) đã được ký; mọi phiếu chi đã có chứng từ | Shift | BR-24, 39 |
| INV-13 | `GuestSession` chỉ được tạo khi `TableSession` đang mở, QR của bàn đang bật, và mã ngồi bàn đúng. Phiên **hết hiệu lực** khi bill đóng hoặc bàn được mở cho nhóm mới | GuestSession | BR-41, 56, 57 |
| INV-14 | `GuestOrderRequest` chỉ sinh `OrderRound` khi trạng thái **ĐÃ XÁC NHẬN** bởi một `Staff` (QR-1). Đơn có `holdFlags` (BR-43) **không bao giờ** được tự nhận, kể cả ở QR-2 | GuestOrderRequest | BR-42, 43 |
| INV-15 | Nếu edge không xác nhận **đã nhận** đơn QR trong 5 giây thì đơn chuyển **TỪ CHỐI (mất kết nối)**. **Không** có trạng thái "chờ giao lại" | GuestOrderRequest | BR-55 |
| INV-16 | `PaymentIntent` chỉ được tạo khi bill **đã chốt**. `amount` = số còn phải trả lúc tạo; `reference` là duy nhất; mỗi bill có **tối đa 1** lệnh đang hoạt động | PaymentIntent | BR-48 |
| INV-17 | Khoản tự thanh toán chỉ **ĐÃ XÁC NHẬN** khi `BankTransaction` có chữ ký hợp lệ, `reference` khớp, và số tiền được đối chiếu. Tiền vượt số còn phải trả được ghi **thừa** hoặc **trùng** để hoàn | Payment, PaymentIntent | BR-49, 50 |
| INV-18 | *(CR-02)* Mỗi `DiningTable` có **tối đa một** `TableAssignment` đang hiệu lực, tức tối đa một nhóm đang ngồi. Chuyển nhóm chỉ tới bàn **trống hoặc đã thuộc chính nhóm đó** | TableAssignment | BR-64, 65 |
| INV-19 | *(CR-02)* `TableSession` chỉ được tạo khi **edge xác nhận**; `openedVia` ∈ {`SCAN`, `MANUAL`} luôn được ghi | TableSession | BR-63, 68 |
| INV-20 | *(CR-02)* Bản nháp trên máy cầm tay **không phải** `OrderRound`. `OrderRound` chỉ sinh ra khi nhân viên **chủ động bấm Gửi** và edge nhận. `source` ∈ {`STAFF`, `GUEST_QR`, `RECOVERY`} | OrderRound | BR-68, 69 |

## 7. Ánh xạ Boundary / Control / Entity

| Use case | Boundary (giao diện) | Control (điều phối) | Entity |
|---|---|---|---|
| UC-01 Mở bàn và gọi món | Màn hình sơ đồ bàn; màn hình gọi món (máy cầm tay); phiếu bếp in; màn hình bếp | OrderService (nhận lệnh gửi, kiểm tra hết món); RoutingService (định tuyến theo khu); PrintService; SyncOutbox | TableSession, TableAssignment, OrderRound, OrderItem, AllergyNote, KitchenTicket, OutletItemState |
| UC-03 Huỷ, đổi món đã gửi bếp | Màn hình yêu cầu huỷ; màn hình duyệt của quản lý; thông báo và phiếu HUỶ ở bếp | ChangeRequestService; ApprovalService; KitchenNotifier | ChangeRequest, ApprovalRequest, OrderItem, KitchenTicket, AuditEntry |
| UC-08 Thanh toán bill | Màn hình bill; màn hình thanh toán; màn hình QR; bill in | BillingService; SplitCalculator (phân bổ từng đồng); CashRoundingPolicy; PaymentConfirmationService; InvoiceQueueService | Bill, BillShare, Adjustment, Payment, InvoiceRecipient, InvoiceRecord, Shift |
| UC-09 Giảm giá | Màn hình giảm giá; thông báo đẩy tới chủ | DiscountPolicy (hạn mức bill và ca); ApprovalService (đếm ngược 3 phút, dự phòng); AlertService | Adjustment, ApprovalRequest, AuditEntry |
| UC-21 Chuyển kho | Phiếu chuyển (web hoặc tablet); màn hình xác nhận nhận | TransferService; TransferCostingPolicy (theo tỷ lệ thành phẩm) | TransferNote, TransferLine, StockMovement, ProductionBatch |
| UC-34 Khách gửi món thêm (CR-01) | App Khách: G-02 thực đơn, G-03 món đã chọn | GuestOrderService (cloud); EdgeRelay (kênh trực tiếp); GuestOrderIntake (edge); RateLimiter | GuestSession, GuestOrderRequest, GuestOrderLine |
| UC-35 Xác nhận đơn QR (CR-01) | PV-09 hàng chờ xác nhận | GuestOrderConfirmationService; HoldFlagPolicy; DuplicateDetector | GuestOrderRequest, OrderRound, KitchenTicket |
| UC-38 Tự thanh toán (CR-01) | G-06 bill, G-07 thanh toán, TN-07 theo dõi | PaymentIntentService; `PaymentProvider` (adapter); WebhookVerifier; PaymentMatcher | PaymentIntent, BankTransaction, Payment, Bill |
| UC-41 Quét QR bàn, chế độ nhân viên (CR-02) | PV-10 màn hình quét và kết quả | TableScanService (edge: giải mã token, kiểm tra trạng thái); TableOpeningService; GroupMoveService; SeatingCodeGenerator | DiningTable, TableQrCode, TableSession, TableAssignment, SeatingCode, AuditEntry |

## 8. Từ vựng lớp ↔ thuật ngữ nghiệp vụ

| Lớp | Thuật ngữ nghiệp vụ |
|---|---|
| Outlet / Area / DiningTable | Quán / Khu / Bàn |
| TableGroupTemplate | Nhóm bàn định sẵn (ví dụ khu quây 4 bàn ở Cầu Giấy) |
| Station | Khu chế biến |
| TableSession / TableAssignment | Lượt phục vụ / Bàn đang chiếm |
| OrderRound / OrderItem | Lượt gửi món / Dòng món |
| KitchenTicket | Phiếu bếp |
| ChangeRequest | Yêu cầu huỷ hoặc đổi món |
| Bill / BillShare | Bill (hoá đơn tạm tính) / Phần phân bổ món trên bill |
| Adjustment | Điều chỉnh (giảm giá, tặng món, làm tròn, cấn trừ cọc) |
| Payment | Khoản thanh toán |
| InvoiceRecord / InvoiceRecipient | Dữ liệu HĐĐT / Thông tin người mua trên HĐ |
| Shift / CashMovement | Ca / Phiếu chi, hoàn tiền mặt |
| ApprovalRequest / AuditEntry | Yêu cầu duyệt / Nhật ký |
| Booking / EventDetail / Deposit / TermsNotice | Đặt bàn / Chi tiết tiệc / Tiền cọc / Bằng chứng điều khoản |
| StockItem / Warehouse / StockMovement | Hàng kho / Kho / Sổ kho |
| Recipe / RecipeLine | Định lượng |
| ProductionBatch / TransferNote | Lô sản xuất sơ chế / Phiếu chuyển kho |
| GoodsReceipt / StockCount | Phiếu nhận hàng / Phiếu kiểm kê |
| TableQrCode / SeatingCode | Thẻ QR bàn / Mã ngồi bàn (CR-01) |
| GuestSession / GuestOrderRequest | Phiên khách / Đơn QR chờ xác nhận (CR-01) |
| ServiceRequest | Yêu cầu gọi nhân viên, tính tiền (CR-01) |
| PaymentIntent | Lệnh thanh toán (CR-01) |
| ItemStatusHistory | Lịch sử trạng thái món |
