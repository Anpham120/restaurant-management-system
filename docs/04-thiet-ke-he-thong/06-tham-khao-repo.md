# Thiết kế — Phần 6: Tham khảo repo mã nguồn mở và chuyển đổi sang công nghệ của dự án

> **Công nghệ bắt buộc** (yêu cầu của nhóm dự án, 30/09/2026): **Backend Java Spring Boot · Frontend React · CSDL PostgreSQL · DevOps có CI/CD đầy đủ**.
> **Nguyên tắc tham khảo:**
> - Repo khác công nghệ vẫn được tham khảo; chỉ lấy **mẫu thiết kế và ý tưởng nghiệp vụ**, rồi hiện thực lại bằng Spring Boot và React.
> - Repo **không có license** thì **không sao chép mã**; chỉ học cách tổ chức.
> - Repo **MIT** được phép tái sử dụng, nhưng phải giữ thông báo bản quyền.
> - Khảo sát bằng `gh api` ngày 30/09/2026. Số sao và ngày cập nhật có thể đã thay đổi.

## 1. Repo bạn đưa và repo tìm thêm

| # | Repo | Công nghệ thực tế | License | Mức liên quan | Nên học |
|---|---|---|---|---|---|
| 1 | [mtayyab/numa](https://github.com/mtayyab/numa) | **Spring Boot** (Maven, Liquibase) + **Next.js/React** (TypeScript, Tailwind) + **PostgreSQL**, Docker, nginx | Không có | ⭐⭐⭐⭐⭐ | **Phiên bàn** (`DiningSession`) và **khách tham gia phiên** (`SessionGuest`) qua QR; trạng thái phiên `ACTIVE, PAUSED, AWAITING_PAYMENT, COMPLETED, CANCELLED`; kiểm tra chia bill; phân tách Controller–Service–Repository–DTO |
| 2 | [user7121/Restaurant-Management-System](https://github.com/user7121/Restaurant-Management-System) | Node/Express + React (Vite) + app khách riêng + SQL PostgreSQL, Docker Compose | MIT | ⭐⭐⭐⭐ | **Pipeline CI/CD**: lint → test có CSDL dạng service → smoke test `/health` → build frontend → build và push image lên GHCR khi merge `main`; tách **customer-app** khỏi app nội bộ |
| 3 | [nazrul-ancala/Restaurant-Management-System](https://github.com/nazrul-ancala/Restaurant-Management-System) | Node/TypeScript + Prisma, frontend SCSS/JS, Docker Compose | Không có | ⭐⭐⭐⭐ | Bộ **tài liệu nghiệp vụ** (vision, requirements, user stories, workflow, business rules, domain model, ERD, architecture) để so với bộ tài liệu này; **kiểm thử E2E Playwright** cho luồng bếp và thanh toán |
| 4 | QRMENU | Không tìm được đúng repo "Spring Boot + React" theo tên. Gần nhất: [AaronMurillo01/qrmenu_frontend](https://github.com/AaronMurillo01/qrmenu_frontend) (JS), [sedefbas/qrMenu-backend](https://github.com/sedefbas/qrMenu-backend) (Java) | — | ⭐⭐ | Ý tưởng menu số theo QR |
| 5 | OrderKing | Gần nhất: [Ryan87834/orderking](https://github.com/Ryan87834/orderking): *"QR-code ordering & POS SaaS"*, Next.js/TypeScript | MIT | ⭐⭐⭐ | Bố cục màn hình POS, dashboard, menu theo QR |
| 6 | [lequochuy05/order_by_qr](https://github.com/lequochuy05/order_by_qr) | **Spring Boot** + frontend + Docker + **GitHub Actions** | Không có | ⭐⭐⭐⭐⭐ | Module theo nghiệp vụ (`modules/order, kitchen, payment, notification`); **State pattern cho đơn** (Pending → Serving → AwaitingPayment → Completed/Cancelled); **lịch sử trạng thái** món và đơn; **PaymentGateway + Resolver** (nhiều cổng); **đánh dấu trả trùng**; **dọn giao dịch treo** bằng `@Scheduled`; **WebSocket STOMP** xác thực khi CONNECT và phân quyền khi SUBSCRIBE; CI: `mvn verify` rồi build và push image |
| 7 | [TruongDx2004/restaurant-ordering-system](https://github.com/TruongDx2004/restaurant-ordering-system) | **Spring Boot** + **React (Vite)** + WebSocket + MoMo; GitHub Actions `ci.yml` và `cd.yml` | Không có | ⭐⭐⭐⭐ | Màn hình **chờ xác nhận thanh toán** (`PaymentWaitingModal`); thanh toán tiền mặt và online; CD kiểu **SCP + SSH** (đơn giản, dùng cho VPS) |
| 8 | [Ashutosh-negi07/Plato](https://github.com/Ashutosh-negi07/Plato) | **Spring Boot** + Flyway + PostgreSQL | **MIT** | ⭐⭐⭐⭐⭐ | `QrTokenService` sinh token bằng `SecureRandom`; **phiên khách** `customer_sessions` với `session_token` duy nhất, **hết hạn trượt 30 phút** (`last_activity`, `expires_at`), nhân viên đóng phiên; migration Flyway theo phiên bản |
| 9 | [Stephen-C-Noh/restaurant-pos](https://github.com/Stephen-C-Noh/restaurant-pos) | **Spring Boot** + **React** + Flyway + Docker Compose | Không có | ⭐⭐⭐⭐ | **KDS theo sự kiện** (`OrderFiredEvent`, `ItemStatusChangedEvent` → hiển thị theo `KitchenSection`); `WebSocketConfig` |
| 10 | [payOSHQ/payos-demo-java-spring](https://github.com/payOSHQ/payos-demo-java-spring) | Spring Boot 3, Java 17, **SDK `vn.payos:payos-java`** | Không có (demo chính thức) | ⭐⭐⭐⭐⭐ | Tạo **link thanh toán VietQR** và nhận **webhook** (`/payment/payos_transfer_handler`) bằng SDK chính thức |
| 11 | [thanhnguyenle/19130206_19130163_LUANVAN2023](https://github.com/thanhnguyenle/19130206_19130163_LUANVAN2023) | React Native + React + Spring Boot (microservice), luận văn | Không có | ⭐⭐ | Tham khảo cách trình bày luận văn. **Không** chọn microservice vì ràng buộc chi phí (ADR-02) |

## 2. Mẫu thiết kế được chọn và cách chuyển về stack của dự án

| Mẫu | Lấy từ | Áp dụng trong BNN-RMS (Spring Boot / React / PostgreSQL) | Tài liệu cập nhật |
|---|---|---|---|
| **Phiên bàn + khách tham gia qua QR** | numa, Plato | `table_session` (đã có) + `guest_session` (mới): token ngẫu nhiên 128-bit, hết hạn trượt, **chỉ tạo được khi bàn đang mở** (BR-41) | Mô hình lớp, CSDL |
| **Token QR bàn** | Plato `QrTokenService` | `table_qr_code.token` sinh bằng `SecureRandom`, in trên thẻ bàn chống bóc dán; đổi được khi nghi bị giả (BR-53) | CSDL |
| **State pattern cho đơn và món** | order_by_qr | Giữ máy trạng thái ST-01 và bổ sung **ST-09 đơn QR của khách**. Trong Java: enum trạng thái + lớp chuyển trạng thái có kiểm tra điều kiện; **bảng lịch sử trạng thái** để hiện dòng thời gian cho khách | Tương tác và trạng thái |
| **KDS theo sự kiện** | restaurant-pos | Sự kiện miền `OrderRoundSent`, `ItemReady`, `ItemServed` → `ApplicationEventPublisher` → STOMP `/topic/outlet.{id}.kitchen.{station}` và `/topic/guest.{sessionId}` | Kiến trúc |
| **WebSocket an toàn** | order_by_qr | Spring WebSocket STOMP: **xác thực ở CONNECT** (JWT nhân viên hoặc token phiên khách), **phân quyền ở SUBSCRIBE** (khách chỉ nghe được kênh của phiên mình) | Kiến trúc |
| **Cổng thanh toán dạng strategy** | order_by_qr | `PaymentProvider` interface → `VietcombankAdapter` / `PayOsAdapter` / `SePayAdapter` / `ManualConfirmation`; chọn qua cấu hình (C2 của CR-01) | Kiến trúc |
| **Phát hiện trả trùng, dọn lệnh treo** | order_by_qr | `PaymentIntent` hết hạn sau N phút (`@Scheduled`); giao dịch về sau khi bill đã đủ tiền được đánh dấu **thừa hoặc trùng** để hoàn (BR-50) | CSDL, trạng thái |
| **Webhook có chữ ký** | payOS demo | Kiểm tra chữ ký HMAC, idempotency theo mã giao dịch, **đối chiếu số tiền thực nhận** trước khi xác nhận (BR-49) | Tương tác |
| **Màn hình chờ xác nhận thanh toán** | TruongDx2004 | Trang khách hiện "đang chờ ngân hàng xác nhận" và **không** gợi ý trả lần hai (BR-51) | Giao diện |
| **App khách tách riêng** | user7121 | `apps/guest` (React) tách khỏi `apps/staff`, `apps/backoffice`: bundle nhỏ, tên miền riêng, bề mặt tấn công hẹp | Kiến trúc, mã nguồn |
| **CI/CD** | user7121, order_by_qr, TruongDx2004 | GitHub Actions: build và test (Testcontainers PostgreSQL) → lint và test frontend → E2E Playwright → quét bảo mật → build image → push GHCR → staging tự động → production có duyệt | [Cấu trúc mã nguồn và CI/CD](07-ma-nguon-va-cicd.md) |
| **E2E cho luồng bếp và thanh toán** | nazrul-ancala | Kịch bản Playwright bám theo tiêu chí chấp nhận (AC-01, 05, 08, 15, 25, 43–52) | CI/CD |

## 3. Điều **không** áp dụng và lý do

| Thực tế trong repo | Lý do không áp dụng |
|---|---|
| Microservices (luận văn tham khảo) | Ngân sách và đội nhỏ, nên chọn modular monolith (ADR-02) |
| Một CSDL cloud cho mọi thứ, không có edge | Không đáp ứng yêu cầu chạy khi mất mạng (NFR-08) |
| Đơn QR tự vào bếp, khách tự huỷ | Khách chốt: thí điểm **nhân viên xác nhận mọi đơn QR**; khách chỉ rút lại khi đơn còn chờ (BR-42, BR-45) |
| Trạng thái "đang nấu" hiển thị cho khách | Bếp không bấm "đang nấu" lúc đông, nên **chỉ hiện trạng thái trung thực** (BR-47) |
| Xác nhận thanh toán theo trang trả về (return URL) | Chỉ tin **thông báo từ máy chủ** có chữ ký và đúng số tiền (BR-49) |
| Deploy bằng SCP + SSH trực tiếp | Dùng image có phiên bản + Docker Compose để **rollback được** |
