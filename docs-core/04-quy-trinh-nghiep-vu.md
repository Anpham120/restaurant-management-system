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
    A[Khách muốn thanh toán] --> B{Còn món chờ xác nhận?}
    B -- Còn --> B1[Phục vụ xác nhận hoặc từ chối trước] --> B
    B -- Không --> C{Cách trả}
    C -- Tiền mặt --> D[Thu ngân nhập tiền khách đưa]
    D --> E{Đủ tiền?}
    E -- Thiếu --> D
    E -- Đủ --> Z[Đơn đóng, bàn trống]
    C -- Chuyển khoản tại quầy --> F[Thu ngân tạo mã VietQR]
    C -- Chuyển khoản trên điện thoại --> F2[Khách bấm Thanh toán trên trang QR]
    F --> G[Mã VietQR: đúng số tiền, nội dung là mã thanh toán]
    F2 --> G
    G --> H[Khách quét bằng app ngân hàng và chuyển]
    H --> I[SePay gửi webhook về hệ thống]
    I --> J{Đúng API key, tiền vào, có mã, đúng tiền?}
    J -- Đúng --> Z
    J -- Sai --> K[Lưu giao dịch KHÔNG KHỚP]
    K --> L[Thu ngân kiểm tra app ngân hàng]
    L --> M[Xác nhận tay, ghi tên người xác nhận] --> Z
```

## 4.6 P5 — Kho nguyên liệu

```mermaid
flowchart LR
    A[Nhập hàng] --> P[Tạo phiếu biến động]
    B[Xuất dùng hoặc hỏng] --> P
    C[Kiểm kê: nhập số thực tế] --> P
    P --> Q{Hợp lệ? Xuất không vượt tồn}
    Q -- Không --> X[Báo lỗi, tồn không đổi]
    Q -- Có --> R[Cập nhật tồn]
    R --> S{Tồn ≤ mức tối thiểu?}
    S -- Có --> T[Hiện nhãn Sắp hết]
    S -- Không --> U[Bình thường]
```
