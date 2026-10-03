# 4. Quy trình nghiệp vụ

## 4.1 Trước và sau khi có hệ thống

| Việc | Hiện tại | Khi có hệ thống |
|---|---|---|
| Gọi món | Ghi giấy, chạy xuống bếp | Phục vụ gọi trên điện thoại, hoặc khách tự gọi bằng QR |
| Báo bếp | Đọc to hoặc ghim phiếu | Món hiện ngay trên màn hình bếp |
| Biết món xong | Bếp gọi to | Sơ đồ bàn và điện thoại khách tự cập nhật |
| Nhận chuyển khoản | Thu ngân dò app ngân hàng | Webhook tự xác nhận, màn hình tự báo |
| Kho | Sổ tay | Phiếu nhập, xuất, kiểm kê và cảnh báo sắp hết |
| Doanh thu | Cộng tay cuối ngày | Báo cáo theo ngày, phương thức, món |
| Giảm giá, tặng món | Tuỳ người thu, không ghi lại | Có lý do; vượt hạn mức thì quản lý duyệt; ghi nhật ký |
| Tiền trong két | Đếm cuối ngày, không biết lệch vì đâu | Mở ca, phiếu chi, chốt ca so tiền đếm với tiền dự kiến |
| Đặt bàn, cọc | Ghi sổ, dò app ngân hàng xem cọc | Booking có mã; cọc chuyển khoản tự xác nhận, tự trừ vào bill |
| Đơn app giao hàng | Xem trên tablet của app, dễ sót hoặc nhập trùng | Nhập theo mã đơn app, vào bếp như đơn thường, doanh thu theo kênh |
| Hoá đơn điện tử | Kế toán gõ lại từ bill giấy | Dữ liệu hoá đơn lập sẵn khi thu đủ, xuất file để nhập vào MISA |
| Ca làm, lương | Xếp ca trên giấy, chấm công tay | Xếp ca, chấm công vào ra, nghỉ phép, bảng lương tính từ chấm công |

## 4.2 P1 — Phục vụ gọi món

```mermaid
flowchart TD
    A[Khách vào bàn] --> B[Phục vụ chọn bàn trên sơ đồ]
    B --> C{Bàn đã có đơn?}
    C -- Chưa --> D[Mở đơn, nhập số khách]
    C -- Có --> E[Mở đơn hiện có]
    D --> F[Chọn món, số lượng, ghi chú]
    E --> F
    F --> G{Món còn bán?}
    G -- Hết --> F
    G -- Còn --> H[Bấm Gửi bếp]
    H --> I[Món ở trạng thái Chờ làm, hiện ngay ở bếp]
```

## 4.3 P2 — Khách gọi món qua QR, nhân viên xác nhận

```mermaid
sequenceDiagram
    actor K as Khách
    participant HT as Hệ thống
    actor PV as Phục vụ
    actor B as Bếp
    K->>HT: Quét QR trên bàn
    HT-->>K: Tên bàn, thực đơn, món đã gọi
    K->>HT: Chọn món và gửi
    Note over HT: Bàn chưa có đơn thì tự mở đơn
    HT-->>K: Món hiện "Chờ xác nhận"
    HT-->>PV: Báo "Bàn B05 có món chờ xác nhận"
    alt Hợp lệ
        PV->>HT: Xác nhận
        HT-->>B: Món vào cột Chờ làm
        HT-->>K: Món chuyển "Chờ làm"
    else Không hợp lệ
        PV->>HT: Từ chối, ghi lý do
        HT-->>K: "Đã từ chối" kèm lý do
    end
```

## 4.4 P3 — Chế biến và ra món

```mermaid
flowchart LR
    A[Chờ làm] -- Bếp bấm Bắt đầu --> B[Đang làm]
    B -- Bếp bấm Xong --> C[Xong]
    C -- Phục vụ mang ra, bấm Đã ra --> D[Đã ra]
    C -. Báo ngay .-> P[Sơ đồ bàn của phục vụ]
    A -. Báo ngay .-> K[Điện thoại khách]
    B -. Báo ngay .-> K
    C -. Báo ngay .-> K
```

## 4.5 P4 — Thanh toán

```mermaid
flowchart TD
    A[Khách muốn thanh toán] --> B{Còn món chờ xác nhận, hoặc giảm giá chờ duyệt?}
    B -- Còn --> B1[Phục vụ xác nhận hoặc từ chối món; quản lý duyệt giảm giá] --> B
    B -- Không --> T[Bill = tiền món − giảm giá − cọc đã nhận]
    T --> P[Khoản thu lần này: cả số còn lại, hoặc một phần khi tách bill]
    P --> C{Cách trả}
    C -- Tiền mặt --> D[Thu ngân nhập tiền khách đưa; cần ca két đang mở]
    D --> E{Đủ tiền?}
    E -- Thiếu --> D
    E -- Đủ --> Y{Đã thu đủ bill?}
    Y -- Chưa --> P
    Y -- Đủ --> Z[Đơn đóng, bàn trống, lập dữ liệu hoá đơn điện tử]
    C -- Chuyển khoản tại quầy --> F[Thu ngân tạo mã VietQR]
    C -- Chuyển khoản trên điện thoại --> F2[Khách bấm Thanh toán trên trang QR]
    F --> G[Mã VietQR: đúng số tiền, nội dung là mã thanh toán]
    F2 --> G
    G --> H[Khách quét bằng app ngân hàng và chuyển]
    H --> I[SePay gửi webhook về hệ thống]
    I --> J{Đúng API key, tiền vào, có mã, đúng tiền?}
    J -- Đúng --> Y
    J -- Sai --> K[Lưu giao dịch KHÔNG KHỚP]
    K --> L[Thu ngân kiểm tra app ngân hàng]
    L --> M[Xác nhận tay, ghi tên người xác nhận] --> Y
```

