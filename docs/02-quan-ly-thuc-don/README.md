# Phân hệ 2 – Quản lý thực đơn (RMS Core)

Tài liệu phân tích phân hệ 2 của hệ thống quản lý nhà hàng (RMS): sơ đồ use case, sơ đồ hoạt động và ERD. Nguồn sơ đồ là các file `.puml` (PlantUML) cùng thư mục; ảnh `.png` được render từ các file đó.

Nguyên tắc thiết kế: công thức món được thiết lập một lần; hằng ngày chỉ cập nhật món bán và số suất; hệ thống tự tính toán và đồng bộ.

## 1. Phạm vi

Actor chính: Manager (Quản lý nhà hàng). Phân hệ liên kết với Quản lý kho (phân hệ 4), Quản lý đơn hàng (phân hệ 3), POS điều phối và bộ phận bếp.

| STT | Chức năng | Mô tả |
| --- | --- | --- |
| 1 | Quản lý danh mục món ăn | Thêm, sửa, phân loại món, giá bán, hình ảnh, trạng thái kinh doanh |
| 2 | Quản lý công thức định lượng (Recipe) | Khai báo nguyên liệu và số lượng cho một suất; chỉ chỉnh sửa khi công thức thay đổi |
| 3 | Quản lý thực đơn hằng ngày | Chọn món được bán, nhập hạn mức suất, sao chép thực đơn ngày trước, công bố và đóng thực đơn |
| 4 | Tự động tính toán nguyên liệu và suất khả dụng | Tính nhu cầu nguyên liệu dự kiến, đối chiếu kho và số suất còn được nhận |
| 5 | Quản lý trạng thái bán theo thời gian thực | Tự động khóa món hết suất hoặc thiếu nguyên liệu, cho phép tạm ngừng bán theo thông báo của bếp, đồng bộ với POS và Mobile |

**Phân chia trách nhiệm:**

| Bộ phận | Trách nhiệm | Trong sơ đồ |
| --- | --- | --- |
| Manager | Quản lý món, công thức, thực đơn và hạn mức suất | Actor Manager |
| Bếp trưởng | Trao đổi với Manager về số suất có thể chuẩn bị; báo thay đổi thực tế | Không có tài khoản, không phải actor; báo qua bộ đàm |
| POS điều phối | Tiếp nhận đơn, in phiếu bếp, cập nhật chế biến, tạm ngừng bán khi cần | Actor Order Coordinator |
| Nhân viên phục vụ | Chỉ được chọn món đang cho phép nhận đơn | Actor Waiter |
| Hệ thống | Tính nguyên liệu, giữ suất, cập nhật khả dụng, khóa món, đồng bộ trạng thái | Xử lý nội bộ, mô tả trong sơ đồ hoạt động; không phải actor |

**Ngoài phạm vi Core:** AI dự báo thực đơn, quản lý sơ chế nhiều cấp, tối ưu lợi nhuận, tự động mua hàng, tự động đóng thực đơn theo giờ.

**Luồng cốt lõi:** thiết lập công thức một lần → cập nhật số suất hằng ngày → kiểm tra nguyên liệu → công bố thực đơn → nhận đơn → giữ suất → chế biến và trừ kho → tự động ngừng bán khi hết khả năng phục vụ → Manager đóng thực đơn khi ngừng nhận món trong ngày.

**Kịch bản demo (Thực đơn – POS – Kho):** 1 laptop, 2 điện thoại; bếp dùng phiếu in xem trước và giao tiếp bằng lời.

1. Món Phở bò có công thức 100g thịt bò mỗi suất, kho còn 500g thịt bò.
2. Manager sao chép thực đơn ngày trước, đặt hạn mức Phở bò hôm nay là 3 suất.
3. Hệ thống tính cần 300g thịt bò, báo đủ nguyên liệu.
4. Phục vụ A gửi 2 suất, POS chấp nhận; còn 1 suất có thể nhận.
5. Phục vụ B gửi suất cuối; nút chọn Phở bò trên các thiết bị tự động bị vô hiệu (`UNAVAILABLE`, lý do `SOLD_OUT`).
6. POS chuyển món xuống bếp, ghi nhận bắt đầu chế biến; kho giảm từ 500g xuống 200g.
7. POS cập nhật món sẵn sàng, phục vụ nhận thông tin và mang món ra bàn.
8. Từ chối một dòng món khác để chứng minh hoàn suất; thử hai yêu cầu đặt suất cuối cùng lúc để kiểm tra chống vượt hạn mức.

