# SRS — Phần 5: Ma trận truy vết

> Tệp này được **sinh tự động** bằng script kiểm tra (`check-docs.mjs`) từ bảng yêu cầu chức năng, danh sách use case và tiêu chí chấp nhận. Bản đầy đủ để lọc trong Excel: [05-ma-tran-truy-vet.csv](05-ma-tran-truy-vet.csv).
> Chiều truy vết: **Mục tiêu → Yêu cầu (FR) → Quy tắc (BR) → Use case (UC) → Tiêu chí chấp nhận (AC) → Thành phần thiết kế và bảng dữ liệu**. Mục tiêu gán theo module.

## 1. Tổng quan độ phủ

| Chỉ số | Giá trị |
|---|---|
| Số yêu cầu chức năng | 163 |
| Số use case | 40 |
| Số quy tắc nghiệp vụ | 60 |
| Số tiêu chí chấp nhận | 63 |
| FR Must của thí điểm | 76 (có AC trực tiếp: 59) |
| Quy tắc chưa có FR thực thi | 0 |
| Tham chiếu tới mã không tồn tại | 0 |

## 2. Mục tiêu → Yêu cầu

| Mục tiêu | Số FR | Module |
|---|---|---|
| G1 | 59 | MNU, RSV, BIL, SHF, DLV, RPT |
| G2 | 58 | RSV, BIL, SHF, DLV, RPT, INT, AUD |
| G3 | 41 | TBL, ORD, KIT, RPT, OFF |
| G4 | 25 | INV, PRP, PUR |
| G5 | 23 | TBL, ORD, OFF |
| G6 | 24 | GST |
| G7 | 24 | GST |

## 3. Quy tắc nghiệp vụ → Yêu cầu thực thi

| BR | FR thực thi |
|---|---|
| BR-01 | FR-ADM-06, FR-BIL-04 |
| BR-02 | FR-ADM-06, FR-BIL-04 |
| BR-03 | FR-ADM-06, FR-BIL-05, FR-AUD-03 |
| BR-04 | FR-BIL-06 |
| BR-05 | FR-ADM-05, FR-ORD-06, FR-BIL-04, FR-DLV-03, FR-RPT-07 |
| BR-06 | FR-ORD-05 |
| BR-07 | FR-ORD-06, FR-KIT-05, FR-BIL-06 |
| BR-08 | FR-KIT-07 |
| BR-09 | FR-ORD-09, FR-INV-07 |
| BR-10 | FR-ADM-07, FR-RSV-03, FR-RSV-04 |
| BR-11 | FR-RSV-02, FR-RSV-05, FR-INT-06 |
| BR-12 | FR-RSV-06 |
| BR-13 | FR-RSV-07, FR-BIL-16 |
| BR-14 | FR-RSV-07 |
| BR-15 | FR-MNU-01, FR-MNU-09 |
| BR-16 | FR-MNU-10 |
| BR-17 | FR-MNU-06, FR-MNU-07, FR-MNU-08 |
| BR-18 | FR-ORD-07, FR-KIT-06, FR-DLV-06 |
| BR-19 | FR-BIL-09, FR-BIL-10, FR-INT-01 |
| BR-20 | FR-BIL-11, FR-SHF-04, FR-OFF-01 |
| BR-21 | FR-BIL-02, FR-BIL-08 |
| BR-22 | FR-SHF-01, FR-SHF-02 |
| BR-23 | FR-SHF-03, FR-SHF-04 |
| BR-24 | FR-ADM-06, FR-SHF-02, FR-PUR-05 |
| BR-25 | FR-BIL-13, FR-BIL-15, FR-INT-02, FR-INT-03 |
| BR-26 | FR-RSV-07, FR-BIL-12 |
| BR-27 | FR-BIL-13, FR-BIL-14, FR-SHF-05, FR-INV-08, FR-RPT-07, FR-AUD-01, FR-AUD-02 |
| BR-28 | FR-ADM-02, FR-ADM-03, FR-INV-03 |
| BR-29 | FR-ADM-06, FR-RPT-04, FR-OFF-02 |
| BR-30 | FR-CUS-02, FR-CUS-04, FR-GST-18 |
| BR-31 | FR-INT-05 |
| BR-32 | FR-PUR-04 |
| BR-33 | FR-PUR-04 |
| BR-34 | FR-PRP-03, FR-PRP-04, FR-PRP-05, FR-PRP-06 |
| BR-35 | FR-INV-10, FR-PRP-02, FR-PRP-05, FR-PRP-07 |
| BR-36 | FR-INV-01, FR-INV-05, FR-INV-06 |
| BR-37 | FR-ORD-03, FR-GST-07 |
| BR-38 | FR-OFF-04 |
| BR-39 | FR-OFF-05 |
| BR-40 | FR-DLV-02 |
| BR-41 | FR-GST-03 |
| BR-42 | FR-GST-06, FR-GST-22, FR-GST-23 |
| BR-43 | FR-GST-07, FR-GST-22 |
| BR-44 | FR-GST-05, FR-GST-08 |
| BR-45 | FR-GST-09 |
| BR-46 | FR-GST-04 |
| BR-47 | FR-GST-10 |
| BR-48 | FR-GST-13, FR-GST-14, FR-GST-24 |
| BR-49 | FR-GST-14, FR-GST-15, FR-GST-16, FR-GST-24 |
| BR-50 | FR-GST-19 |
| BR-51 | FR-GST-14, FR-GST-16 |
| BR-52 | FR-GST-17 |
| BR-53 | FR-GST-01, FR-GST-14 |
| BR-54 | FR-GST-18 |
| BR-55 | FR-GST-12 |
| BR-56 | FR-GST-12 |
| BR-57 | FR-GST-02, FR-GST-22 |
| BR-58 | FR-GST-20 |
| BR-59 | FR-GST-14 |
| BR-60 | FR-GST-15 |

