# Hướng dẫn phát triển

## 1. Chạy dự án

Cần: JDK 21, Docker (Compose v2). Maven không cần cài — dùng `./mvnw`.

```bash
cp .env.example .env                   # điền DB_ADMIN_PASSWORD, DB_APP_PASSWORD, JWT_SECRET (openssl rand -base64 48)
docker compose up -d db                # PostgreSQL 17 ở localhost:5433, tự nạp docs/04 → 09 → 10 lần đầu

# chạy app trên máy (đọc biến từ .env của shell, hoặc export tay)
set -a; source .env; set +a
export DB_URL=jdbc:postgresql://localhost:5433/restaurant_core FLYWAY_USER=$DB_ADMIN_USER FLYWAY_PASSWORD=$DB_ADMIN_PASSWORD
./mvnw spring-boot:run                 # http://localhost:8081, profile dev

# hoặc chạy tất cả bằng Docker
docker compose up -d --build
curl http://localhost:8081/actuator/health
```

**Tài khoản đầu tiên** (database mới chưa có ai đăng nhập được):

```bash
docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
    -v username=manager -v full_name='Quản lý' -v password='mat-khau-tam' < docker/bootstrap-manager.sql
curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"manager","password":"mat-khau-tam"}'
```

Làm lại database từ đầu: `docker compose down -v` (XÓA dữ liệu) rồi `docker compose up -d db`.

## 2. Thêm một API (theo mẫu module `order` của Phạm Duy An)

Ví dụ thật đã có: "gửi lượt gọi món" `POST /api/orders/{orderId}/batches`. Mỗi tầng một trách nhiệm:

```java
// order/dto/SubmitBatchRequest.java — record, Bean Validation, KHÔNG có actorId
public record SubmitBatchRequest(@NotBlank @Size(max = 64) String requestKey,
                                 @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items) {
    public record Item(@NotNull @Positive Long dailyMenuItemId, @NotNull @Positive Integer quantity, @Size(max = 255) String note) {}
}

// order/repository/OrderRepository.java — SQL ở đây, JdbcClient, tham số đặt tên
public String submitBatch(long orderId, long actorId, String requestKey, String itemsJson) {
    return jdbc.sql("SELECT rc_submit_batch(:o, :a, :k, CAST(:items AS jsonb))::text")   // hàm trả JSONB
            .param("o", orderId).param("a", actorId).param("k", requestKey).param("items", itemsJson)
            .query(String.class).single();
}

// order/service/OrderService.java — dựng JSON đầu vào, đọc JSON kết quả (giữ nguyên warnings), không try/catch lỗi DB
// order/controller/OrderController.java
@PostMapping("/{orderId}/batches")
@ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")          // khớp ma trận docs/07 §10
public ApiResponse<SubmitBatchResponse> submitBatch(@PathVariable @Positive long orderId, @Valid @RequestBody SubmitBatchRequest request) {
    return ApiResponse.ok(orderService.submitBatch(orderId, currentUser.employeeId(), request));
}
```

Quy tắc:

- **Controller**: URL, `@PreAuthorize`, `@Valid`, lấy `currentUser.employeeId()`, bọc `ApiResponse.ok(...)`. Không SQL.
- **Service**: nghiệp vụ phía Java (ghép dữ liệu, chọn strategy, gọi nhiều repository khi cần). Đặt trong package `..service..` để `PerformanceLoggingAspect` đo thời gian. Không cần interface.
- **Repository**: SQL bằng `JdbcClient`, tham số đặt tên (`:name`) — không nối chuỗi SQL. Map kết quả vào `record` (tên cột snake_case tự khớp field camelCase).
- **DTO**: `record` trong `<module>/dto`. Request không bao giờ chứa `actorId`, `employeeId` của người thao tác, tổng tiền phải thu hay trạng thái muốn đặt.

## 3. Gọi hàm `rc_*`

- Một hàm = một câu `SELECT rc_xxx(:a, :b)` = một transaction nguyên tử. **Không** thêm `@Transactional`, **không** gọi hàm rồi tự `UPDATE` bổ sung.
- Kiểu trả về theo chữ ký thật (kiểm bằng `\df rc_*` trong psql): `bigint` → `Long`, `text` → `String`, `jsonb` → ép `::text` rồi parse, `void` → `jdbc.sql(...).query().singleRow()` hoặc `.update()`.
- `p_actor` luôn là `currentUser.employeeId()`.
- Lỗi nghiệp vụ do DB ném (RC403/RC409/RC422/RC001) **để nguyên cho bay lên** — `GlobalExceptionHandler` đổi thành HTTP với thông điệp của DB.
- Khi thật sự cần nhiều câu lệnh trong một transaction Java (hiếm — ví dụ ghi danh mục nhiều bảng), dùng `@Transactional` ở Service và hỏi Leader trước.

## 4. Kiểm tra quyền

- `@PreAuthorize("hasAnyRole('…')")` trên **mọi** endpoint ngoài `/api/auth/login` và `/actuator/health`; vai trò lấy theo ma trận [07 §10](../07-kiem-toan-ham-rc.md). Không có annotation = mọi người đã đăng nhập đều gọi được.
- DB kiểm lại vai trò trong hàm `rc_*` (RC403). Bảng ghi trực tiếp (danh mục, nhân sự) không có lớp kiểm thứ hai — `@PreAuthorize` ở đó là bắt buộc.
- Không tự giải mã JWT; dùng `CurrentUser`.