## 2. Quy tắc nghiệp vụ

1. Không nhập lại công thức mỗi ngày; công thức được tái sử dụng cho đến khi có thay đổi.
2. Mỗi ngày Manager cập nhật danh sách món và hạn mức suất, có thể sao chép cấu hình từ ngày trước.
3. Lập và công bố thực đơn không trừ kho và không đặt giữ nguyên liệu cho kế hoạch ngày; hệ thống chỉ tính nhu cầu nguyên liệu để cảnh báo.
4. Khi phục vụ gửi đơn hợp lệ, hệ thống tạm giữ suất (dòng món `PENDING`) để hai nhân viên không cùng đặt vượt số suất còn lại.
5. POS chấp nhận dòng món thì kho đặt giữ nguyên liệu (`ACCEPTED`); chưa ghi nhận tiêu hao thực tế.
6. Chỉ khi POS ghi nhận bếp bắt đầu chế biến (`COOKING`) kho mới trừ nguyên liệu theo công thức. POS duyệt món hay chuyển bếp (`IN_KITCHEN`) không phải là bắt đầu chế biến. Nếu lúc này kho không cấp đủ nguyên liệu (lô đã giữ hỏng, hết hạn, không bù được), dòng món giữ `IN_KITCHEN`, được đánh dấu ngoại lệ thiếu nguyên liệu và POS xử lý cùng bếp, không tự hủy món (phân hệ 4, INV-20; phân hệ 3, ORD-18).
7. Món không nhận thêm yêu cầu hiển thị `UNAVAILABLE` và bị vô hiệu nút gọi món trên mọi thiết bị. Mã lý do chỉ giải thích nguyên nhân, không phải ba trạng thái lưu độc lập; thứ tự ưu tiên: `PAUSED` (POS tạm ngừng) → `SOLD_OUT` (hết hạn mức ngày) → `OUT_OF_STOCK` (không đủ nguyên liệu cho một suất).
8. POS có thể tạm ngừng bán thủ công khi bếp báo hết món, thiếu đồ sơ chế hoặc không thể phục vụ.
9. Dòng món bị từ chối hoặc hủy hợp lệ được hoàn suất đã giữ và giải phóng nguyên liệu đã đặt giữ. Hủy theo trạng thái thống nhất với phân hệ 3: hủy thông thường khi `PENDING` hoặc `ACCEPTED` (ORD-05); hủy ngoại lệ khi `IN_KITCHEN` có bếp xác nhận (ORD-11); `COOKING` hoặc `READY` không hoàn nguyên liệu tự động (ORD-12).
10. Suất được chấp nhận hoặc từ chối theo cả dòng món, không nhận một phần số lượng khách đặt.
11. Được công bố thực đơn khi nguyên liệu không đủ cho kế hoạch bán cả ngày, kèm cảnh báo thiếu nguyên liệu; hệ thống chỉ cho nhận số suất thực sự khả dụng tại thời điểm đặt. Không bắt Manager nhập hàng xong mới được công bố.
12. Khi nguyên nhân được giải quyết (kho bổ sung, dòng món bị từ chối hoặc hủy, POS mở bán lại), hệ thống tự tính lại khả dụng; món được mở lại nếu chưa hết hạn mức ngày và không bị POS tạm ngừng.
13. Nhiều món dùng chung nguyên liệu nên số suất khả dụng chỉ là khả năng hiện tại; backend vẫn kiểm tra và đặt giữ nguyên liệu trong transaction khi POS chấp nhận món.
14. Manager đóng thực đơn ngày khi nhà hàng ngừng nhận món trong ngày: thực đơn không nhận yêu cầu gọi món mới; các món đã được tiếp nhận hợp lệ vẫn tiếp tục chế biến, phục vụ và thanh toán. Không tự động đóng lúc 00:00 và không xóa thực đơn cũ.
15. Thực đơn ngày gắn với `business_date` (ngày kinh doanh), có thể kéo dài qua nửa đêm, không mặc định trùng ngày lịch. Ngày kinh doanh này cũng là mốc để phân hệ 3 gán cho khoản thanh toán và phân hệ 5 tổng hợp thu, chi, đối soát tiền mặt.

