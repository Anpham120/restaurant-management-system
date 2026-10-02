# 13. Kế hoạch và báo cáo kiểm thử

Tài liệu này là việc P5-01 ([tài liệu 10](10-ke-hoach-phat-trien.md)). Cách tổ chức các mức kiểm thử ở [tài liệu 9, mục 9.7](09-kien-truc-va-cicd.md). Truy vết từ yêu cầu tới test tự động ở [README](README.md#truy-vết).

## 13.1 Kế hoạch

**Mục tiêu.**
- Chứng minh hệ thống đạt tiêu chí nghiệm thu ở [mục 1.5](01-tam-nhin-du-an.md#15-tiêu-chí-nghiệm-thu) và tiêu chí chấp nhận (AC) của các user story.
- Mỗi AC có ít nhất một test tự động. Riêng AC phải nghe hoặc nhìn trên thiết bị thật (âm báo, cập nhật trong ≤ 2 giây) thì kiểm tra tay khi demo.

**Phạm vi.**
- US-01 → US-19 là các story của bản core; test case ở mục 13.3.
- US-20 → US-41 là các tính năng làm thêm. Mỗi story có test tích hợp riêng, ghi trong bảng truy vết ở README.

**Mức kiểm thử và công cụ:**

| Mức | Công cụ | Chạy khi |
|---|---|---|
| Đơn vị | JUnit 5, AssertJ, Mockito | Mỗi PR và mỗi lần push |
| Tích hợp | Spring Boot Test, MockMvc, Testcontainers (PostgreSQL 17 thật) | Mỗi PR và mỗi lần push |
| Kiến trúc | ArchUnit, 10 quy tắc | Mỗi PR và mỗi lần push |
| Frontend, component | Vitest, Testing Library (jsdom) | Mỗi PR và mỗi lần push |
| ERD và migration | `scripts/check-erd.mjs` | Mỗi PR và mỗi lần push |
| E2E | Playwright trên Chromium, cả ứng dụng dựng bằng Docker Compose | Mỗi PR và mỗi lần push |
| Bảo mật | CodeQL (Java, TypeScript), Trivy (image), Dependabot | Mỗi PR, mỗi lần build image, mỗi tuần |
| Tải | k6 | Chạy tay trước khi phát hành |
| Chạy thử trên trình duyệt | Playwright, viết riêng cho từng tính năng | Trước khi mở PR của tính năng |

**Môi trường.**
- CI: GitHub Actions trên Ubuntu, Java 21, Node 24.
- Máy dev: Windows 11 với Docker Desktop, dùng để chạy thử và đo tải.

**Tiêu chí đạt:**
- Không test nào đỏ.
- Độ phủ backend từ 70% số dòng (JaCoCo chặn CI nếu thấp hơn).
- E2E xanh.
- ERD khớp migration, 0 lệch.
- NFR-02: p95 dưới 500 ms và dưới 1% request lỗi với 30 người dùng.

## 13.2 Kết quả (02/10/2026)

| Hạng mục | Kết quả |
|---|---|
| Backend | **192 test, 0 lỗi**, trong 36 lớp test (gồm 10 quy tắc kiến trúc) |
| Độ phủ backend | **91,9%** số dòng (2608/2839); ngưỡng 70% |
| Frontend | **83 test** trong 25 tệp, 0 lỗi; ESLint và kiểm tra kiểu sạch |
| E2E | 3 kịch bản xanh: luồng QR và chuyển khoản tới bàn trống; mỗi vai trò chỉ làm việc của mình; trang "Của tôi" |
| ERD và migration | 23 migration, 35 bảng, 297 cột, 111 khoá, 0 lệch |
| Tải (NFR-02) | p95 **23,8 ms**, 0% lỗi trên 2.281 request (mục 13.4) |
| Bảo mật | CodeQL Java và TypeScript chạy ở mọi PR; Trivy quét 2 image; không chặn PR nào |
| Chạy thử | Mỗi tính năng P1 → P4 và phần triển khai đều dựng bằng Docker Compose, rồi chạy Playwright trên Chromium, cả ở khổ điện thoại 375 px |

## 13.3 Test case theo tiêu chí chấp nhận

Mỗi dòng là một AC của [tài liệu 3](03-user-stories.md), viết gọn. Test backend nằm trong `backend/src/test/java/vn/khoibep/rms/`, test frontend trong `frontend/src/`. "E2E" là kịch bản trong `e2e/tests/acceptance.spec.ts`. Tất cả đều **đạt** ở lần chạy ngày 02/10/2026, trừ các dòng ghi "kiểm tra tay".

| Mã | Tiêu chí | Test |
|---|---|---|
| TC-01.1 | Đăng nhập đúng thì vào màn hình theo vai trò | `AuthIntegrationTest.loginReturnsTokenAndRole`, `format.test.ts` (`homePath`) |
| TC-01.2 | Sai mật khẩu không nói rõ sai phần nào | `AuthIntegrationTest.wrongPasswordDoesNotSayWhichPartWasWrong` |
| TC-01.3 | Tài khoản bị khoá không đăng nhập được | `AuthIntegrationTest.lockingAnAccountTakesEffectImmediately` |
| TC-01.4 | Một tên đăng nhập thử quá 10 lần mỗi phút thì bị chặn, kèm thời gian chờ | `RateLimitIntegrationTest.aUserNameIsTriedAtMostTenTimesAMinute` |
| TC-02.1 | Tạo tài khoản trùng tên đăng nhập thì báo lỗi | `AuthIntegrationTest.onlyAdminManagesEmployees` |
| TC-02.2 | Đặt lại mật khẩu thì đăng nhập được bằng mật khẩu mới | `AuthIntegrationTest.resetPasswordLetsEmployeeSignInWithNewOne` |
| TC-02.3 | Phục vụ gọi API tạo nhân viên thì nhận 403 | `AuthIntegrationTest.onlyAdminManagesEmployees` |
| TC-02.4 | CSDL mới, không bật demo: tài khoản quản trị đầu tiên từ biến môi trường | `InitialAdminInitializerTest` (5 test), chạy thật bằng Docker Compose |
| TC-03.1 | Khoá tài khoản đang đăng nhập thì yêu cầu kế tiếp bị từ chối (401) | `AuthIntegrationTest.lockingAnAccountTakesEffectImmediately` |
| TC-03.2 | Không xoá được nhân viên, chỉ khoá | `EmployeeProfileIntegrationTest.resigningLocksTheAccountButKeepsTheProfile`; API không có lệnh xoá |
| TC-04.1 | Đổi giá thì đơn mới dùng giá mới, đơn cũ giữ giá cũ | `OrderFlowIntegrationTest.priceIsFrozenWhenOrdered` |
| TC-04.2 | Không xoá được món đã từng được gọi | `OrderFlowIntegrationTest.dishAlreadyOrderedCannotBeDeleted` |
| TC-05.1 | Mỗi bàn một mã QR khác nhau | Ràng buộc `UNIQUE (qr_token)`; in thẻ: kiểm tra tay |
| TC-05.2 | Tạo lại mã QR thì thẻ cũ hết hiệu lực | `GuestQrIntegrationTest.regeneratedQrCodeReplacesTheOldOne`, `unknownQrCodeIsRejected` |
| TC-06.1 | Xuất nhiều hơn tồn thì báo lỗi, tồn không đổi | `InventoryIntegrationTest.stockChangesOnlyThroughMovements` |
| TC-06.2 | Kiểm kê ghi phiếu điều chỉnh bằng phần chênh lệch | `InventoryIntegrationTest.stockChangesOnlyThroughMovements` |
| TC-06.3 | Tồn ≤ mức tối thiểu thì hiện "Sắp hết" | `InventoryIntegrationTest.stockChangesOnlyThroughMovements` |
| TC-07.1 | Doanh thu chỉ tính khoản đã xác nhận, theo ngày giờ Việt Nam | `ReportIntegrationTest.revenueCountsConfirmedPaymentsAndSkipsCancelledDishes` |
| TC-07.2 | Món huỷ không tính vào món bán chạy | `ReportIntegrationTest.revenueCountsConfirmedPaymentsAndSkipsCancelledDishes` |
| TC-08.1 | Mở đơn cho bàn đang có khách thì báo bàn đã có đơn | `OrderFlowIntegrationTest.tableHasAtMostOneOpenOrder` |
| TC-08.2 | Gửi món thì món vào cột Chờ làm ở bếp | `OrderFlowIntegrationTest.staffDishGoesThroughKitchenToTable`; ≤ 2 giây: E2E |
| TC-08.3 | Món hết không chọn được | `OrderFlowIntegrationTest.soldOutDishCannotBeOrdered`, `MenuPicker.test.tsx`, `CartPanel.test.tsx` |
| TC-09.1 | Khách gửi món thì sơ đồ bàn hiện "chờ xác nhận" | E2E |
| TC-09.2 | Món chờ xác nhận không hiện ở bếp | `GuestQrIntegrationTest.guestDishesWaitForStaffConfirmationBeforeTheKitchen` |
| TC-09.3 | Xác nhận thì món vào bếp; từ chối thì khách thấy lý do | `GuestQrIntegrationTest.guestDishesWaitForStaffConfirmationBeforeTheKitchen`, `rejectedDishShowsTheReasonToTheGuest` |
| TC-09.4 | Máy phục vụ kêu khi khách gửi món | `KitchenAlertIntegrationTest.kitchenRingsOnlyForDishesThatReachIt`, `sound.test.ts`; tiếng kêu: kiểm tra tay |
| TC-10.1 | Bếp bấm Xong thì sơ đồ bàn hiện "có món xong" | `OrderFlowIntegrationTest.staffDishGoesThroughKitchenToTable` |
| TC-10.2 | Bấm Đã ra thì món rời cột Xong của bếp | `OrderFlowIntegrationTest.staffDishGoesThroughKitchenToTable` |
| TC-10.3 | Máy phục vụ kêu khi bếp bấm Xong | `KitchenAlertIntegrationTest.waitersHearWhenTheKitchenFinishesADish`; tiếng kêu: kiểm tra tay |
| TC-11.1 | Món Chờ làm: phục vụ huỷ được | `OrderFlowIntegrationTest.whoMayCancelDependsOnHowFarTheDishWent` |
| TC-11.2 | Món Đang làm, Xong: chỉ quản lý huỷ, phải có lý do | `OrderFlowIntegrationTest.whoMayCancelDependsOnHowFarTheDishWent` |
| TC-11.3 | Món đã ra không huỷ được | `OrderFlowIntegrationTest.whoMayCancelDependsOnHowFarTheDishWent`, `ItemStatusTest.canBeCancelledUntilServed` |
| TC-12.1 | Món sắp theo thời gian gửi, chờ lâu nhất ở trên | Kiểm tra tay trên màn hình bếp |
| TC-12.2 | Trạng thái chỉ đi tiến | `OrderFlowIntegrationTest.statusCannotSkipSteps`, `ItemStatusTest.movesForwardOneStepAtATime` |
| TC-12.3 | Món chờ tới ngưỡng ở Cài đặt thì tô đỏ | `KitchenAlertIntegrationTest.adminSetsTheLateDishThresholdThatTheKitchenReads`, `format.test.ts` (`minutesSince`) |
| TC-12.4 | Bếp kêu khi có món mới; món QR chỉ kêu khi đã xác nhận | `KitchenAlertIntegrationTest.kitchenRingsOnlyForDishesThatReachIt` |
| TC-13.1 | Báo hết thì món rời thực đơn của khách, bị khoá trên máy phục vụ | `GuestQrIntegrationTest.guestMenuHidesSoldOutDishes`, `OrderFlowIntegrationTest.soldOutDishCannotBeOrdered` |
| TC-14.1 | Quét QR mở trang có tên bàn, không cần đăng nhập | E2E; `GuestQrIntegrationTest.unknownQrCodeIsRejected` |
| TC-14.2 | Món khách gửi hiện "Chờ xác nhận" | `GuestQrIntegrationTest.guestDishesWaitForStaffConfirmationBeforeTheKitchen` |
| TC-14.3 | Bàn đang có đơn thì món mới vào đơn đó | `GuestQrIntegrationTest.laterSubmissionsJoinTheSameOrder` |
| TC-14.4 | Bàn đã có 30 món chờ thì khách phải chờ nhân viên | `RateLimitIntegrationTest.aTableKeepsAtMostThirtyDishesWaitingForConfirmation` |
| TC-14.5 | Một bàn gửi quá 10 lần mỗi phút thì bị chặn; bàn khác không ảnh hưởng | `RateLimitIntegrationTest.aTableSendsAtMostTenRequestsAMinute` |
| TC-15.1 | Điện thoại khách tự cập nhật khi bếp đổi trạng thái | E2E |
| TC-15.2 | Trang khách chỉ hiện đơn của bàn đó, không có tên nhân viên | `GuestQrIntegrationTest.guestViewShowsNoStaffInformation` |
| TC-16.1 | Nút Thanh toán chỉ bật khi không còn món chờ xác nhận | `PaymentIntegrationTest.noPaymentWhileGuestDishesAwaitConfirmation` |
| TC-16.2 | Mã VietQR đúng số tiền, nội dung là mã thanh toán | `PaymentIntegrationTest.guestPaysFromThePhoneAndTheTableIsFreed`, `TransferQr.test.tsx` |
| TC-16.3 | Ngân hàng báo có đúng tiền thì trang hiện "Đã thanh toán", bàn trống | `PaymentIntegrationTest.guestPaysFromThePhoneAndTheTableIsFreed`, E2E |
| TC-17.1 | Tiền khách đưa nhỏ hơn tổng thì không cho xác nhận | `PaymentIntegrationTest.cashMustCoverTheTotalAndClosesTheTable` |
| TC-17.2 | Thu xong thì đơn đóng, bàn trống | `PaymentIntegrationTest.cashMustCoverTheTotalAndClosesTheTable` |
| TC-18.1 | Webhook đúng mã, đúng tiền thì thu ngân thấy "Đã nhận tiền" | `PaymentIntegrationTest.transferIsConfirmedAutomaticallyByTheWebhook`, E2E |
| TC-18.2 | Webhook gửi lặp một giao dịch thì chỉ ghi một lần | `PaymentIntegrationTest.transferIsConfirmedAutomaticallyByTheWebhook` |
| TC-18.3 | Webhook sai API key bị từ chối (401) | `PaymentIntegrationTest.transferIsConfirmedAutomaticallyByTheWebhook` |
| TC-19.1 | Chuyển sai số tiền thì vào "Không khớp", đơn chưa đóng | `PaymentIntegrationTest.wrongAmountIsLeftForTheCashierToConfirm` |
| TC-19.2 | Xác nhận tay ghi tên người xác nhận | `PaymentIntegrationTest.wrongAmountIsLeftForTheCashierToConfirm` |
| TC-19.3 | Webhook lỗi 3 lần liên tiếp thì thu ngân thấy cảnh báo, kèm lý do và lúc bắt đầu | `WebhookAlertIntegrationTest.threeFailuresInARowWarnTheCashierUntilADeliveryWorks` |
| TC-19.4 | Có lại webhook hợp lệ thì cảnh báo tắt | `WebhookAlertIntegrationTest.threeFailuresInARowWarnTheCashierUntilADeliveryWorks` |

## 13.4 Kiểm thử tải (NFR-02)

**Cách đo:**
- k6 2.3.0 chạy trong Docker, kịch bản `perf/load-test.js`, cách chạy ở README.
- 30 người dùng cùng lúc trong 2 phút: 14 khách gọi món qua QR, 10 phục vụ, 3 bếp, 2 thu ngân, 1 quản lý xem báo cáo 30 ngày.
- Dữ liệu: nạp 6 tháng bán hàng bằng `perf/seed-history.sql` (21.481 đơn).
- Máy: Windows 11, Docker Desktop. Đo ngày 02/10/2026, sau khi đã có đủ các tính năng giai đoạn 4.

| Request | p95 |
|---|---|
| Toàn bộ | **23,8 ms** (ngưỡng 500 ms) |
| `GET /reports/summary` (báo cáo 30 ngày) | 63,8 ms |
| `POST /public/tables/{qr}/items` (khách gửi món) | 41,4 ms |
| `POST /orders/{id}/confirm-pending` | 26,0 ms |
| `GET /orders` | 25,8 ms |
| `GET /tables` | 22,1 ms |
| `GET /kitchen/items` | 20,9 ms |
| `PATCH /order-items/{id}/status` | 20,5 ms |
| `GET /public/tables/{qr}` | 18,2 ms |
| `GET /orders/{id}` | 16,1 ms |
| `GET /public/menu` | 15,7 ms |

Kết quả:
- 2.281 request, **0% lỗi**; 717/717 kiểm tra trong kịch bản đạt; 1.392 vòng lặp.
- So với lần đo ở P3-03 (p95 19,5 ms), thời gian tăng nhẹ sau khi thêm hoá đơn điện tử lúc thanh toán và đơn app. Vẫn dưới ngưỡng hơn 20 lần.
- Vài request có `max` khoảng 55 giây. Đó là đồng hồ máy ảo của Docker Desktop nhảy (README có ghi), không phải máy chủ chậm; p95 và số lỗi không bị ảnh hưởng.

## 13.5 Một số lỗi tìm thấy khi kiểm thử

| Lỗi | Tìm thấy khi | Cách sửa |
|---|---|---|
| Khách vừa bấm từ chối nhận tin vẫn được coi là đồng ý, vì đồng hồ container lùi khoảng 56 giây giữa hai lần bấm | Chạy thử P4-02 bằng Playwright | Trạng thái theo thứ tự ghi, không so hai thời điểm (BR-44). Thêm test chỉnh lùi đồng hồ một giờ (PR #80) |
| Production tắt tài khoản demo mà CSDL mới không có nhân viên nào, nên không ai đăng nhập được | Rà phần triển khai | Tài khoản quản trị đầu tiên từ `APP_INITIAL_ADMIN_PASSWORD` (BR-48), có test (PR #83) |
| Trang Thực đơn tràn ngang trên điện thoại | Chạy thử P1-07 ở khổ 375 px | Bảng danh mục và bảng món cuộn ngang bên trong (PR #79) |
| Tệp `.xlsx` thiếu kiểu mặc định, openpyxl cảnh báo | Mở thử tệp xuất bằng openpyxl ở chế độ coi cảnh báo là lỗi | Thêm `cellStyles` (PR #81) |
| Bảng loại thuế chật; cột thao tác của hàng chờ hoá đơn bị khuất khi cuộn ngang | Xem ảnh chụp lúc chạy thử P4-03 | Gọn còn 3 cột, ghim cột thao tác bên phải (PR #81) |

## 13.6 Việc còn lại

- **Kiểm tra tay trên thiết bị thật:**
  - Tiếng kêu ở máy phục vụ và bếp (TC-09.4, TC-10.3, TC-12.4).
  - Cập nhật trong ≤ 2 giây giữa hai máy (mục tiêu G2).
  - In phiếu 80 mm trên máy in thật.
- **Sau khi có máy chủ** ([tài liệu 11](11-trien-khai-van-hanh.md)):
  - Chuyển thật 2.000 đ qua SePay (P0-03).
  - Thử khôi phục một bản sao lưu (P0-04).
  - Đo tải lại trên máy chủ thật.
- Kế toán nhập thử một ngày dữ liệu hoá đơn vào MISA (P4-03).