## 4. Yêu cầu → Mục tiêu, Quy tắc, Use case, AC, Thiết kế

| FR | TĐ/TC | Mục tiêu | BR | UC | AC | Thành phần thiết kế |
|---|---|---|---|---|---|---|
| FR-ADM-01 | M/M | — | — | UC-30 | — | Identity & Access |
| FR-ADM-02 | M/M | — | BR-28 | UC-30 | — | Identity & Access |
| FR-ADM-03 | M/M | — | BR-28 | UC-30 | AC-33 | Identity & Access |
| FR-ADM-04 | M/M | — | — | UC-30 | — | Identity & Access |
| FR-ADM-05 | M/M | — | BR-05 | UC-30 | AC-40 | Identity & Access |
| FR-ADM-06 | M/M | — | BR-01–03, 24, 29 | UC-30 | AC-40 | Identity & Access |
| FR-ADM-07 | M/M | — | BR-10 | UC-30 | — | Identity & Access |
| FR-ADM-08 | M/M | — | — | UC-30 | — | Identity & Access |
| FR-MNU-01 | M/M | G1 | BR-15 | UC-26 | — | Catalog & Pricing |
| FR-MNU-02 | M/M | G1 | — | UC-26 | — | Catalog & Pricing |
| FR-MNU-03 | M/M | G1 | — | UC-26 | — | Catalog & Pricing |
| FR-MNU-04 | M/M | G1 | — | UC-26 | — | Catalog & Pricing |
| FR-MNU-05 | M/M | G1 | — | UC-26 | — | Catalog & Pricing |
| FR-MNU-06 | S/M | G1 | BR-17 | UC-26 | — | Catalog & Pricing |
| FR-MNU-07 | S/M | G1 | BR-17 | UC-26 | — | Catalog & Pricing |
| FR-MNU-08 | S/M | G1 | BR-17 | UC-26 | — | Catalog & Pricing |
| FR-MNU-09 | S/M | G1 | BR-15 | UC-26 | AC-34 | Catalog & Pricing |
| FR-MNU-10 | M/M | G1 | BR-16 | UC-08 | AC-34 | Catalog & Pricing |
| FR-MNU-11 | S/M | G1 | — | UC-19 | — | Catalog & Pricing |
| FR-RSV-01 | S/M | G1, G2 | — | UC-15 | — | Reservations & Events |
| FR-RSV-02 | S/M | G1, G2 | BR-11 | UC-15 | — | Reservations & Events |
| FR-RSV-03 | S/M | G1, G2 | BR-10 | UC-15 | — | Reservations & Events |
| FR-RSV-04 | S/M | G1, G2 | BR-10 | UC-08 | AC-22 | Reservations & Events |
| FR-RSV-05 | S/M | G1, G2 | BR-11 | UC-17 | AC-23 | Reservations & Events |
| FR-RSV-06 | S/M | G1, G2 | BR-12 | UC-15 | — | Reservations & Events |
| FR-RSV-07 | S/M | G1, G2 | BR-13, 14, 26 | UC-16 | — | Reservations & Events |
| FR-RSV-08 | S/M | G1, G2 | — | UC-16 | — | Reservations & Events |
| FR-RSV-09 | S/M | G1, G2 | — | UC-15 | — | Reservations & Events |
| FR-RSV-10 | C/S | G1, G2 | — | UC-18 | — | Reservations & Events |
| FR-TBL-01 | M/M | G3, G5 | — | UC-01 | — | Outlet Ops |
| FR-TBL-02 | M/M | G3, G5 | — | UC-01 | — | Outlet Ops |
| FR-TBL-03 | M/M | G3, G5 | — | UC-01 | — | Outlet Ops |
| FR-TBL-04 | M/M | G3, G5 | — | UC-06 | AC-07 | Outlet Ops |
| FR-TBL-05 | M/M | G3, G5 | — | UC-06 | AC-07 | Outlet Ops |
| FR-TBL-06 | M/M | G3, G5 | — | UC-01 | — | Outlet Ops |
| FR-ORD-01 | M/M | G3, G5 | — | UC-01 | AC-01 | Outlet Ops |
| FR-ORD-02 | M/M | G3, G5 | — | UC-01 | AC-01 | Outlet Ops |
| FR-ORD-03 | M/M | G3, G5 | BR-37 | UC-01 | AC-03 | Outlet Ops |
| FR-ORD-04 | S/M | G3, G5 | — | UC-02 | — | Outlet Ops |
| FR-ORD-05 | M/M | G3, G5 | BR-06 | UC-01 | AC-04 | Outlet Ops |
| FR-ORD-06 | M/M | G3, G5 | BR-05, BR-07 | UC-03 | AC-05 | Outlet Ops |
| FR-ORD-07 | M/M | G3, G5 | BR-18 | UC-05 | AC-06 | Outlet Ops |
| FR-ORD-08 | M/M | G3, G5 | — | UC-01 | AC-02 | Outlet Ops |
| FR-ORD-09 | S/M | G3, G5 | BR-09 | UC-07 | — | Outlet Ops |
| FR-ORD-10 | M/M | G3, G5 | — | UC-01 | AC-38 | Outlet Ops |
| FR-ORD-11 | M/M | G3, G5 | — | UC-07 | AC-42 | Outlet Ops |
| FR-KIT-01 | M/M | G3 | — | UC-04 | AC-01 | Outlet Ops |
| FR-KIT-02 | M/M | G3 | — | UC-04 | AC-01 | Outlet Ops |
| FR-KIT-03 | M/M | G3 | — | UC-04 | AC-38 | Outlet Ops |
| FR-KIT-04 | M/M | G3 | — | UC-04 | AC-38 | Outlet Ops |
| FR-KIT-05 | M/M | G3 | BR-07 | UC-03 | AC-05 | Outlet Ops |
| FR-KIT-06 | M/M | G3 | BR-18 | UC-05 | AC-06 | Outlet Ops |
| FR-KIT-07 | S/M | G3 | BR-08 | UC-07 | — | Outlet Ops |
| FR-KIT-08 | S/M | G3 | — | UC-04 | — | Outlet Ops |
| FR-KIT-09 | C/S | G3 | — | UC-04 | — | Outlet Ops |
| FR-BIL-01 | M/M | G1, G2 | — | UC-08 | — | Billing & Cash / Payments |
| FR-BIL-02 | M/M | G1, G2 | BR-21 | UC-08 | AC-08 | Billing & Cash / Payments |
| FR-BIL-03 | M/M | G1, G2 | — | UC-08 | AC-35 | Billing & Cash / Payments |
| FR-BIL-04 | M/M | G1, G2 | BR-01, 02, 05 | UC-09 | AC-10 AC-11 | Billing & Cash / Payments |
| FR-BIL-05 | M/M | G1, G2 | BR-03 | UC-10 | AC-12 | Billing & Cash / Payments |
| FR-BIL-06 | M/M | G1, G2 | BR-04, 07 | UC-09 | AC-13 | Billing & Cash / Payments |
| FR-BIL-07 | M/M | G1, G2 | — | UC-08 | AC-35 | Billing & Cash / Payments |
| FR-BIL-08 | M/M | G1, G2 | BR-21 | UC-08 | AC-09 | Billing & Cash / Payments |
| FR-BIL-09 | S/S | G1, G2 | BR-19 | UC-11 | AC-15 | Billing & Cash / Payments |
| FR-BIL-10 | M/M | G1, G2 | BR-19 | UC-11 | AC-14 | Billing & Cash / Payments |
| FR-BIL-11 | M/M | G1, G2 | BR-20 | UC-08 | AC-16 | Billing & Cash / Payments |
| FR-BIL-12 | M/M | G1, G2 | BR-26 | UC-08 | AC-17 | Billing & Cash / Payments |
| FR-BIL-13 | M/M | G1, G2 | BR-25, 27 | UC-12 | AC-36 | Billing & Cash / Payments |
| FR-BIL-14 | M/M | G1, G2 | BR-27 | UC-12 | AC-36 | Billing & Cash / Payments |
| FR-BIL-15 | M/M | G1, G2 | BR-25 | UC-29 | AC-17 | Billing & Cash / Payments |
| FR-BIL-16 | —/S | G1, G2 | BR-13 | UC-16 | — | Billing & Cash / Payments |
| FR-BIL-17 | S/S | G1, G2 | — | UC-08 | — | Billing & Cash / Payments |
| FR-SHF-01 | M/M | G1, G2 | BR-22 | UC-13 | AC-20 | Billing & Cash |
| FR-SHF-02 | M/M | G1, G2 | BR-22, 24 | UC-14 | AC-21 | Billing & Cash |
| FR-SHF-03 | M/M | G1, G2 | BR-23 | UC-13 | AC-19 | Billing & Cash |
| FR-SHF-04 | M/M | G1, G2 | BR-20, 23 | UC-13 | AC-20 | Billing & Cash |
| FR-SHF-05 | M/M | G1, G2 | BR-27 | UC-13 | AC-37 | Billing & Cash |
| FR-SHF-06 | M/M | G1, G2 | — | UC-13 | AC-20 | Billing & Cash |
| FR-DLV-01 | M/M | G1, G2 | — | UC-19 | AC-41 | Outlet Ops / Payments & Reconciliation |
| FR-DLV-02 | M/M | G1, G2 | BR-40 | UC-19 | AC-24 | Outlet Ops / Payments & Reconciliation |
| FR-DLV-03 | M/M | G1, G2 | BR-05 | UC-19 | AC-41 | Outlet Ops / Payments & Reconciliation |
| FR-DLV-04 | S/M | G1, G2 | — | UC-28 | — | Outlet Ops / Payments & Reconciliation |
| FR-DLV-05 | —/C | G1, G2 | — | UC-19 | — | Outlet Ops / Payments & Reconciliation |
| FR-DLV-06 | —/S | G1, G2 | BR-18 | UC-05 | — | Outlet Ops / Payments & Reconciliation |
| FR-INV-01 | S/M | G4 | BR-36 | UC-23 | — | Inventory & Recipes |
| FR-INV-02 | S/M | G4 | — | UC-23 | — | Inventory & Recipes |
| FR-INV-03 | S/M | G4 | BR-28 | UC-24 | — | Inventory & Recipes |
| FR-INV-04 | S/M | G4 | — | UC-23 | — | Inventory & Recipes |
| FR-INV-05 | S/M | G4 | BR-36 | UC-23 | — | Inventory & Recipes |
| FR-INV-06 | S/M | G4 | BR-36 | UC-23 | AC-32 | Inventory & Recipes |
| FR-INV-07 | S/M | G4 | BR-09 | UC-07 | — | Inventory & Recipes |
| FR-INV-08 | S/M | G4 | BR-27 | UC-23 | — | Inventory & Recipes |
| FR-INV-09 | C/S | G4 | — | UC-22 | — | Inventory & Recipes |
| FR-INV-10 | S/M | G4 | BR-35 | UC-22 | — | Inventory & Recipes |
| FR-PRP-01 | S/M | G4 | — | UC-20 | — | Prep & Transfers |
| FR-PRP-02 | S/M | G4 | BR-35 | UC-20 | — | Prep & Transfers |
| FR-PRP-03 | S/M | G4 | BR-34 | UC-21 | AC-30 | Prep & Transfers |
| FR-PRP-04 | S/M | G4 | BR-34 | UC-21 | — | Prep & Transfers |
| FR-PRP-05 | S/M | G4 | BR-34, 35 | UC-21 | AC-30 | Prep & Transfers |
| FR-PRP-06 | S/M | G4 | BR-34 | UC-21 | — | Prep & Transfers |
| FR-PRP-07 | C/S | G4 | BR-35 | UC-29 | — | Prep & Transfers |
| FR-PUR-01 | C/S | G4 | — | UC-25 | — | Purchasing & Receiving |
| FR-PUR-02 | C/S | G4 | — | UC-25 | — | Purchasing & Receiving |
| FR-PUR-03 | C/S | G4 | — | UC-25 | — | Purchasing & Receiving |
| FR-PUR-04 | S/M | G4 | BR-32, 33 | UC-22 | AC-31 | Purchasing & Receiving |
| FR-PUR-05 | S/M | G4 | BR-24 | UC-14 | — | Purchasing & Receiving |
| FR-PUR-06 | C/S | G4 | — | UC-22 | — | Purchasing & Receiving |
| FR-PUR-07 | C/S | G4 | — | UC-25 | — | Purchasing & Receiving |
| FR-PUR-08 | —/C | G4 | — | UC-25 | — | Purchasing & Receiving |
| FR-CUS-01 | C/S | — | — | UC-32 | — | Customers & Consent |
| FR-CUS-02 | C/S | — | BR-30 | UC-32 | — | Customers & Consent |
| FR-CUS-03 | C/S | — | — | UC-32 | — | Customers & Consent |
| FR-CUS-04 | —/S | — | BR-30 | UC-32 | — | Customers & Consent |
| FR-CUS-05 | C/S | — | CON-07 | UC-32 | — | Customers & Consent |
| FR-RPT-01 | M/M | G1, G2, G3 | — | UC-27 | AC-29 | Reporting & Alerts |
| FR-RPT-02 | M/M | G1, G2, G3 | — | UC-27 | — | Reporting & Alerts |
| FR-RPT-03 | M/M | G1, G2, G3 | — | UC-28 | AC-39 | Reporting & Alerts |
| FR-RPT-04 | M/M | G1, G2, G3 | BR-29 | UC-27 | AC-28 | Reporting & Alerts |
| FR-RPT-05 | S/M | G1, G2, G3 | — | UC-28 | — | Reporting & Alerts |
| FR-RPT-06 | S/M | G1, G2, G3 | — | UC-27 | — | Reporting & Alerts |
| FR-RPT-07 | M/M | G1, G2, G3 | BR-05, 27 | UC-27 | AC-39 | Reporting & Alerts |
| FR-RPT-08 | M/M | G1, G2, G3 | — | UC-27 | AC-42 | Reporting & Alerts |
| FR-RPT-09 | S/M | G1, G2, G3 | — | UC-27 | — | Reporting & Alerts |
| FR-INT-01 | S/S | G2 | BR-19 | UC-11 | AC-15 | Integrations |
| FR-INT-02 | M/M | G2 | BR-25 | UC-29 | AC-17 | Integrations |
| FR-INT-03 | C/S | G2 | BR-25 | UC-29 | — | Integrations |
| FR-INT-04 | S/M | G2 | — | UC-29 | — | Integrations |
| FR-INT-05 | M/M | G2 | BR-31 | UC-29 | — | Integrations |
| FR-INT-06 | S/M | G2 | BR-11 | UC-15 | — | Integrations |
| FR-OFF-01 | M/M | G3, G5 | BR-20 | UC-31 | AC-25 | Sync / Recovery |
| FR-OFF-02 | M/M | G3, G5 | BR-29 | UC-31 | AC-25 | Sync / Recovery |
| FR-OFF-03 | M/M | G3, G5 | — | UC-31 | AC-25 | Sync / Recovery |
| FR-OFF-04 | M/M | G3, G5 | BR-38 | UC-31 | AC-26 | Sync / Recovery |
| FR-OFF-05 | M/M | G3, G5 | BR-39 | UC-31 | AC-27 | Sync / Recovery |
| FR-OFF-06 | M/M | G3, G5 | — | UC-01 | AC-02 | Sync / Recovery |
| FR-AUD-01 | M/M | G2 | BR-27 | UC-30 | AC-37 | Approvals & Audit |
| FR-AUD-02 | M/M | G2 | BR-27 | — | AC-37 | Approvals & Audit |
| FR-AUD-03 | M/M | G2 | BR-03 | UC-10 | AC-12 | Approvals & Audit |
| FR-AUD-04 | M/M | G2 | — | UC-28 | AC-37 | Approvals & Audit |
| FR-GST-01 | S/S | G6, G7 | BR-53 | UC-39 | AC-61 | Guest Ordering / Guest Payments |
| FR-GST-02 | S/S | G6, G7 | BR-57 | UC-33 | AC-44 | Guest Ordering / Guest Payments |
| FR-GST-03 | S/S | G6, G7 | BR-41 | UC-33 | AC-43 AC-58 AC-60 | Guest Ordering / Guest Payments |
| FR-GST-04 | S/S | G6, G7 | BR-46 | UC-33 | AC-50 | Guest Ordering / Guest Payments |
| FR-GST-05 | S/S | G6, G7 | BR-44 | UC-34 | AC-45 | Guest Ordering / Guest Payments |
| FR-GST-06 | S/S | G6, G7 | BR-42 | UC-35 | AC-45 | Guest Ordering / Guest Payments |
| FR-GST-07 | S/S | G6, G7 | BR-43, BR-37 | UC-35 | AC-46 | Guest Ordering / Guest Payments |
| FR-GST-08 | S/S | G6, G7 | BR-44 | UC-35 | AC-47 | Guest Ordering / Guest Payments |
| FR-GST-09 | S/S | G6, G7 | BR-45 | UC-34 | AC-48 | Guest Ordering / Guest Payments |
| FR-GST-10 | S/S | G6, G7 | BR-47 | UC-36 | AC-49 | Guest Ordering / Guest Payments |
| FR-GST-11 | S/S | G6, G7 | — | UC-37 | AC-62 | Guest Ordering / Guest Payments |
| FR-GST-12 | S/S | G6, G7 | BR-55, BR-56 | UC-39 | AC-51 AC-52 | Guest Ordering / Guest Payments |
| FR-GST-13 | S/S | G6, G7 | BR-48 | UC-38 | AC-53 | Guest Ordering / Guest Payments |
| FR-GST-14 | S/S | G6, G7 | BR-48, 49, 51, 53, 59 | UC-38 | AC-54 AC-56 | Guest Ordering / Guest Payments |
| FR-GST-15 | S/S | G6, G7 | BR-49, 60 | UC-38 | AC-54 AC-55 | Guest Ordering / Guest Payments |
| FR-GST-16 | S/S | G6, G7 | BR-49, 51 | UC-40 | AC-55 | Guest Ordering / Guest Payments |
| FR-GST-17 | S/S | G6, G7 | BR-52 | UC-38 | AC-54 | Guest Ordering / Guest Payments |
| FR-GST-18 | S/S | G6, G7 | BR-54, 30 | UC-38 | AC-57 | Guest Ordering / Guest Payments |
| FR-GST-19 | S/S | G6, G7 | BR-50 | UC-40 | AC-56 | Guest Ordering / Guest Payments |
| FR-GST-20 | —/S | G6, G7 | BR-58 | UC-34 | AC-59 | Guest Ordering / Guest Payments |
| FR-GST-21 | S/S | G6, G7 | — | UC-27 | AC-63 | Guest Ordering / Guest Payments |
| FR-GST-22 | —/C | G6, G7 | BR-42, 43, 57 | UC-34 | — | Guest Ordering / Guest Payments |
| FR-GST-23 | —/C | G6, G7 | BR-42 | UC-34 | — | Guest Ordering / Guest Payments |
| FR-GST-24 | —/C | G6, G7 | BR-48, 49 | UC-38 | — | Guest Ordering / Guest Payments |
