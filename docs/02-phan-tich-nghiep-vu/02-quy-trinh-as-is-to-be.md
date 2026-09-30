# BRD — Phần 2: Quy trình nghiệp vụ hiện tại (As-is) và mục tiêu (To-be)

> Phương pháp: skill `process-modelling-and-improvement`, `as-is-process-investigator`, `to-be-process-designer`, `process-model-spec`.
> Các mã `FR-…` tham chiếu tới [SRS](../03-dac-ta-yeu-cau/01-yeu-cau-chuc-nang.md), mã `BR-…` tới [sổ quy tắc](03-quy-tac-nghiep-vu.md).
> Ô đỏ trong sơ đồ as-is là **điểm đau** đã được khách xác nhận.

## Danh sách quy trình

| Mã | Quy trình | Người thực hiện chính | Có sơ đồ |
|---|---|---|---|
| P1 | Phục vụ tại bàn: đón khách → gọi món → bếp → ra món → thanh toán | Phục vụ, thu ngân, bếp | As-is + To-be |
| P2 | Huỷ, đổi món, giảm giá, tặng món (có duyệt) | Quản lý, bếp, chủ | To-be |
| P3 | Thanh toán và xác nhận chuyển khoản | Thu ngân, quản lý | To-be |
| P4 | Mở ca, giao ca, chốt ca, đối chiếu két | Thu ngân, quản lý | To-be |
| P5 | Đặt bàn, tiền cọc, tiệc | Quản lý | To-be |
| P6 | Đơn mang về và app giao hàng | Phục vụ quầy, thu ngân | To-be |
| P7 | Sơ chế chung và chuyển kho | Sơ chế, quản lý | To-be |
| P8 | Mua hàng và nhận hàng | Điều phối mua hàng, người nhận | To-be |
| P9 | Kiểm kê và chênh lệch kho | Quản lý, bếp, mua hàng | To-be |
| P10 | Sự cố mất mạng và khôi phục | Mọi người trong quán | To-be |
| P11 | Khớp số hằng ngày | Kế toán | To-be |
| P12 | Khách gọi thêm món qua QR, nhân viên xác nhận (CR-01) | Khách, phục vụ | To-be |
| P13 | Khách tự thanh toán chuyển khoản (CR-01) | Khách, thu ngân | To-be |

---

## P1. Phục vụ tại bàn

### P1 As-is

```mermaid
flowchart TD
    A["Khách tới"] --> B{"Có đặt bàn?"}
    B -->|Có| C["Tra sổ tay đặt bàn"]
    B -->|Không| D["Tìm bàn trên sơ đồ giấy ép"]
    C --> D
    D --> E["Phục vụ ghi order ra giấy"]
    E --> F["Thu ngân nhập lại vào POS365"]:::pain
    F --> G["In một phiếu bếp chung ở cửa bếp"]
    G --> H["Bếp trưởng chia phiếu, hô ưu tiên"]
    H --> I["Nấu, gạch phiếu bằng bút"]
    I --> J["Chạy món mang ra, hô số bàn"]
    E -.->|Món thêm hoặc thay đổi| K["Hô miệng, nhắn Zalo, phiếu rời"]:::pain
    K --> F
    E -.->|Món ra sau| K2["Ghi later, giữ phiếu riêng ở quầy"]:::pain
    J --> L["Khách gọi thanh toán"]
    L --> M["Thu ngân in bill, tự tách bill bằng tay"]:::pain
    M --> N["Tiền mặt, QR tĩnh, máy thẻ rời"]:::pain
    N --> O["Quản lý kiểm tra dọn bàn, mở lại bàn"]
    classDef pain fill:#fde2e2,stroke:#c0392b,color:#7b241c
```

**Điểm đau** (S1-Q4, Q7; S2-C1, C2, C3; S3-K2):
- Order có **4 nguồn** khác nhau: giấy, POS, Zalo, lời nói. Thu ngân là "cổ chai" vì phải nhập lại mọi thứ.
- Món thêm in thành phiếu rời, bếp phải tự ghép với phiếu đầu. "Later" không có nghĩa rõ ràng.
- Sót hoặc trùng món khoảng 2–3 lần/tuần.
- Tách bill theo món làm tay. Chuyển bàn thì sơ đồ giấy và POS lệch nhau.
- QR tĩnh không có số tiền và mã bill.

