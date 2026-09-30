# Bộ tài liệu phân tích thiết kế hệ thống — Quản lý chuỗi nhà hàng Bếp Nhà & Nướng

> **Bản mở rộng, chỉ để tham khảo.** Đồ án dùng **bản core** ở [`../docs-core/`](../docs-core/README.md) (một nhà hàng, có mã nguồn chạy được). Bộ tài liệu này không còn được cập nhật. Phần CR-02 (chuyển sang Hà Nội, nhân viên quét QR) mới tích hợp một phần.

**Cách làm:**
- **Codex** khảo sát thực tế ngành F&B Việt Nam (có web search), rồi **đóng vai khách hàng**: chị Mai Anh, chủ chuỗi 3 quán ở Hà Nội, cùng nhân viên.
- **Claude** làm **BA**, dùng skill pack [45ck/business-analysis-skills](https://github.com/45ck/business-analysis-skills) và [45ck/uml-analysis-modelling-skills](https://github.com/45ck/uml-analysis-modelling-skills) (đã cài vào `.claude/skills` của project).
- **10 buổi làm việc**, trao đổi bằng tiếng Anh. **Baseline** được xác nhận ở Buổi 6. **Yêu cầu thay đổi CR-01** (khách gọi món qua QR, trạng thái realtime, tự thanh toán chuyển khoản) được duyệt đưa vào thiết kế ở Buổi 10.

**Công nghệ bắt buộc:** **Java Spring Boot · React · PostgreSQL · CI/CD đầy đủ.**

## Nên đọc theo thứ tự nào (cho người mới với nghiệp vụ)

1. [Từ điển thuật ngữ](02-phan-tich-nghiep-vu/04-thuat-ngu.md): đọc trước để hiểu các từ POS, KDS, định lượng, đối soát, lệnh thanh toán, webhook...
2. [Tổng quan dự án (BRD 1)](02-phan-tich-nghiep-vu/01-tong-quan-du-an.md): vấn đề, mục tiêu G1–G7, phạm vi, phương án, bên liên quan, rủi ro, lộ trình.
3. [Quy trình as-is / to-be (BRD 2)](02-phan-tich-nghiep-vu/02-quy-trinh-as-is-to-be.md): 13 quy trình, gồm P12 (khách gọi thêm qua QR) và P13 (tự thanh toán).
4. [Quy tắc nghiệp vụ (BRD 3)](02-phan-tich-nghiep-vu/03-quy-tac-nghiep-vu.md): 60 quy tắc có tham số.
5. SRS:
   - [Yêu cầu chức năng](03-dac-ta-yeu-cau/01-yeu-cau-chuc-nang.md) (163 FR, gồm module GST cho kênh khách)
   - [Phi chức năng và chuyển đổi](03-dac-ta-yeu-cau/02-yeu-cau-phi-chuc-nang.md) (46 NFR, 16 TR)
   - [Use case](03-dac-ta-yeu-cau/03-use-case.md) (40 UC)
   - [Tiêu chí chấp nhận](03-dac-ta-yeu-cau/04-tieu-chi-chap-nhan.md) (63 AC)
   - [Ma trận truy vết](03-dac-ta-yeu-cau/05-ma-tran-truy-vet.md) (kèm [CSV](03-dac-ta-yeu-cau/05-ma-tran-truy-vet.csv))
6. Thiết kế:
   - [Kiến trúc Edge + Cloud](04-thiet-ke-he-thong/01-kien-truc.md) (Spring Boot, React, PostgreSQL; kênh khách qua cloud)
   - [Mô hình lớp](04-thiet-ke-he-thong/02-mo-hinh-lop.md)
   - [CSDL / ERD](04-thiet-ke-he-thong/03-co-so-du-lieu.md)
   - [Sequence và state](04-thiet-ke-he-thong/04-tuong-tac-trang-thai.md)
   - [Giao diện](04-thiet-ke-he-thong/05-giao-dien.md) (có App Khách)
   - [**Tham khảo repo** và chuyển về stack của dự án](04-thiet-ke-he-thong/06-tham-khao-repo.md)
   - [**Cấu trúc mã nguồn và CI/CD**](04-thiet-ke-he-thong/07-ma-nguon-va-cicd.md)
7. Kiểm soát:
   - [Quyết định và vấn đề mở](05-kiem-soat-chat-luong/01-van-de-mo-va-quyet-dinh.md)
   - [Kiểm tra chất lượng và đối chiếu persona](05-kiem-soat-chat-luong/02-kiem-tra-chat-luong.md)
   - [**Yêu cầu thay đổi CR-01**](05-kiem-soat-chat-luong/03-yeu-cau-thay-doi-CR01.md)

## Hồ sơ khảo sát gốc

- [Khảo sát thực tế của Codex](01-thu-thap-yeu-cau/00-codex-khao-sat-thuc-te.en.md) (tiếng Anh, có nguồn)
- [Khảo sát cho CR-01: QR, trạng thái, tự thanh toán](01-thu-thap-yeu-cau/04-codex-khao-sat-qr-thanh-toan.en.md) (tiếng Anh, có nguồn)
- [Biên bản 10 buổi làm việc](01-thu-thap-yeu-cau/02-bien-ban-phong-van.en.md) (tiếng Anh, nguyên văn)
- [Tóm tắt phỏng vấn](01-thu-thap-yeu-cau/03-tom-tat-phong-van.md) (tiếng Việt, tách sự kiện, suy luận và câu hỏi mở)
- Persona giấu của khách giả lập: `../.client-sim/persona-private.md`

## Sơ đồ

Mọi sơ đồ viết bằng **Mermaid**, xem được trên GitHub, VS Code (extension Markdown Preview Mermaid) hoặc mermaid.live. **58/58** sơ đồ đã được kiểm tra render không lỗi.