## 3. Sơ đồ use case

![Sơ đồ use case phân hệ 2](usecase.png)

Actor gồm Manager, Waiter và Order Coordinator. Bếp trưởng không có tài khoản phần mềm nên không phải actor. Restaurant Core là hệ thống đang được thiết kế, không phải tác nhân bên ngoài, nên các thao tác tự động (tính nguyên liệu, tính khả dụng, giữ suất, khóa hoặc mở món, đồng bộ POS và Mobile) được mô tả là xử lý nội bộ trong cột mô tả và trong sơ đồ hoạt động. Điều kiện tiên quyết của mọi use case: người dùng đã đăng nhập.

| Mã | Use case | Actor | Mô tả |
| --- | --- | --- | --- |
| UC01 | Thêm món ăn | Manager | Nhập tên, loại món, giá bán, hình ảnh, trạng thái kinh doanh |
| UC02 | Sửa món ăn | Manager | Sửa thông tin món, nhất là giá bán và hình ảnh |
| UC03 | Thêm hoặc sửa loại món | Manager | Thêm loại món; sửa tên, thứ tự sắp xếp hoặc ngừng dùng loại món |
| UC04 | Bật hoặc tắt kinh doanh món | Manager | Cho phép hoặc ngừng đưa món vào thực đơn |
| UC05 | Khai báo công thức món | Manager | Chọn nguyên liệu và định lượng cho một suất (phiên bản đầu tiên) |
| UC06 | Cập nhật công thức | Manager | Chỉ khi công thức thay đổi: tạo phiên bản mới, giữ phiên bản cũ |
| UC07 | Lập thực đơn ngày | Manager | Chọn món, nhập hạn mức suất, lưu bản nháp; hệ thống tự tính nhu cầu nguyên liệu và đối chiếu kho |
| UC08 | Sao chép thực đơn ngày trước | Manager | Mở rộng UC07: bản nháp lấy món và hạn mức của ngày trước, dùng công thức hiện hành |
| UC09 | Công bố thực đơn ngày | Manager | `DRAFT` → `PUBLISHED`; được công bố dù còn cảnh báo thiếu nguyên liệu; không đặt giữ nguyên liệu (quy tắc 3, 11) |
| UC10 | Đóng thực đơn ngày | Manager | `PUBLISHED` → `CLOSED` khi ngừng nhận món trong ngày; món đã tiếp nhận vẫn xử lý tiếp (quy tắc 14) |
| UC11 | Xem cảnh báo nguyên liệu | Manager | Xem nhu cầu nguyên liệu dự kiến so với tồn kho và các nguyên liệu thiếu |
| UC12 | Xem món đang bán và số suất còn nhận | Waiter, Order Coordinator | Món `AVAILABLE` hiển thị số suất còn nhận; món `UNAVAILABLE` hiển thị mã lý do và bị vô hiệu nút gọi món |
| UC13 | Tạm ngừng bán món | Order Coordinator | Theo thông báo của bếp, nhập lý do: món `UNAVAILABLE` (`PAUSED`) |
| UC14 | Mở bán lại món | Order Coordinator | Bỏ tạm ngừng; hệ thống tính lại khả dụng (quy tắc 12) |

**Quan hệ giữa các use case:** chỉ có UC08 «extend» UC07, vì sao chép thực đơn ngày trước là cách tùy chọn để khởi tạo bản nháp khi lập thực đơn ngày.

**Ranh giới hệ thống:** khung nét liền "Restaurant Core – Phân hệ 2" là ranh giới hệ thống; các khung nét đứt chỉ nhóm use case theo chức năng, không phải hệ thống riêng. Actor đứng ngoài khung và nối với use case bằng đường liên kết không mũi tên.