### P1 To-be

```mermaid
flowchart LR
    subgraph PV["Phục vụ - máy cầm tay"]
        A1["Mở bàn, gắn booking nếu có"] --> A2["Chọn món, size, tuỳ chọn, dị ứng"]
        A2 --> A3{"Giữ món?"}
        A3 -->|Không| A4["Gửi bếp"]
        A3 -->|Có| A5["Giữ, bấm gọi ra món sau"]
        A5 --> A4
    end
    subgraph HT["Máy chủ tại quán"]
        B1["Định tuyến theo khu chế biến"] --> B2["In phiếu khu và hiện màn hình bếp"]
        B3["Cập nhật bill ngay lập tức"]
    end
    subgraph BEP["Bếp"]
        C1["Bắt đầu làm"] --> C2["Xong, đặt ở pass"]
    end
    subgraph TN["Thu ngân"]
        D1["Tách, gộp bill"] --> D2["Tiền mặt, QR động, thẻ, ví"]
        D2 --> D3["Đóng bill, dữ liệu cho HĐĐT"]
    end
    A4 --> B1
    A4 --> B3
    B2 --> C1
    C2 --> E1["Chạy món xác nhận đã mang ra"]
    E1 --> D1
```

**Thay đổi chính:**
- Phục vụ gọi món ngay tại bàn, order đi **cùng lúc** tới bếp và bill. **Bỏ bước thu ngân nhập lại** (FR-ORD-01, FR-KIT-01).
- Món thêm hiện **trong ngữ cảnh cả bàn** (FR-ORD-02). Có **giữ món / gọi ra món** thay cho chữ "later" (FR-ORD-04).
- Trạng thái món chung cho sảnh, bếp và quầy (FR-KIT-03, FR-KIT-04, FR-ORD-10).
- Vẫn **giữ phiếu in** trong thí điểm (CON-05).

---

## P2. Huỷ, đổi món, giảm giá, tặng món (To-be)

```mermaid
flowchart TD
    S["Yêu cầu huỷ, đổi món hoặc giảm giá"] --> T{"Loại yêu cầu"}
    T -->|Món chưa gửi bếp| U["Phục vụ sửa tự do - BR-06"]
    T -->|Món đã tới bếp| V["Quản lý duyệt và chọn lý do - BR-07"]
    V --> W["Bếp nhận thông báo, bấm xác nhận đã thấy"]
    W --> X["Quản lý thấy trạng thái xác nhận của bếp"]
    T -->|Món tính nhầm| Y["Lý do tính trùng hoặc sai order, không tính hạn mức - BR-04"]
    T -->|Giảm giá hoặc tặng món| Z{"Trong hạn mức bill và ca?"}
    Z -->|Có| Z1["Quản lý áp dụng kèm lý do - BR-01, BR-02"]
    Z -->|Không| Z2["Gửi yêu cầu duyệt tới điện thoại chủ"]
    Z2 --> Z3{"Chủ phản hồi trong 3 phút?"}
    Z3 -->|Duyệt| Z4["Áp dụng"]
    Z3 -->|Từ chối| Z5["Không áp dụng"]
    Z3 -->|Không phản hồi| Z6{"Lỗi phục vụ thật và tối đa 300k?"}
    Z6 -->|Có| Z7["Quản lý xử lý, thu ngân hoặc bếp trưởng xác nhận - BR-03"]
    Z7 --> Z8["Cảnh báo chủ ngay, kế toán rà soát"]
    Z6 -->|Không| Z9["Tiếp tục chờ chủ duyệt"]
```

- **Hạn mức:** ≤ 10% bill và ≤ 150.000đ/bill; trần 600.000đ/ca/quản lý (BR-01, BR-02).
- **Lý do** chọn từ danh mục; chọn "Khác" thì phải ghi chú (BR-05).
- **Nhật ký:** ai làm, ai duyệt, lúc nào, giá trị trước và sau (FR-AUD-01).

---

## P3. Thanh toán và xác nhận chuyển khoản (To-be)

