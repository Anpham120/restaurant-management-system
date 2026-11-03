# Hướng dẫn phát triển

## 1. Chạy dự án

Cần: JDK 25, Docker (Compose v2). Maven không cần cài — dùng `./mvnw`.

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
                                 @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items) { … }

// order/entity/OrderItem.java — @Entity ánh xạ bảng order_items (Lombok @Getter)
// order/repository/OrderItemRepository.java — interface extends JpaRepository<OrderItem, Long>
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM OrderItem i JOIN FETCH i.batch WHERE i.id = :id")
Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);

// order/service/OrderService.java — interface: danh sách nghiệp vụ
SubmitBatchResponse submitOrderBatch(long orderId, long actorId, SubmitBatchRequest request);

// order/service/impl/OrderServiceImpl.java — nghiệp vụ: khóa đơn, kiểm món đang bán, ghi món + nhật ký, cảnh báo hạn mức
@Override
@Transactional
public SubmitBatchResponse submitOrderBatch(long orderId, long actorId, SubmitBatchRequest request) { … }

// order/controller/OrderController.java
@PostMapping("/{orderId}/batches")
@ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasAnyRole('WAITER', 'MANAGER')")          // khớp ma trận docs/07 §10
public ApiResponse<SubmitBatchResponse> submitOrderBatch(@PathVariable @Positive long orderId, @Valid @RequestBody SubmitBatchRequest request) {
    return ApiResponse.ok(orderService.submitOrderBatch(orderId, currentUser.employeeId(), request));
}
```

Quy tắc:

- **Controller**: URL, `@PreAuthorize`, `@Valid`, lấy `currentUser.employeeId()`, bọc `ApiResponse.ok(...)`. Không SQL.
- **Service**: nghiệp vụ phía Java (ghép dữ liệu, chọn strategy, gọi nhiều repository khi cần). Đặt trong package `..service..` để `PerformanceLoggingAspect` đo thời gian. Không cần interface.
- **Repository**: ghi bằng interface `JpaRepository`; truy vấn chỉ đọc ghép nhiều bảng bằng `JdbcClient` trong `…QueryRepository`, tham số đặt tên (`:name`) — không nối chuỗi SQL. Map kết quả vào `record` (tên cột snake_case tự khớp field camelCase).
- **Ranh giới module** (Spring Modulith): chỉ import từ `common` và lớp ở package gốc của `security` (như `CurrentUser`). Không import `controller/service/repository/dto` của module khác. `ModularityTest` sẽ báo lỗi nếu vi phạm. Module mới cần `package-info.java` với `@ApplicationModule` ([ARCHITECTURE](ARCHITECTURE.md#module-spring-modulith)).
- **DTO**: `record` trong `<module>/dto`. Request không bao giờ chứa `actorId`, `employeeId` của người thao tác, tổng tiền phải thu hay trạng thái muốn đặt.

### Cấu trúc chuẩn của một module (mẫu: `employee`)

```text
employee/
├── controller/        EmployeeController            URL, @PreAuthorize, @Valid, gọi Service
├── dto/request/       CreateEmployeeRequest …       record + Bean Validation
├── dto/response/      EmployeeResponse …            record trả ra API
├── entity/            Employee, Account, Role …     @Entity ánh xạ bảng (Lombok @Getter/@Setter)
├── mapper/            EmployeeMapper                Entity → Response
├── repository/        EmployeeRepository …          interface extends JpaRepository
└── service/
    ├── EmployeeService                              interface: danh sách nghiệp vụ
    └── impl/EmployeeServiceImpl                     logic + @Transactional