## 4. Sơ đồ hoạt động

Mỗi chức năng có ít nhất một sơ đồ; chức năng 5 có hai sơ đồ vì gồm hai tình huống khác nhau (đơn hàng và tạm ngừng thủ công).

| Chức năng | Sơ đồ |
| --- | --- |
| 1 Danh mục món ăn | 4.1 |
| 2 Công thức định lượng | 4.2 |
| 3 Thực đơn hằng ngày | 4.3 |
| 4 Tính nguyên liệu và suất khả dụng | 4.4 (và phần tính nhu cầu nguyên liệu trong 4.3) |
| 5 Trạng thái bán thời gian thực | 4.5, 4.6 |

### 4.1 Quản lý danh mục món ăn

![Sơ đồ hoạt động quản lý danh mục món ăn](activity-1-danh-muc.png)

- Thêm và sửa món dùng chung một biểu mẫu; nếu cần loại món mới thì tạo ngay trước khi nhập món.
- Bật hoặc tắt kinh doanh là thao tác riêng, chỉ đổi trạng thái kinh doanh của món.

### 4.2 Quản lý công thức định lượng

![Sơ đồ hoạt động quản lý công thức định lượng](activity-2-cong-thuc.png)

- Nếu món đã có công thức, hệ thống hiển thị công thức hiện hành làm cơ sở chỉnh sửa.
- Lưu thành phiên bản mới, đặt là hiện hành và giữ lại các phiên bản cũ để thực đơn ngày và đơn hàng cũ vẫn tham chiếu đúng công thức đã áp dụng.
- Nguyên liệu được chọn từ danh mục nguyên liệu đang sử dụng của phân hệ Kho.

### 4.3 Quản lý thực đơn hằng ngày

![Sơ đồ hoạt động quản lý thực đơn hằng ngày](activity-3-thuc-don-ngay.png)

- Bắt đầu từ bản sao thực đơn ngày trước hoặc bản nháp trống; Manager chọn món và nhập hạn mức suất.
- Mỗi lần chọn món hoặc nhập hạn mức, hệ thống kiểm tra món đang kinh doanh và có công thức hiện hành, hạn mức không âm, không trùng món; sai thì báo lỗi và giữ nguyên bản nháp.
- Hệ thống tính nhu cầu nguyên liệu của cả thực đơn và đối chiếu tồn kho, hiển thị cảnh báo nếu thiếu.
- Manager có thể giảm suất hoặc bổ sung kho rồi tính lại, hoặc công bố ngay dù còn cảnh báo (quy tắc 11).
- Khi Manager xác nhận công bố, hệ thống kiểm tra lại bản nháp (món còn kinh doanh, hạn mức hợp lệ); sai thì từ chối công bố.
- Công bố chuyển `PUBLISHED`, không đặt giữ nguyên liệu; POS và Mobile chỉ nhận số suất khả dụng tại thời điểm đặt. Khi ngừng nhận món trong ngày, Manager đóng thực đơn (`CLOSED`, quy tắc 14).

### 4.4 Tính khả dụng của món

![Sơ đồ hoạt động tính khả dụng của món](activity-4-suat-kha-dung.png)

- Món không có trong thực đơn đang `PUBLISHED` thì không hiển thị để gọi; thực đơn `CLOSED` không nhận yêu cầu mới.
- Thứ tự xét lý do: POS tạm ngừng → `PAUSED`; hết hạn mức → `SOLD_OUT`; không đủ nguyên liệu cho một suất → `OUT_OF_STOCK`; còn lại là `AVAILABLE`.
- Tính lại khi có đơn mới, khi dòng món bị từ chối hoặc hủy, khi kho thay đổi (nhập, tiêu hao, kiểm kê, lô không sử dụng được).

### 4.5 Xử lý yêu cầu gọi món: suất và nguyên liệu

![Sơ đồ hoạt động xử lý yêu cầu gọi món](activity-5-xu-ly-don.png)

Sơ đồ liên phân hệ (Thực đơn – Đơn hàng – Kho) với các mốc riêng: gửi yêu cầu, POS duyệt từng dòng, chuyển bếp, bắt đầu chế biến và hoàn thành.