```mermaid
flowchart TD
    A["Thu ngân mở bill"] --> B{"Phương thức"}
    B -->|Tiền mặt| C["Nhập tiền khách đưa, làm tròn xuống 1.000đ - BR-21"]
    B -->|Chuyển khoản| D["Hiện QR động: số tiền, mã quán, mã bill"]
    B -->|Thẻ| E["Quẹt máy thẻ rời, nhập mã chuẩn chi"]
    D --> F{"Ngân hàng xác nhận tự động?"}
    F -->|Có và khớp| G["Đã thanh toán"]
    F -->|Chưa hoặc không có kết nối| H["Thu ngân kiểm tra app ngân hàng, nhập số tiền và mã tham chiếu - BR-19"]
    H --> I{"Khớp chắc chắn?"}
    I -->|Có| G
    I -->|Không| J["Quản lý duyệt"]
    J --> G
    E --> G
    C --> G
    G --> K["Đóng bill, sinh dữ liệu HĐĐT"]
```

- **Một bill có thể trả bằng nhiều phương thức** (thanh toán kết hợp). Tổng các phần tách phải bằng tổng bill. Không làm tròn từng phần (BR-21).
- **Khi offline:** thẻ và QR chỉ được ghi "dự định, chưa xác nhận" (BR-20). Xem P10.
- **Ảnh chụp màn hình không bao giờ là bằng chứng thanh toán.**

---

## P4. Mở ca, giao ca, chốt ca (To-be)

```mermaid
flowchart TD
    A["Mở ca: quỹ đầu ca 1.000.000đ"] --> B["Trong ca: thu tiền, chi tiền mặt có phiếu chi - BR-24"]
    B --> C{"Giao ca?"}
    C -->|Có| D["Đếm tiền, hai người xác nhận bằng PIN - BR-23"]
    D --> B
    C -->|Cuối ngày| E["Hệ thống tính tiền mặt dự kiến"]
    E --> F["Thu ngân đếm thực tế"]
    F --> G{"Có chênh lệch?"}
    G -->|Không| H["Quản lý kiểm tra và ký"]
    G -->|Có| I["Đếm lại, kiểm tra thanh toán kết hợp"]
    I --> J["Ghi chênh lệch và lý do, không sửa doanh thu"]
    J --> K{"Lệch trên 200.000đ?"}
    K -->|Có| L["Cảnh báo chủ ngay - BR-29"]
    K -->|Không| H
    L --> H
    H --> M["Báo cáo chốt ca tự gửi kế toán"]
    H --> N["Liệt kê khoản dự định, chưa xác nhận - BR-20"]
```

**Tiền mặt dự kiến** = quỹ đầu ca + thu tiền mặt − tiền thối − phiếu chi − hoàn tiền mặt − khoản làm tròn đã ghi.

Thay thế cách cũ là chụp phiếu chốt két gửi Zalo (FR-SHF-04, FR-SHF-06).

---

## P5. Đặt bàn, tiền cọc, tiệc (To-be)

```mermaid
flowchart TD
    A["Khách liên hệ qua điện thoại, Zalo, Facebook, trực tiếp"] --> B["Quản lý tạo booking: ngày giờ, số khách, bàn hoặc nhóm bàn"]
    B --> C{"Nhóm từ 8 người, phòng riêng hoặc tiệc?"}
    C -->|Không| D["Gửi xác nhận booking"]
    C -->|Có| E["Chốt cọc, mức chi tối thiểu, set đặt trước, các bên thanh toán"]
    E --> F["Gửi xác nhận kèm điều khoản cọc, lưu bằng chứng - BR-11"]
    F --> G["Khách chuyển cọc vào TK công ty, nội dung có mã booking - BR-10"]
    G --> H["Ghi nhận cọc là tiền giữ, không phải doanh thu"]
    D --> I{"Khách tới?"}
    H --> I
    I -->|Có| J["Mở bàn từ booking, cấn trừ cọc vào bill cuối"]
    I -->|Huỷ hoặc không tới| K{"Huỷ trước ít nhất 24 giờ?"}
    K -->|Có| L["Hoàn 100%"]
    K -->|Không| M{"Dời booking trong 7 ngày?"}
    M -->|Có| N["Chuyển cọc sang booking mới"]
    M -->|Không| O{"Có bằng chứng đã báo điều khoản?"}
    O -->|Có| P["Giữ 50%, hoàn 50%"]
    O -->|Không| L
```