```

- Mọi thao tác ghi: Entity + `JpaRepository`, nghiệp vụ trong `…ServiceImpl`. Truy vấn chỉ đọc ghép nhiều bảng cho màn hình: `…QueryRepository` (JdbcClient, trả thẳng DTO).
- Ghi xong thì `flush()` trong Service, để lỗi ràng buộc DB thành lỗi API ngay trong method.
- Sửa cùng một bản ghi đồng thời: đọc bằng method `…ForUpdate` (`@Lock(PESSIMISTIC_WRITE)`).

### Đặt tên hàm

Tên hàm nói rõ **làm gì với cái gì**: động từ + đối tượng, tiếng Anh, camelCase. Tìm theo chức năng là ra.

| Loại | Mẫu | Ví dụ |
| --- | --- | --- |
| Lấy danh sách | `getAll…`, `get…s` | `getAllDepartments()`, `getEmployees(status, page, size)` |
| Lấy một | `get…ById` | `getEmployeeById(id)` |
| Tạo / sửa / xóa | `create…`, `update…`, `delete…` | `createEmployee(request)`, `updateAccountRoles(...)` |
| Đổi trạng thái | động từ nghiệp vụ + đối tượng | `lockAccount`, `unlockAccount`, `markEmployeeAsLeft`, `resetAccountPassword` |
| Kiểm tra | `is…`, `has…`, `can…` | `hasLeft()` |
| Truy vấn khóa dòng | `find…ForUpdate` | `findAccountForUpdate(id)` |
| Tạo lỗi | `…Error` | `notFoundError(...)`, `selfLockoutError()` |

Không đặt tên chung chung (`process`, `handle`, `doAction`, `data`). Method controller trùng tên method service nó gọi.

## 3. Viết nghiệp vụ trong Service

- Method ghi: `@Transactional` ở `…ServiceImpl` (class để `@Transactional(readOnly = true)` cho method đọc).
- Đổi trạng thái qua method có tên nghiệp vụ trên entity (`invoice.lockForPayment(total)`, `batch.reserve(qty)`), không set trạng thái lung tung trong Service. Trạng thái món chỉ đổi qua `OrderItemStatusChanger` (kiểm bước chuyển + ghi nhật ký).
- Khóa dòng bằng `…ForUpdate` đúng thứ tự khóa chung ([ARCHITECTURE](ARCHITECTURE.md#nguyên-tắc-cốt-lõi)). Khóa trước rồi mới nạp entity.
- Mọi thay đổi tồn kho đi kèm một dòng `StockMovement` (sổ kho chỉ-thêm) — DB kiểm số dư khớp sổ lúc COMMIT.
- Ghi xong thì `flush()` trước khi ghi bản ghi phụ thuộc (ví dụ đổi trạng thái món rồi mới ghi nhật ký), để trigger bảo vệ của DB thấy đúng thứ tự.
- Lỗi nghiệp vụ: `throw ApiException.invalidState(...)` (409), `ApiException.invalidRequest(...)` (422), `ApiException.notFound(...)` (404), `ApiException.insufficientStock(...)` (409). Thông điệp tiếng Việt, hiển thị được cho người dùng.

## 4. Kiểm tra quyền

- `@PreAuthorize("hasAnyRole('…')")` trên **mọi** endpoint ngoài `/api/auth/login` và `/actuator/health`; vai trò lấy theo ma trận [07 §10](../07-kiem-toan-ham-rc.md). Không có annotation = mọi người đã đăng nhập đều gọi được.
- `@PreAuthorize` là lớp kiểm vai trò duy nhất ở API — bắt buộc trên mọi endpoint ghi.
- Không tự giải mã JWT; dùng `CurrentUser`.

## 5. Xử lý lỗi

- Lỗi do backend phát hiện: `throw ApiException.notFound("Không tìm thấy món " + id)` (xem mục 3).
- Lỗi validate: dùng annotation (`@NotBlank`, `@Positive`…) — tự thành 400 `VALIDATION_ERROR`.
- Không `catch (Exception)` trong Controller/Service để trả JSON. Mã lỗi đầy đủ: [API_CONVENTIONS](API_CONVENTIONS.md#mã-lỗi).

## 6. Viết test

- **Unit test** (`*Test.java`): logic Java thuần, không Spring, không DB (ví dụ `PostgresErrorTranslatorTest`).
- **Integration test** (`*IT.java`): kế thừa `IntegrationTestBase` — một PostgreSQL 17 Testcontainers dựng y như compose; app kết nối bằng tài khoản `rc_app`. Dựng dữ liệu bằng `TestData` (tài khoản quản trị), gọi API bằng `mvc` + `bearer(data.staff("WAITER"))`. Mỗi test tự tạo bàn/nhân viên mới, không phụ thuộc thứ tự.
- Mỗi endpoint mới cần ít nhất: 1 ca thành công, 1 ca sai vai trò (403), 1 ca lỗi nghiệp vụ (409/422).
- Chạy: `./mvnw test` (cần Docker đang chạy). Chỉ chạy một lớp: `./mvnw test -Dtest=TableIT`.

## SePay (webhook QR) — Phạm Duy An

1. `.env`: `SEPAY_API_KEY` (khóa đặt ở cấu hình webhook SePay, kiểu xác thực **API Key**), `SEPAY_BANK_ACCOUNT`, `SEPAY_BANK_NAME` (để dựng ảnh QR). Webhook dùng kết nối DB chính của ứng dụng (không còn login `rc_webhook` riêng).
2. Trong SePay: URL webhook `https://<miền>/api/webhooks/sepay`, cấu hình nhận diện mã thanh toán theo tiền tố `RC` (mã dạng `RC<số hóa đơn>Q<số>`). Endpoint trả `{"success": true}` HTTP 200 cho mọi kết quả nghiệp vụ (kể cả lệch tiền) để SePay không gửi lại; chỉ lỗi xác thực/payload/hệ thống mới trả mã lỗi.
3. Thử local không cần SePay: `curl -X POST localhost:8081/api/webhooks/sepay -H "Authorization: Apikey $SEPAY_API_KEY" -H 'Content-Type: application/json' -d '{"id":1,"code":"RC1Q1","content":"RC1Q1","transferType":"in","transferAmount":50000}'`.

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
- Cần đổi schema hoặc trigger → viết migration Flyway mới, báo cả nhóm duyệt; không sửa tay DB.