## 4.6 P5 — Kho nguyên liệu

```mermaid
flowchart LR
    A[Phiếu nhập: nhà cung cấp, số lượng, giá] --> P[Tạo phiếu biến động]
    A --> V[Cập nhật giá vốn bình quân]
    B[Xuất dùng hoặc hỏng] --> P
    C[Kiểm kê: nhập số thực tế] --> P
    D[Món vào bếp] --> E[Trừ theo định lượng của món] --> P
    P --> Q{Hợp lệ? Xuất tay không vượt tồn}
    Q -- Không --> X[Báo lỗi, tồn không đổi]
    Q -- Có --> R[Cập nhật tồn]
    R --> S{Tồn ≤ mức tối thiểu?}
    S -- Có --> T[Hiện nhãn Sắp hết]
    S -- Không --> U[Bình thường]
```

## 4.7 P6 — Đặt bàn và cọc

```mermaid
flowchart TD
    A[Khách gọi đặt bàn] --> B[Phục vụ tạo booking: tên, số điện thoại, giờ, số khách, bàn, cọc]
    B --> C[Hệ thống cấp mã KB + 8 ký tự, gắn khách theo số điện thoại]
    C --> D[Gửi tin xác nhận cho khách]
    D --> E{Có cọc?}
    E -- Có --> F[Mã VietQR cọc, nội dung là mã booking]
    F --> G[Webhook SePay đúng mã, đúng tiền: đã nhận cọc]
    G --> H{Khách tới?}
    E -- Không --> H
    H -- Tới --> I[Nhận khách: mở đơn ở bàn trống, bill trừ cọc]
    H -- Huỷ hoặc không tới --> J[Booking đóng; cọc đã nhận vẫn giữ]
```

## 4.8 P7 — Ca két

```mermaid
flowchart LR
    A[Thu ngân mở ca, nhập quỹ đầu ca] --> B[Thu tiền mặt trong ca]
    B --> C[Phiếu chi khi cần; trên 300.000 đ thì quản lý lập]
    C --> D[Chốt ca: đếm tiền thực tế]
    D --> E{Khớp tiền dự kiến?}
    E -- Khớp --> F[Ca đóng]
    E -- Lệch --> G[Ghi lý do lệch] --> F
```

## 4.9 P8 — Đơn app giao hàng

```mermaid
flowchart TD
    A[Đơn mới trên tablet GrabFood hoặc ShopeeFood] --> B[Phục vụ chọn Đơn app, chọn kênh, nhập mã đơn]
    B --> C{Mã đã có trong kênh?}
    C -- Có --> X[Báo trùng, không tạo đơn]
    C -- Chưa --> D[Chọn món theo giá app, gửi bếp]
    D --> E[Bếp làm như đơn thường]
    E --> F{Mọi món xong?}
    F -- Chưa --> E
    F -- Xong --> G[Phục vụ bấm Giao shipper]
    G --> H[Đơn đóng: app thu tiền, doanh thu ghi theo kênh]
```

## 4.10 P9 — Hoá đơn điện tử

```mermaid
flowchart LR
    A[Đơn thu đủ] --> B[Hệ thống lập dữ liệu hoá đơn: dòng hàng, thuế theo từng thuế suất]
    B --> C[Thu ngân ghi người mua khi khách lấy hoá đơn công ty]
    C --> D[Quản lý xuất file Excel các hoá đơn chưa có số]
    D --> E[Kế toán nhập file vào MISA meInvoice, phát hành]
    E --> F[Quản lý ghi ký hiệu và số hoá đơn]
```

## 4.11 P10 — Ca làm và lương

```mermaid
flowchart LR
    A[Quản lý xếp ca tuần] --> B[Nhân viên vào ca, ra ca trên app]
    B --> C[Đi muộn, về sớm tự tính; quản lý sửa có lý do]
    D[Nhân viên gửi đơn nghỉ phép] --> E{Quản lý duyệt?}
    E -- Duyệt --> F[Gỡ các ca đã xếp trong ngày nghỉ]
    E -- Từ chối --> G[Giữ nguyên lịch]
    C --> H[Cuối tháng: quản trị tính bảng lương từ chấm công, thưởng phạt]
    H --> I[Chốt bảng lương, phiếu lương, xuất Excel]
```