- **Tiệc** (S8-Q3): nhiều bàn hoặc phòng riêng; số khách có lịch sử thay đổi; set đặt trước kèm **giờ ra đồ nướng và lẩu**; **danh sách chuẩn bị gửi bếp từ hôm trước**; nhiều bên thanh toán, mỗi bên thông tin HĐ riêng (FR-RSV-07, FR-RSV-08, BR-26).
- **Khách trễ:** gọi điện sau 15 phút, giữ bàn khoảng 20 phút (tham số FR-RSV-09).

---

## P6. Đơn mang về và app giao hàng (To-be)

```mermaid
flowchart LR
    A["Đơn mới trên tablet GrabFood hoặc ShopeeFood"] --> B["Nhập vào hệ thống: kênh, mã đơn app, món theo giá app"]
    B --> C{"Mã đơn đã tồn tại?"}
    C -->|Có| D["Cảnh báo trùng, không tạo đơn - BR-40"]
    C -->|Không| E["Gửi bếp theo khu"]
    E --> F["Sẵn sàng, giao shipper"]
    F --> G["Doanh thu ghi theo kênh app"]
    G --> H["Kế toán nhập bảng kê app, khớp theo mã đơn"]
```

- **Thí điểm:** nhân viên nhập tay (Must). Kết nối trực tiếp với app là Could (FR-DLV-05).
- **Hết món:** hệ thống **nhắc cập nhật từng app** cho tới khi có người xác nhận đã cập nhật (FR-KIT-06, BR-18).

---

## P7. Sơ chế chung và chuyển kho (To-be)

```mermaid
flowchart TD
    A["Quán gửi yêu cầu hàng sơ chế cho ngày mai"] --> B["Trưởng sơ chế tổng hợp yêu cầu"]
    B --> C["Lệnh sản xuất: cân nguyên liệu vào"]
    C --> D["Cân hoặc đếm thành phẩm, tính tỷ lệ thành phẩm của lô"]
    D --> E["Đóng gói, dán nhãn: ngày làm, hạn dùng, quán nhận"]
    E --> F["Phiếu chuyển kho: bên gửi ghi xuất"]
    F --> G["Tài xế giao hàng"]
    G --> H["Quán nhận đếm, xác nhận số thực nhận"]
    H --> I{"Khớp số lượng?"}
    I -->|Có| J["Nhập kho quán, tính giá chuyển theo tỷ lệ thành phẩm - BR-35"]
    I -->|Thiếu, rò hoặc hỏng| K["Chuyển trạng thái tranh chấp, ghi bằng chứng - BR-34"]
    K --> L["Chi phí giữ lại chờ kế toán xem xét"]
    L --> M["Giải quyết: điều chỉnh, ghi hao hỏng hoặc trả về"]
```

- **Thay cho** nhóm Zalo và sổ tay (S3-P2).
- **Đơn vị chuẩn:** mỗi mặt hàng có đơn vị cơ sở (g, ml, cái) và quy đổi túi, hộp → đơn vị cơ sở (FR-INV-01).
- **Quán cho quán mượn** dùng cùng cơ chế phiếu chuyển (FR-PRP-04).

---

## P8. Mua hàng và nhận hàng (To-be)

```mermaid
flowchart TD
    A["Quán, bếp gửi đề xuất mua"] --> B["Điều phối mua hàng tổng hợp, đặt nhà cung cấp"]
    B --> C["Nhà cung cấp giao hàng kèm phiếu giao"]
    C --> D["Người nhận đếm, cân, nhập số thực nhận - BR-32"]
    D --> E{"Thịt lạnh hoặc hải sản?"}
    E -->|Có| F["Ghi nhiệt độ, giờ nhận, chất lượng - BR-33"]
    E -->|Không| G["So giá phiếu giao với giá thoả thuận"]
    F --> G
    G --> H{"Thiếu, hỏng hoặc chênh giá?"}
    H -->|Có| I["Ghi tranh chấp, ảnh bổ sung sau"]
    H -->|Không| J["Nhập kho"]
    I --> J
    J --> K["Xuất dữ liệu nhập hàng cho MISA, công nợ nằm ở MISA"]
    X["Mua gấp bằng tiền mặt từ két"] --> Y["Phiếu chi và phiếu nhập, báo ngay điều phối mua hàng"]
    Y --> J
```