- Gửi yêu cầu: đủ suất cho cả dòng thì `PENDING` và giữ tạm suất.
- POS duyệt: từ chối thì `REJECTED` và hoàn suất; chấp nhận thì kho đặt giữ nguyên liệu, thành công là `ACCEPTED`.
- Chuyển bếp: `IN_KITCHEN`, tạo phiếu bếp; chưa tiêu hao nguyên liệu.
- Bắt đầu chế biến: `COOKING`, kho trừ nguyên liệu theo công thức. Hoàn thành: `READY`, thông báo Waiter.
- Chi tiết đặt giữ theo FEFO và tiêu hao: xem phân hệ 4; hủy theo trạng thái: xem quy tắc 9 và sơ đồ 4.8 của phân hệ 3.

### 4.6 Tạm ngừng bán và mở bán lại món

![Sơ đồ hoạt động tạm ngừng bán và mở bán lại món](activity-6-tam-ngung-ban.png)

- Bếp báo qua bộ đàm; POS chọn món, nhập lý do và tạm ngừng bán, hoặc bỏ tạm ngừng khi bếp phục vụ lại được.
- Sau mỗi thao tác hệ thống tính lại khả dụng (`AVAILABLE` hoặc `UNAVAILABLE` kèm mã lý do) và đồng bộ để Waiter thấy ngay trên điện thoại.

## 5. ERD

![ERD phân hệ 2](erd.png)

Phân hệ sở hữu 6 bảng, vẽ viền liền, đủ cột và ràng buộc. Bảng của phân hệ khác vẽ viền nét đứt và chỉ hiện khóa cùng các cột phân hệ 2 dùng: `ingredients`, `inventory_batches` (phân hệ 4) và `order_items` (phân hệ 3). Đó là cùng một bảng trong cơ sở dữ liệu chung, định nghĩa đầy đủ ở README phân hệ sở hữu và [ERD tổng thể](../erd-tong-the.png); phân hệ 2 không tạo bảng mới. Mọi đường nối có chân chim là khóa ngoại thật, bản số theo NOT NULL và UNIQUE của cột khóa ngoại.

**Quy ước toàn hệ thống:** tên bảng và cột `snake_case`, tên bảng số nhiều; khóa chính `id BIGINT`; khóa ngoại `<thực thể>_id BIGINT` (ví dụ `menu_category_id`); chỉ khóa ngoại mới có hậu tố `_id`. Thời điểm dùng `TIMESTAMPTZ`, ngày dùng `DATE`; tiền dùng `DECIMAL(12,2)`, số lượng dùng `DECIMAL(12,3)`, không dùng `FLOAT` hay `DOUBLE`.

| Phân hệ | Bảng sở hữu |
| --- | --- |
| Quản lý thực đơn | `menu_categories`, `menu_items`, `recipes`, `recipe_ingredients`, `daily_menus`, `daily_menu_items` |
| Quản lý kho | `ingredients`, `inventory_batches`, `stock_receipts`, `stock_receipt_items`, `stock_reservations`, `stock_movements`, `stock_counts`, `stock_count_items` (xem phân hệ 4) |
| Quản lý đơn hàng | `table_sessions`, `orders`, `order_batches`, `order_items` và các bảng khác (xem phân hệ 3) |

Các bảng nằm trong cơ sở dữ liệu chung, nhưng mỗi phân hệ chỉ cập nhật dữ liệu mà nó chịu trách nhiệm.

**Quyết định thiết kế quan trọng:**

