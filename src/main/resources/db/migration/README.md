# Migration Flyway

Baseline (version 1) là database dựng từ `docs/04-schema-v4.1.sql` → `docs/09-acl-v4.1.sql` → `docs/10-bao-cao-v4.1.sql`.
Không chép lại các file đó vào đây. Thay đổi schema phát sinh sau đóng băng đặt ở thư mục này với tên `V2__mo_ta.sql`,
`V3__…`, và phải được nhóm duyệt (xem `docs/backend/DEVELOPMENT_GUIDE.md`, mục Migration).