- **Thí điểm:** chỉ bắt buộc phần **nhận hàng** cho các mặt hàng ưu tiên, để có số liệu chênh lệch kho. Quy trình đề xuất → đơn mua đầy đủ là Could (thí điểm) và Should (toàn chuỗi).
- **Không quản lý công nợ** nhà cung cấp (CON-10).

---

## P9. Kiểm kê và chênh lệch kho (To-be)

```mermaid
flowchart LR
    A["Lịch kiểm kê: bia mỗi tối, thịt 2 lần mỗi tuần, toàn bộ cuối tháng"] --> B["Phiếu kiểm theo danh sách mặt hàng ưu tiên"]
    B --> C["Nhập theo đơn vị thực tế, hệ thống tự quy đổi"]
    C --> D["Tiêu hao thực tế = tồn đầu + nhập + chuyển đến - chuyển đi - tồn cuối"]
    D --> E["Tiêu hao giải thích được = bán x định lượng + hỏng + bữa NV + khuyến mãi"]
    E --> F["Chênh lệch = thực tế - giải thích được, tách theo loại - BR-36"]
    F --> G{"Vượt ngưỡng?"}
    G -->|Có| H["Tìm nguyên nhân: phiếu thiếu, chuyển kho, hỏng; giải trình"]
    G -->|Không| I["Lưu kỳ kiểm kê"]
```

- Danh sách **~15 mặt hàng ưu tiên** (S3-A1): phần bò, phần heo, gà, hải sản đắt tiền, nước lẩu cốt, 2 sốt chính, hộp ướp và sốt từ sơ chế, bia theo nhãn (2 loại bán chạy), nước ngọt chai, két vỏ.
- **Trước khi báo cáo chênh lệch có ý nghĩa**, cần thống nhất đơn vị và **một lần kiểm kê đầu kỳ đáng tin** (S5-C2).

---

## P10. Sự cố mất mạng và khôi phục (To-be)

```mermaid
flowchart TD
    A["Mất Internet"] --> B{"Máy chủ tại quán và LAN còn chạy?"}
    B -->|Có| C["Tiếp tục gọi món, in bếp, bill, tiền mặt qua máy chủ tại quán"]
    C --> D["Thẻ và QR ghi dự định, chưa xác nhận - BR-20"]
    D --> E["Có mạng lại: tự đồng bộ, không trùng"]
    B -->|Không - mất điện hoặc hỏng máy chủ| F["Chuyển sang phiếu giấy đánh số"]
    F --> G["Phiếu giấy mang thẳng vào bếp"]
    G --> H["Hệ thống hoạt động lại"]
    H --> I["Nhập bù ở chế độ khôi phục, mặc định không gửi bếp - BR-38"]
    I --> J["Một người đối chiếu phiếu giấy với order"]
    J --> K["Quản lý ký trước khi chốt ca - BR-39"]
    A --> L{"Mất quá 15 phút trong giờ phục vụ?"}
    L -->|Có| M["Cảnh báo chủ - BR-29"]
```

- **Bài học từ sự cố tháng 8 ở Cầu Giấy** (S8-T1): khi nhập bù phiếu giấy, máy in bếp tự in lại phiếu, dẫn tới 2 món bị nấu lần hai. Chế độ khôi phục **mặc định không gửi bếp** để chặn lỗi này.
- **Với máy chủ tại quán và 4G dự phòng**, mất đường Internet (loại sự cố hay gặp nhất ở Cầu Giấy) **không còn phải chuyển sang giấy**. Giấy chỉ dùng khi mất điện quá thời gian lưu điện của UPS, hoặc máy chủ tại quán hỏng.

---

## P11. Khớp số hằng ngày của kế toán (To-be)

```mermaid
flowchart LR
    A["Báo cáo chốt ca 3 quán"] --> E["Màn hình khớp số"]
    B["Giao dịch ngân hàng - tự động hoặc nhập file"] --> E
    C["Bảng kê thẻ"] --> E
    D["Bảng kê GrabFood, ShopeeFood"] --> E
    E --> F["Tự khớp theo mã bill, mã đơn app, số tiền"]
    F --> G{"Còn khoản chưa khớp?"}
    G -->|Có| H["Kế toán xử lý tay, ghi giải thích"]
    G -->|Không| I["Đánh dấu đã khớp; chênh lệch do thời điểm chuyển kỳ sau"]
    H --> I
```