- `recipes` lưu phiên bản công thức: tháng này phở bò dùng 100g thịt, tháng sau đổi 120g thì đơn cũ vẫn tham chiếu đúng công thức đã áp dụng.
- `menu_items` là món tồn tại lâu dài; `daily_menu_items` là món được bán trong một ngày kinh doanh cụ thể kèm hạn mức suất, nên một món xuất hiện nhiều ngày với số suất khác nhau.
- Không có cột `remaining_servings` và không lưu trạng thái khả dụng: số suất còn nhận và mã lý do được tính từ hạn mức ngày, dòng món đang chờ hoặc đã chấp nhận, tồn khả dụng của kho và cờ tạm ngừng.
- Công thức định lượng chỉ nằm ở phân hệ 2; kho đọc `recipe_ingredients` qua `daily_menu_items.recipe_id` để đặt giữ và tiêu hao, không tạo bản sao công thức.
- `daily_menu_items` giữ cả `menu_item_id` và `recipe_id`: `menu_item_id` cần cho UNIQUE (`daily_menu_id`, `menu_item_id`); khóa ngoại ghép (`recipe_id`, `menu_item_id`) → `recipes` (`id`, `menu_item_id`) bảo đảm công thức thuộc đúng món của dòng.
- Liên kết sang đơn hàng chỉ qua `order_items.daily_menu_item_id`.

**Quan hệ giữa các bảng:**

| Quan hệ | Khóa ngoại | Nhãn |
| --- | --- | --- |
| menu_categories 1 – N menu_items | `menu_items.menu_category_id` | contains |
| menu_items 1 – N recipes | `recipes.menu_item_id` | has |
| recipes 1 – N recipe_ingredients | `recipe_ingredients.recipe_id` | contains |
| ingredients 1 – N recipe_ingredients | `recipe_ingredients.ingredient_id` | used_in |
| daily_menus 1 – N daily_menu_items | `daily_menu_items.daily_menu_id` | includes |
| menu_items 1 – N daily_menu_items | `daily_menu_items.menu_item_id` | appears_in |
| recipes 1 – N daily_menu_items | `daily_menu_items.recipe_id` | applied_to |
| daily_menu_items 1 – N order_items | `order_items.daily_menu_item_id` | selected_from |

ERD còn vẽ khóa ngoại giữa hai bảng tham chiếu của phân hệ 4: `ingredients` 1 – N `inventory_batches` (`inventory_batches.ingredient_id`, nhãn stored_in), là đường lấy tồn khả dụng `A_i` cho công thức tính số suất.

### Từ điển dữ liệu (6 bảng thuộc phân hệ)

**menu_categories** – Loại món

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã loại món |
| name | varchar(100) | bắt buộc | Tên loại món |
| sort_order | int | bắt buộc | Thứ tự hiển thị |
| is_active | boolean | bắt buộc | Còn sử dụng |

**menu_items** – Món ăn

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã món |
| menu_category_id | bigint | FK → menu_categories, bắt buộc | Loại món |
| name | varchar(100) | bắt buộc | Tên món |
| price | decimal(12,2) | bắt buộc, ≥ 0 | Giá bán hiện tại; giá trên đơn là bản chụp `order_items.unit_price` (phân hệ 3) |
| image_url | varchar(255) | | Đường dẫn hình ảnh |
| is_active | boolean | bắt buộc | Trạng thái kinh doanh |

**recipes** – Công thức (theo phiên bản)

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã công thức |
| menu_item_id | bigint | FK → menu_items, bắt buộc | Món áp dụng |
| version | int | bắt buộc, ≥ 1 | Số phiên bản |
| is_current | boolean | bắt buộc | Phiên bản hiện hành |
| (menu_item_id, version) | | UK | Không trùng số phiên bản của một món |
| (menu_item_id) khi `is_current` | | UK một phần | Mỗi món chỉ một công thức hiện hành |
| (id, menu_item_id) | | UK | Đích của khóa ngoại ghép từ `daily_menu_items`; `id` đã duy nhất nên ràng buộc này không giới hạn thêm dữ liệu |

**recipe_ingredients** – Định lượng nguyên liệu

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã dòng định lượng |
| recipe_id | bigint | FK → recipes, bắt buộc | Công thức |
| ingredient_id | bigint | FK → ingredients, bắt buộc | Nguyên liệu (bảng của phân hệ Kho) |
| quantity_per_serving | decimal(12,3) | bắt buộc, > 0 | Lượng cho một suất, theo đơn vị cơ sở của nguyên liệu |
| (recipe_id, ingredient_id) | | UK | Một nguyên liệu không lặp trong một công thức |