## 5. Xử lý lỗi

- Lỗi do backend phát hiện: `throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy món")`.
- Lỗi validate: dùng annotation (`@NotBlank`, `@Positive`…) — tự thành 400 `VALIDATION_ERROR`.
- Không `catch (Exception)` trong Controller/Service để trả JSON. Mã lỗi đầy đủ: [API_CONVENTIONS](API_CONVENTIONS.md#mã-lỗi).

## 6. Viết test

- **Unit test** (`*Test.java`): logic Java thuần, không Spring, không DB (ví dụ `PostgresErrorTranslatorTest`).
- **Integration test** (`*IT.java`): kế thừa `IntegrationTestBase` — một PostgreSQL 17 Testcontainers dựng y như compose; app kết nối bằng tài khoản `rc_app`. Dựng dữ liệu bằng `TestData` (tài khoản quản trị), gọi API bằng `mvc` + `bearer(data.staff("WAITER"))`. Mỗi test tự tạo bàn/nhân viên mới, không phụ thuộc thứ tự.
- Mỗi endpoint mới cần ít nhất: 1 ca thành công, 1 ca sai vai trò (403), 1 ca lỗi nghiệp vụ từ DB (409/422).
- Chạy: `./mvnw test` (cần Docker đang chạy). Chỉ chạy một lớp: `./mvnw test -Dtest=TableIT`.

## SePay (webhook QR) — Phạm Duy An

1. `.env`: `SEPAY_API_KEY` (khóa đặt ở cấu hình webhook SePay, kiểu xác thực **API Key**), `SEPAY_BANK_ACCOUNT`, `SEPAY_BANK_NAME` (để dựng ảnh QR), `DB_WEBHOOK_USER`, `DB_WEBHOOK_PASSWORD`.
2. Login `rc_webhook` được `docker/db-init/40-app-logins.sh` tạo **khi khởi tạo volume** nếu có hai biến DB_WEBHOOK_*. Database đã khởi tạo trước đó → tạo tay bằng tài khoản quản trị (không cấp thêm quyền nào khác):
   ```bash
   docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
       -v u="$DB_WEBHOOK_USER" -v p="$DB_WEBHOOK_PASSWORD" <<< 'CREATE ROLE :"u" LOGIN PASSWORD :'"'"'p'"'"' IN ROLE rc_webhook;'
   ```
3. Trong SePay: URL webhook `https://<miền>/api/webhooks/sepay`, cấu hình nhận diện mã thanh toán theo tiền tố `RC` (mã dạng `RC<số hóa đơn>Q<số>`). Endpoint trả `{"success": true}` HTTP 200 cho mọi kết quả nghiệp vụ (kể cả lệch tiền) để SePay không gửi lại; chỉ lỗi xác thực/payload/hệ thống mới trả mã lỗi.
4. Thử local không cần SePay: `curl -X POST localhost:8081/api/webhooks/sepay -H "Authorization: Apikey $SEPAY_API_KEY" -H 'Content-Type: application/json' -d '{"id":1,"code":"RC1Q1","content":"RC1Q1","transferType":"in","transferAmount":50000}'`.

## Migration (Flyway)

| Tình huống | Cách làm |
| --- | --- |
| Database **mới** dựng bằng compose/Testcontainers | Schema đã đúng v4.1 → để `FLYWAY_BASELINE_ON_MIGRATE=true` (mặc định trong `.env.example`): lần chạy app đầu ghi baseline version 1, sau đó chạy V2+ |
| Database **đã có dữ liệu** dựng từ docs/04 + 09 + 10 | Giữ `FLYWAY_BASELINE_ON_MIGRATE=false` (mặc định, bắt buộc ở profile `prod`). App sẽ dừng với lỗi "non-empty schema without schema history table" → quản trị viên xác nhận schema đúng v4.1 rồi chạy app **một lần** với biến này = `true`, sau đó tắt lại |
| Thay đổi schema mới | File `src/main/resources/db/migration/V2__mo_ta.sql`, được nhóm duyệt; không sửa file đã chạy. Đối tượng mới phải `ALTER … OWNER TO rc_owner` và cấp quyền theo `docs/09-acl-v4.1.sql` (có thể chạy lại 09 — script idempotent) |
| Đổi ACL / role | Sửa `docs/09-acl-v4.1.sql` (cần quyền quản trị cụm), không đặt trong Flyway |

Flyway luôn chạy bằng `FLYWAY_USER` (quản trị), app chạy bằng `DB_APP_USER` (`rc_app`). Không bao giờ chạy lại docs/04 trên database có dữ liệu.

## 7. Không tự ý sửa (hỏi Leader)

`pom.xml` (thêm dependency), `application*.yml`, `security/*`, `common/*`, `compose.yaml`, `Dockerfile`, `docker/*`, `IntegrationTestBase`, `TestData` (được thêm hàm dựng dữ liệu, không sửa hàm có sẵn), mọi file trong `docs/` (database đã đóng băng).

## 8. Khi nào phải trao đổi với thành viên khác

- Cần đọc/ghi bảng của module khác → hỏi chủ module (bảng [MODULE_BOUNDARIES](MODULE_BOUNDARIES.md)) để dùng Service của họ.
- API của mình là đầu vào của module khác (ví dụ order → finance chốt hóa đơn; inventory → order cấp nguyên liệu).
- Phát hiện hàm `rc_*` hoạt động khác tài liệu, hoặc cần đổi schema → báo cả nhóm, không tự sửa DB.