- **"Khớp"** nghĩa là giải thích được mỗi giao dịch bán và mỗi khoản tiền **đúng một lần**, kể cả chênh lệch do thời điểm (S4-F4). Không ép tổng ngân hàng bằng tổng doanh thu trong ngày.
- **Tài khoản thu chung cho 3 quán**, nên nội dung QR phải có **mã quán và mã bill** (FR-BIL-09).

---

## P12. Khách gọi thêm món qua QR, nhân viên xác nhận (To-be, CR-01)

```mermaid
flowchart TD
    subgraph KH["Khách - điện thoại"]
        A1["Quét QR bàn"] --> A2{"Bàn đang mở?"}
        A2 -->|Không| A3["Đợi nhân viên đón và mở bàn"]
        A2 -->|Có| A4["Nhập mã ngồi bàn trên thẻ"]
        A4 --> A5["Chọn món thêm, bấm Gửi"]
        A6["Thấy Chờ nhân viên xác nhận"]
    end
    subgraph HT["Hệ thống"]
        B1{"QR đang bật và quán còn kết nối?"}
        B1 -->|Không| B2["Từ chối ngay, báo tạm dừng gọi món"]
        B1 -->|Có| B3["Tạo đơn QR chờ xác nhận, gắn cờ: bia, dị ứng, số lượng lớn, set lẩu đổi, nghi trùng"]
    end
    subgraph PV["Phục vụ khu vực"]
        C1["Hàng chờ xác nhận"] --> C2{"Có cờ cần trao đổi?"}
        C2 -->|Có| C3["Tới bàn trao đổi; dị ứng thì báo bếp trưởng"]
        C2 -->|Không| C4["Xác nhận, có thể giữ món để điều nhịp"]
        C3 --> C4
    end
    A5 --> B1
    B3 --> A6
    B3 --> C1
    C4 --> D1["In phiếu bếp; khách thấy Bếp đã nhận"]
    D1 --> D2["Người ở pass bấm Xong nếu bật cấu hình"]
    D2 --> D3["Chạy món bấm Đã phục vụ"]
```

- **QR-1:** nhân viên luôn nhận **order đầu**; QR chỉ dùng cho **món thêm**; **mọi đơn QR** chờ xác nhận (BR-42); món ăn dừng lúc 21:45 (BR-46).
- **Khách chỉ rút lại món khi món còn chờ xác nhận** (BR-45). Sau đó áp dụng quy trình P2.
- **Mất kết nối** giữa quán và cloud: QR tự tạm dừng; đơn gửi trong lúc đó **không bao giờ được giao sau** (BR-55).

---

## P13. Khách tự thanh toán chuyển khoản (To-be, CR-01)

```mermaid
flowchart TD
    A["Khách bấm Yêu cầu tính tiền"] --> B{"Bill đã chốt? Không còn duyệt treo, không còn đơn QR chờ"}
    B -->|Chưa| C["Nút thanh toán bị khoá; nhân viên hoàn tất bill"]
    C --> B
    B -->|Rồi| D["Khách xem bill: món, giảm giá, cọc, còn phải trả"]
    D --> E{"Tự thanh toán đã được bật sau thử nghiệm?"}
    E -->|Chưa| F["Thu ngân xử lý như UC-08"]
    E -->|Rồi| G["Tạo lệnh thanh toán: đúng số tiền, mã tham chiếu duy nhất"]
    G --> H["QR VietQR, mở app ngân hàng, lưu ảnh QR, tên tài khoản công ty"]
    H --> I["Khách chuyển khoản"]
    I --> J{"Thông báo ngân hàng hợp lệ, đúng mã và số tiền?"}
    J -->|Đủ| K["Bill đã thanh toán; dữ liệu HĐĐT vào hàng chờ"]
    J -->|Thiếu| L["Trả một phần, hiện số còn thiếu"]
    J -->|Chưa có sau N phút| M["Đang chờ xác nhận: không gợi ý trả lại; thu ngân kiểm tra tài khoản"]
    L --> H
    K --> N["Phục vụ thấy đã trả, dọn và nhả bàn"]
    O["Giao dịch sai mã hoặc thừa tiền"] --> P["Hàng chờ không khớp của thu ngân, không đoán bàn"]
```