**daily_menus** – Thực đơn ngày

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã thực đơn ngày |
| business_date | date | UK, bắt buộc | Ngày kinh doanh; mỗi ngày một thực đơn, có thể kéo dài qua nửa đêm |
| status | varchar(20) | bắt buộc | DRAFT, PUBLISHED, CLOSED |
| published_at | timestamptz | | Thời điểm công bố |

**daily_menu_items** – Món trong thực đơn ngày

| Cột | Kiểu | Ràng buộc | Ý nghĩa |
| --- | --- | --- | --- |
| id | bigint | PK | Mã dòng món trong ngày |
| daily_menu_id | bigint | FK → daily_menus, bắt buộc | Thực đơn ngày |
| menu_item_id | bigint | FK → menu_items, bắt buộc | Món |
| recipe_id | bigint | FK → recipes, bắt buộc | Phiên bản công thức áp dụng cho ngày đó |
| quota_total | int | bắt buộc, ≥ 0 | Hạn mức số suất của ngày |
| is_paused | boolean | bắt buộc | POS đang tạm ngừng bán |
| pause_reason | varchar(255) | | Lý do tạm ngừng |
| (daily_menu_id, menu_item_id) | | UK | Một món chỉ xuất hiện một lần trong thực đơn ngày |
| (recipe_id, menu_item_id) | | FK → recipes (id, menu_item_id) | Khóa ngoại ghép: công thức áp dụng phải thuộc đúng món của dòng |

### Bảng của phân hệ khác trong ERD

| Bảng | Phân hệ sở hữu | Vai trò đối với thực đơn | Cột hiện trong ERD |
| --- | --- | --- | --- |
| ingredients | Kho (phân hệ 4) | Danh mục nguyên liệu mà `recipe_ingredients` tham chiếu | `id`, `name`, `base_unit` |
| inventory_batches | Kho (phân hệ 4) | Tồn và lượng đặt giữ của lô, nguồn `A_i` khi tính số suất (chỉ đọc) | `id`, `ingredient_id`, `expiry_date`, `quantity_on_hand`, `reserved_quantity`, `is_usable` |
| order_items | Đơn hàng (phân hệ 3) | Dòng món chọn từ `daily_menu_items`; số lượng và trạng thái cho `Q_đã_giữ`, `Q_đã_nhận` | `id`, `daily_menu_item_id`, `quantity`, `status` |

Từ điển dữ liệu đầy đủ của ba bảng này nằm ở README phân hệ 4 và 3.

### Công thức tính số suất còn nhận

```text
Q_quota    = max(0, Q_hạn_mức − Q_đã_giữ − Q_đã_nhận)
Q_kho      = min theo từng nguyên liệu i của floor(A_i / R_i)
Q_khả_dụng = min(Q_quota, Q_kho)
```

- `Q_đã_giữ`: tổng số lượng các `order_items` đang `PENDING`.
- `Q_đã_nhận`: tổng số lượng các `order_items` ở `ACCEPTED`, `IN_KITCHEN`, `COOKING` hoặc `READY` (không tính `CANCELLED`, `REJECTED`).
- `A_i`: lượng khả dụng của nguyên liệu i do phân hệ 4 tính, `max(0, available_quantity)` với `available_quantity` = tồn sử dụng được trừ đặt giữ đang hoạt động (lô hết hạn, không đạt chất lượng không được tính); `R_i`: lượng nguyên liệu i cho một suất.

Món đang bị POS tạm ngừng, thực đơn chưa công bố hoặc đã đóng thì không nhận đặt. Với các món dùng chung nguyên liệu, `Q_kho` là khả năng tại thời điểm truy vấn, không phải cam kết dành riêng nguyên liệu; backend vẫn kiểm tra lại trong transaction khi chấp nhận món (quy tắc 13).

### Trạng thái

- Thực đơn ngày (`daily_menus.status`): `DRAFT` → `PUBLISHED` → `CLOSED`.
- Khả dụng của món trong thực đơn đã công bố (suy ra, không lưu): `AVAILABLE`, hoặc `UNAVAILABLE` kèm một mã lý do theo thứ tự ưu tiên `PAUSED` → `SOLD_OUT` → `OUT_OF_STOCK`. Món không có trong thực đơn ngày thì không hiển thị để gọi.
- Trạng thái dòng món (`order_items.status`) theo phân hệ 3: `PENDING`, `ACCEPTED`, `IN_KITCHEN`, `COOKING`, `READY`, `CANCELLED`, `REJECTED`.