- **QR-1 chỉ trả cả bill.** Tách bill, tiền mặt, thẻ đều qua thu ngân (BR-48).
- **Chỉ bật khi đã thử tuyến ngân hàng thật** (BR-59, TR-11).
- **Chống lừa đảo:** thanh toán chỉ trên tên miền của quán, hiện tên công ty; thẻ QR được kiểm tra mỗi lần mở bàn (BR-53).

---

## Phân tích chênh lệch As-is → To-be (Gap analysis)

| # | As-is | To-be | Thay đổi | Yêu cầu | Mục tiêu |
|---|---|---|---|---|---|
| 1 | Order giấy, thu ngân nhập lại | Gọi món trên máy cầm tay, đi thẳng tới bếp và bill | Hệ thống + thiết bị + đào tạo | FR-ORD-01, FR-KIT-01 | G3, G5 |
| 2 | Món thêm qua Zalo, hô miệng, phiếu rời | Món thêm trong ngữ cảnh bàn, trạng thái chung | Hệ thống | FR-ORD-02, FR-KIT-03 | G3 |
| 3 | "Later" không rõ nghĩa | Giữ món, gọi ra món | Hệ thống + quy ước | FR-ORD-04 | G3 |
| 4 | Huỷ món không báo bếp | Huỷ có duyệt, bếp xác nhận | Hệ thống + quy tắc | FR-ORD-06, FR-KIT-05 | G3 |
| 5 | Giảm giá không có chính sách viết | Hạn mức, lý do, duyệt từ xa, dự phòng | Chính sách + hệ thống | FR-BIL-04, FR-BIL-05 | G1, G2 |
| 6 | QR tĩnh, khớp tay | QR động theo bill và xác nhận ngân hàng | Tích hợp (tuỳ ngân hàng) | FR-BIL-09, FR-INT-01 | G2 |
| 7 | Chốt két chụp ảnh gửi Zalo | Chốt ca trên hệ thống, báo cáo tự gửi | Hệ thống | FR-SHF-04, FR-SHF-06 | G1, G2 |
| 8 | Cọc ghi sổ, lẫn tài khoản cá nhân | Sổ cọc gắn booking, cấn trừ, quy tắc huỷ | Chính sách + hệ thống | FR-RSV-03, FR-RSV-04, FR-RSV-05 | G2 |
| 9 | Sơ chế ghi sổ tay, Zalo | Lệnh sản xuất, phiếu chuyển có xác nhận | Hệ thống + đơn vị chuẩn | FR-PRP-02, FR-PRP-03 | G4 |
| 10 | Không có định lượng chuẩn | Định lượng cho mặt hàng ưu tiên | Dữ liệu + hệ thống | FR-INV-03, FR-INV-04 | G4 |
| 11 | Hết món báo miệng, quên tắt trên app | Chặn trên sảnh ngay, nhắc từng app | Hệ thống | FR-ORD-07, FR-KIT-06 | G3 |
| 12 | Mất mạng thì dùng giấy, nhập bù bị trùng | Máy chủ tại quán, chế độ khôi phục không gửi bếp | Hạ tầng + hệ thống | FR-OFF-01, FR-OFF-04 | G3, G5 |
| 13 | Chủ có số liệu hôm sau | Dashboard cùng đêm, cảnh báo | Hệ thống | FR-RPT-01, FR-RPT-04 | G1 |
| 14 | Menu và giá sửa riêng từng POS, từng app | Menu tập trung, giá theo kênh, bảng giá lễ | Hệ thống + quy trình duyệt | FR-MNU-07, FR-MNU-08, FR-MNU-09 | G1 |
| 15 | Khách phải chờ phục vụ rảnh mới gọi thêm được (CR-01) | Khách gọi thêm qua QR, nhân viên xác nhận | Hệ thống + thẻ QR + mã ngồi bàn + đào tạo | FR-GST-02–10 | G6 |
| 16 | Khách chờ 5–10 phút để tính tiền lúc đông (CR-01) | Khách tự trả cả bill đã chốt, ngân hàng tự xác nhận | Tích hợp tuyến ngân hàng (tuỳ kết quả thử) | FR-GST-13–17 | G7 |