### Chuẩn hóa và khóa ngoại

- **1NF, 2NF:** mọi cột một giá trị; mọi bảng có khóa chính một cột `id` nên không có phụ thuộc vào một phần khóa. Quan hệ nhiều – nhiều `recipes` – `ingredients` đi qua bảng nối `recipe_ingredients`; `quantity_per_serving` phụ thuộc vào cả cặp (`recipe_id`, `ingredient_id`), cặp này là UK. `daily_menus` – `menu_items` cũng là nhiều – nhiều qua `daily_menu_items`, bảng này mang thêm hạn mức và cờ tạm ngừng của ngày.
- **3NF:** dữ liệu của thực thể khác chỉ tham chiếu bằng khóa ngoại. Ngoại lệ có chủ đích:

| Thuộc tính | Vì sao giữ | Kiểm soát |
| --- | --- | --- |
| `daily_menu_items.menu_item_id` | Suy ra được từ `recipe_id` (qua `recipes.menu_item_id`), tức phụ thuộc bắc cầu. Giữ để có UNIQUE (`daily_menu_id`, `menu_item_id`): một món chỉ một lần trong thực đơn ngày | Khóa ngoại ghép (`recipe_id`, `menu_item_id`) → `recipes` (`id`, `menu_item_id`): không thể gắn công thức của món khác |

- Không lưu số suất còn lại hay trạng thái khả dụng: tính khi truy vấn theo công thức ở trên. Giá trên đơn là bản chụp ở phân hệ 3 (`order_items.unit_price`), nên `menu_items.price` chỉ là giá hiện tại.
- Mọi khóa ngoại `ON DELETE RESTRICT`: món, công thức, thực đơn ngày đã được tham chiếu không bị xóa cứng; ngừng dùng bằng `is_active`, thực đơn kết thúc bằng `CLOSED`.

```sql
-- Khóa ngoại ghép (PostgreSQL): công thức của dòng thực đơn ngày phải thuộc đúng món của dòng
ALTER TABLE recipes ADD CONSTRAINT uq_recipes_id_menu_item UNIQUE (id, menu_item_id);
ALTER TABLE daily_menu_items ADD CONSTRAINT fk_daily_menu_items_recipe_menu_item
  FOREIGN KEY (recipe_id, menu_item_id) REFERENCES recipes (id, menu_item_id) ON DELETE RESTRICT;
```

## 6. Giả định còn lại

1. Bản chốt phạm vi gốc đã mất mũi tên của sơ đồ Mermaid; luồng được dựng lại từ nhãn bước, quy tắc nghiệp vụ và các quyết định chuẩn hóa.
2. Sao chép thực đơn ngày trước dùng công thức hiện hành (`is_current`) tại thời điểm sao chép, không giữ lại phiên bản của ngày trước.
3. Ràng buộc bổ sung: mỗi món chỉ một công thức hiện hành; `recipe_id` của `daily_menu_items` phải thuộc đúng `menu_item_id` của dòng đó, bảo đảm bằng khóa ngoại ghép (mục 5, Chuẩn hóa và khóa ngoại).
4. Sau khi công bố, Core chưa mô tả việc Manager tăng hạn mức suất của món trong thực đơn đang bán.

## 7. Render lại sơ đồ

Cần Java và Graphviz. `plantuml.jar` có sẵn trong extension VS Code `jebbs.plantuml` (thư mục `%USERPROFILE%\.vscode\extensions\jebbs.plantuml-<phiên bản>\`). Chạy từ thư mục gốc dự án:

```powershell
java "-DPLANTUML_LIMIT_SIZE=8192" -jar "<đường dẫn plantuml.jar>" -charset UTF-8 -failfast2 -tpng docs\02-quan-ly-thuc-don\*.puml
```

Hoặc mở file `.puml` trong VS Code và nhấn Alt+D để xem trước. Style đen trắng dùng chung nằm ở `docs/_style.puml`.
