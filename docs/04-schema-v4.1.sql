-- =====================================================================
-- RESTAURANT CORE — Schema v4.1 (PostgreSQL 15+) — bản chốt nghiệp vụ
-- Dựng lại từ Master Spec v4 (§C–§K, luồng §G, chuyển trạng thái §E2, khóa §I3),
-- sửa 35 phát hiện DB-01…DB-35 và áp dụng các quyết định nghiệp vụ QĐ-1…QĐ-8
-- (docs/02-danh-gia-database.md §10–§11; kiểm toán hàm ở docs/07-kiem-toan-ham-rc.md).
--
-- Nội dung: 48 bảng · ràng buộc · index · trigger bảo vệ · 32 hàm nghiệp vụ rc_*
--           (20 tên của v4 + 12 hàm bổ sung) và các hàm trợ giúp nội bộ.
--           Phân quyền role PostgreSQL (GRANT/REVOKE) nằm ở 09-acl-v4.1.sql; truy vấn báo cáo ở 10-bao-cao-v4.1.sql.
-- Chạy    : trên database TRỐNG, PostgreSQL 15+ (UNIQUE NULLS NOT DISTINCT, btree_gist); đã kiểm trên 15, 16, 17, 18.
--           Database phải hạ được chữ hoa có dấu tiếng Việt (UNIQUE lower(name) của DB-08), ví dụ:
--             CREATE DATABASE restaurant_core TEMPLATE template0 ENCODING 'UTF8'
--                    LOCALE_PROVIDER icu ICU_LOCALE 'vi-VN';        -- hoặc LC_COLLATE/LC_CTYPE 'en_US.UTF-8'
--           Locale 'C' sẽ bị file này từ chối ngay ở bước đầu.
--             psql -v ON_ERROR_STOP=1 -d restaurant_core -f 04-schema-v4.1.sql
--             psql -v ON_ERROR_STOP=1 -d restaurant_core -f 05-test-v4.1.sql
--             node 06-test-concurrency-v4.1.mjs postgresql://user@host:port/restaurant_core
--
-- Quyết định nghiệp vụ đã áp dụng
--   QĐ-1  daily_menu_items.quota_total: NULL = không giới hạn suất; 0 = không còn suất được bán; ≥ 0.
--         Không giới hạn suất KHÔNG bỏ kiểm tra nguyên liệu (rc_accept_item vẫn đặt giữ FEFO).
--   QĐ-2  Hủy trước COOKING: rc_cancel_item (theo quyền). Từ COOKING trở đi: chỉ Manager, bắt buộc lý do,
--         rc_cancel_item_exception — không hoàn nguyên liệu, không hoàn quota, không tính tiền.
--   QĐ-3  Khách bỏ về chưa thanh toán: rc_abandon_session (Manager, bắt buộc lý do). Ghi nhận nghĩa vụ ở
--         invoices.status = 'UNCOLLECTED' (+ uncollected_*), đóng phiên, trả bàn; không có payments.
--         UNCOLLECTED là trạng thái cuối của Core (không công nợ / thu bù); webhook QR đến sau chỉ được lưu
--         AFTER_CANCEL (OPEN) để đối chiếu, không chuyển PAID.
--   QĐ-4  rc_checkout_invoice (Cashier) tạo đủ invoice_items, tự tính tổng, đối chiếu với đơn, chuyển
--         PAYMENT_PENDING; rc_submit_batch từ chối khi đang thanh toán; khóa orders → invoices.
--         Gửi món khi hết quota: KHÔNG chặn, rc_submit_batch trả cảnh báo QUOTA_EXCEEDED; kiểm cứng ở rc_accept_item.
--   QĐ-5  Không hủy một phần số lượng: hủy cả dòng (rc_cancel_item / rc_cancel_item_exception) rồi gửi dòng mới
--         với số lượng thay thế; dòng CANCELLED là trạng thái cuối, không dùng lại.
--   QĐ-6  Áp dụng (quy ước sẵn có): mọi loại nguyên liệu bếp tính theo base_unit của nguyên liệu gốc.
--   QĐ-7  Áp dụng: giữ bảng orders (1–1 với table_sessions), baseline 48 bảng.
--   QĐ-8  Quota chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING giải phóng đúng một lần
--         (số đã chiếm được suy ra từ trạng thái, không lưu); từ COOKING không hoàn (rc_quota_used).
--   QĐ-MT Miễn thu (rc_waive_invoice, Manager, bắt buộc lý do): invoices.status = 'WAIVED' (+ waived_*), giữ
--         nguyên invoice_items/total (giá trị được miễn), không payments, không doanh thu. Hóa đơn 0 đồng cũng
--         đóng bằng WAIVED — không còn hóa đơn DRAFT treo sau khi đơn kết thúc.
--   QĐ-ID Chứng từ nhập / xuất / kiểm kê tạo bằng rc_create_stock_receipt / _issue / _count với request_key:
--         cùng khóa + cùng payload → trả chứng từ cũ; cùng khóa khác payload → RC409.
--   QĐ-NG Ngày kinh doanh: Asia/Ho_Chi_Minh, bắt đầu 04:00; tính bằng rc_business_date(), không phụ thuộc
--         TimeZone của phiên kết nối.
--
-- Quy ước chung
--   * Ranh giới tin cậy: DB KHÔNG tự xác thực người dùng cuối. Backend (Spring Security) xác thực JWT và chỉ
--     truyền p_actor = employees.id của principal đã xác thực — không bao giờ lấy từ body/query của client.
--     DB cưỡng chế: role PostgreSQL của backend chỉ EXECUTE hàm rc_* (09-acl), và mọi hàm có p_actor kiểm
--     nhân viên ACTIVE + tài khoản ACTIVE + vai trò được phép (rc_authorize, ma trận ở docs/07 §10) → lỗi
--     ánh xạ endpoint ở backend không thể biến Waiter thành Manager. DB không dùng biến phiên tự đặt để định danh.
--   * Mã lỗi (SQLSTATE) để backend ánh xạ HTTP/retry:
--       RC001 thiếu nguyên liệu · RC403 không đủ quyền · RC409 xung đột trạng thái (gọi lặp, sai bước)
--       RC422 dữ liệu không hợp lệ · 23505/23503/23514 vi phạm UNIQUE/FK/CHECK · 40001/40P01 thử lại.
--   * Thứ tự khóa dùng chung cho MỌI hàm (tránh deadlock), luôn theo id tăng dần trong cùng loại:
--       1 orders → 2 invoices → 3 payment_requests đang mở → 4 table_sessions → dining_tables
--       → 5 order_items → 6 daily_menu_items → 7 inventory_batches → 8 stock_issue_items → 9 kitchen_batches
--     Chứng từ kho (header) được khóa trước tài nguyên mà nó chạm.
--   * Ngày kinh doanh: một hàm duy nhất rc_business_date() (chốt ngày 04:00 giờ Việt Nam).
--   * Hàm rc_* là SECURITY DEFINER với search_path cố định, REVOKE khỏi PUBLIC (khối cuối file); chủ sở hữu và
--     GRANT theo role ứng dụng ở 09-acl-v4.1.sql.
-- =====================================================================
BEGIN;

-- Điều kiện môi trường: các UNIQUE (lower(name)) cần database hạ được chữ hoa có dấu (locale 'C' không làm được)
DO $$
BEGIN
    IF lower('ẮÒÚĐ') <> 'ắòúđ' THEN
        RAISE EXCEPTION 'Database không hỗ trợ chữ hoa/thường Unicode (lower(''ẮÒÚĐ'') = %). Tạo database với ENCODING UTF8 và LOCALE_PROVIDER icu ICU_LOCALE ''vi-VN'' (hoặc locale UTF-8 như en_US.UTF-8).', lower('ẮÒÚĐ');
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- DB-02: EXCLUDE chống trùng ca

-- ---------------------------------------------------------------------
-- 0. Hàm tiện ích dùng chung
-- ---------------------------------------------------------------------
-- DB-33: một quy tắc ngày kinh doanh cho payments, operating_expenses, daily_menus, đối soát
CREATE FUNCTION rc_business_date(p_at TIMESTAMPTZ DEFAULT now()) RETURNS DATE
LANGUAGE sql STABLE AS $$
    SELECT ((p_at AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '4 hours')::date
$$;

-- Hạn dùng tại bếp = sớm hơn giữa (hết ngày hạn lô gốc) và (bây giờ + số giờ bảo quản của loại)
CREATE FUNCTION rc_usable_until(p_expiry DATE, p_shelf_life_hours INT) RETURNS TIMESTAMPTZ
LANGUAGE sql STABLE AS $$
    SELECT LEAST(((p_expiry + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'),
                 now() + make_interval(hours => p_shelf_life_hours))
$$;

-- =====================================================================
-- 1. PHÂN HỆ 1 — Nhân viên • tài khoản • ca • chấm công (11 bảng)
-- =====================================================================
CREATE TABLE departments (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    department_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE employees (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_code VARCHAR(20)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    gender        VARCHAR(10),
    date_of_birth DATE,
    phone         VARCHAR(15),
    email         VARCHAR(100),
    address       VARCHAR(255),
    hire_date     DATE,
    end_date      DATE,
    work_status   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CONSTRAINT ck_employees_work_status CHECK (work_status IN ('ACTIVE', 'INACTIVE', 'LEFT')),
    department_id BIGINT       NOT NULL REFERENCES departments (id),
    CONSTRAINT ck_employee_left_end_date CHECK (work_status <> 'LEFT' OR end_date IS NOT NULL),           -- DB-03
    CONSTRAINT ck_employee_dates CHECK (end_date IS NULL OR hire_date IS NULL OR end_date >= hire_date)  -- DB-03
);
CREATE INDEX idx_employee_department_status ON employees (department_id, work_status);

CREATE TABLE accounts (
    id                   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id          BIGINT       NOT NULL UNIQUE REFERENCES employees (id),
    username             VARCHAR(50)  NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN      NOT NULL DEFAULT true,
    account_status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                         CONSTRAINT ck_accounts_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE roles (
    id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_code VARCHAR(30) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL
);

CREATE TABLE permissions (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    permission_code VARCHAR(50)  NOT NULL UNIQUE,
    description     VARCHAR(255) NOT NULL
);

CREATE TABLE account_roles (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    role_id    BIGINT NOT NULL REFERENCES roles (id),
    CONSTRAINT ux_account_role UNIQUE (account_id, role_id)                                               -- DB-04
);
CREATE INDEX idx_account_role_inverse ON account_roles (role_id, account_id);

CREATE TABLE role_permissions (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    CONSTRAINT ux_role_permission UNIQUE (role_id, permission_id)                                         -- DB-04
);
CREATE INDEX idx_permission_role_inverse ON role_permissions (permission_id, role_id);

CREATE TABLE shifts (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_name    VARCHAR(50) NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    break_minutes INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT true
);

CREATE TABLE shift_assignments (
    id                      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id             BIGINT      NOT NULL REFERENCES employees (id),
    shift_id                BIGINT      NOT NULL REFERENCES shifts (id),
    work_date               DATE        NOT NULL,
    scheduled_start_at      TIMESTAMPTZ NOT NULL,
    scheduled_end_at        TIMESTAMPTZ NOT NULL,
    scheduled_break_minutes INT         NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                            CONSTRAINT ck_shift_assignments_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    note                    VARCHAR(255),
    cancel_reason           VARCHAR(255),
    cancelled_by_id         BIGINT REFERENCES employees (id),
    cancelled_at            TIMESTAMPTZ,
    CONSTRAINT ck_shift_assignment_window CHECK (scheduled_end_at > scheduled_start_at),                  -- DB-02
    CONSTRAINT ck_shift_assignment_cancel_fields CHECK (                                                  -- DB-03
        CASE WHEN status = 'CANCELLED'
             THEN num_nonnulls(cancel_reason, cancelled_by_id, cancelled_at) = 3
             ELSE num_nonnulls(cancel_reason, cancelled_by_id, cancelled_at) = 0 END),
    CONSTRAINT ex_shift_assignment_no_overlap                                                             -- DB-02
        EXCLUDE USING gist (employee_id WITH =, tstzrange(scheduled_start_at, scheduled_end_at) WITH &&)
        WHERE (status <> 'CANCELLED')
);
CREATE INDEX idx_shift_assignment_roster ON shift_assignments (employee_id, work_date);

CREATE TABLE attendances (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shift_assignment_id BIGINT      NOT NULL UNIQUE REFERENCES shift_assignments (id),
    attendance_status   VARCHAR(20) NOT NULL                                                              -- DB-01
                        CONSTRAINT ck_attendances_status CHECK (attendance_status IN ('PRESENT', 'ABSENT')),
    actual_start_at     TIMESTAMPTZ,
    actual_end_at       TIMESTAMPTZ,
    worked_minutes      INT,
    note                VARCHAR(255),
    confirmed_by_id     BIGINT REFERENCES employees (id),
    confirmed_at        TIMESTAMPTZ,
    CONSTRAINT ck_attendance_present_fields CHECK (                                                       -- DB-01
        attendance_status <> 'PRESENT'
        OR (actual_start_at IS NOT NULL AND actual_end_at > actual_start_at AND worked_minutes >= 0)),
    CONSTRAINT ck_attendance_confirm_pair CHECK ((confirmed_by_id IS NULL) = (confirmed_at IS NULL)),     -- DB-01
    -- số phút công đã duyệt không vượt khoảng thời gian có mặt (phần nghỉ nằm ở shift_assignments)
    CONSTRAINT ck_attendance_worked_le_span CHECK (
        attendance_status <> 'PRESENT'
        OR worked_minutes <= ceil(extract(epoch FROM (actual_end_at - actual_start_at)) / 60))
);

CREATE TABLE attendance_adjustments (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    attendance_id      BIGINT       NOT NULL REFERENCES attendances (id),
    old_status         VARCHAR(20)  NOT NULL
                       CONSTRAINT ck_attendance_adj_old_status CHECK (old_status IN ('PRESENT', 'ABSENT')),
    new_status         VARCHAR(20)  NOT NULL
                       CONSTRAINT ck_attendance_adj_new_status CHECK (new_status IN ('PRESENT', 'ABSENT')),
    old_start_at       TIMESTAMPTZ,
    new_start_at       TIMESTAMPTZ,
    old_end_at         TIMESTAMPTZ,
    new_end_at         TIMESTAMPTZ,
    old_worked_minutes INT,
    new_worked_minutes INT,
    reason             VARCHAR(255) NOT NULL,
    adjusted_by_id     BIGINT       NOT NULL REFERENCES employees (id),
    adjusted_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_adjustments_history ON attendance_adjustments (attendance_id, adjusted_at DESC);

-- =====================================================================
-- 2. PHÂN HỆ 4 (danh mục) — tạo trước phân hệ 2 vì recipe_ingredients trỏ tới (DB-35)
-- =====================================================================
CREATE TABLE ingredients (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name              VARCHAR(100)  NOT NULL UNIQUE,
    base_unit         VARCHAR(20)   NOT NULL,   -- DB-29: chọn đơn vị nhỏ (g, ml) cho gia vị định lượng < 1 g/suất
    min_quantity      NUMERIC(12,3) NOT NULL DEFAULT 0
                      CONSTRAINT ck_inventory_minimum_nonnegative CHECK (min_quantity >= 0),
    is_expiry_tracked BOOLEAN       NOT NULL DEFAULT true,
    is_active         BOOLEAN       NOT NULL DEFAULT true
);

-- DB-28: không có cột đơn vị — số lượng loại bếp luôn tính theo ingredients.base_unit của nguyên liệu gốc
CREATE TABLE kitchen_ingredient_types (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    raw_ingredient_id BIGINT        NOT NULL REFERENCES ingredients (id),
    name              VARCHAR(100)  NOT NULL,
    handling_type     VARCHAR(12)   NOT NULL
                      CONSTRAINT ck_kitchen_types_handling CHECK (handling_type IN ('DIRECT', 'PREPARED')),
    shelf_life_hours  INT           CONSTRAINT ck_kitchen_shelf_life_positive CHECK (shelf_life_hours > 0),
    minimum_quantity  NUMERIC(12,3) NOT NULL DEFAULT 0
                      CONSTRAINT ck_kitchen_minimum_nonnegative CHECK (minimum_quantity >= 0),
    is_active         BOOLEAN       NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ux_kitchen_direct_one ON kitchen_ingredient_types (raw_ingredient_id) WHERE handling_type = 'DIRECT';
CREATE UNIQUE INDEX ux_kitchen_types_name ON kitchen_ingredient_types (raw_ingredient_id, lower(name));   -- DB-08
CREATE INDEX idx_kitchen_types_raw ON kitchen_ingredient_types (raw_ingredient_id, handling_type);

-- =====================================================================
-- 3. PHÂN HỆ 2 — Thực đơn • món • công thức • thực đơn ngày (6 bảng)
-- =====================================================================
CREATE TABLE menu_categories (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ux_menu_categories_name ON menu_categories (lower(name));                             -- DB-08

CREATE TABLE menu_items (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    menu_category_id BIGINT        NOT NULL REFERENCES menu_categories (id),
    name             VARCHAR(100)  NOT NULL,
    price            NUMERIC(12,2) NOT NULL CONSTRAINT ck_price_nonnegative CHECK (price >= 0),
    image_url        VARCHAR(255),
    is_active        BOOLEAN       NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ux_menu_items_name ON menu_items (lower(name));                                       -- DB-08
CREATE INDEX idx_menu_items_active_category ON menu_items (menu_category_id, is_active);

CREATE TABLE recipes (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    menu_item_id BIGINT  NOT NULL REFERENCES menu_items (id),
    version      INT     NOT NULL,
    is_current   BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ux_recipes_item_version UNIQUE (menu_item_id, version),                                    -- DB-06
    CONSTRAINT ux_recipes_id_item UNIQUE (id, menu_item_id)            -- đích của FK ghép từ daily_menu_items
);
CREATE UNIQUE INDEX ux_recipes_current ON recipes (menu_item_id) WHERE is_current IS TRUE;

CREATE TABLE recipe_ingredients (
    id                         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    recipe_id                  BIGINT        NOT NULL REFERENCES recipes (id),
    kitchen_ingredient_type_id BIGINT        NOT NULL REFERENCES kitchen_ingredient_types (id),
    quantity_per_serving       NUMERIC(12,3) NOT NULL
                               CONSTRAINT ck_recipe_qty_positive CHECK (quantity_per_serving > 0),         -- DB-05
    CONSTRAINT ux_recipe_ingredient_once UNIQUE (recipe_id, kitchen_ingredient_type_id)
);
CREATE INDEX idx_recipe_ingredient_type ON recipe_ingredients (kitchen_ingredient_type_id);

CREATE TABLE daily_menus (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_date DATE        NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                  CONSTRAINT ck_daily_menus_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    published_at  TIMESTAMPTZ,
    CONSTRAINT ck_daily_menu_published_at CHECK ((status = 'DRAFT') = (published_at IS NULL))             -- DB-03
);

CREATE TABLE daily_menu_items (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    daily_menu_id BIGINT       NOT NULL REFERENCES daily_menus (id),
    menu_item_id  BIGINT       NOT NULL REFERENCES menu_items (id),
    recipe_id     BIGINT       NOT NULL REFERENCES recipes (id),
    -- QĐ-1: NULL = không giới hạn suất; 0 = không còn suất được bán; > 0 = số suất tối đa trong ngày
    quota_total   INT          CONSTRAINT ck_daily_menu_quota_nonneg CHECK (quota_total >= 0),
    is_paused     BOOLEAN      NOT NULL DEFAULT false,
    pause_reason  VARCHAR(255),
    paused_by_id  BIGINT REFERENCES employees (id),                                                       -- DB-07
    paused_at     TIMESTAMPTZ,                                                                            -- DB-07
    CONSTRAINT fk_daily_menu_items_recipe_of_item FOREIGN KEY (recipe_id, menu_item_id)
        REFERENCES recipes (id, menu_item_id),
    CONSTRAINT ux_daily_menu_items_once UNIQUE (daily_menu_id, menu_item_id),
    CONSTRAINT ck_daily_menu_pause_fields CHECK (                                                         -- DB-07
        CASE WHEN is_paused THEN num_nonnulls(pause_reason, paused_by_id, paused_at) = 3
             ELSE num_nonnulls(pause_reason, paused_by_id, paused_at) = 0 END)
);

-- =====================================================================
-- 4. PHÂN HỆ 3 — Bàn • đơn hàng • hóa đơn • thanh toán (11 bảng)
-- =====================================================================
CREATE TABLE dining_tables (
    id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code      VARCHAR(20) NOT NULL UNIQUE,
    capacity  INT         NOT NULL CONSTRAINT ck_dining_tables_capacity_positive CHECK (capacity > 0),
    status    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'    -- cache của "có phiên OPEN", chỉ đổi trong rc_* (DB-15)
              CONSTRAINT ck_dining_tables_status CHECK (status IN ('AVAILABLE', 'OCCUPIED')),
    is_active BOOLEAN     NOT NULL DEFAULT true                                                           -- DB-15
);

CREATE TABLE table_sessions (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    dining_table_id BIGINT      NOT NULL REFERENCES dining_tables (id),
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                    CONSTRAINT ck_table_sessions_status CHECK (status IN ('OPEN', 'CLOSED')),
    opened_by_id    BIGINT      NOT NULL REFERENCES employees (id),                                       -- DB-16
    closed_by_id    BIGINT REFERENCES employees (id),                                                     -- DB-16
    CONSTRAINT ck_table_session_closed CHECK (                                                            -- DB-16
        (status = 'OPEN') = (closed_at IS NULL)
        AND (status = 'OPEN') = (closed_by_id IS NULL)
        AND (closed_at IS NULL OR closed_at >= opened_at))
);
CREATE UNIQUE INDEX ux_table_sessions_open ON table_sessions (dining_table_id) WHERE status = 'OPEN';
CREATE INDEX idx_table_sessions_history ON table_sessions (dining_table_id, opened_at DESC);              -- DB-27

-- DB-18: giữ quan hệ 1–1 với table_sessions (chứng từ bán hàng tách khỏi việc chiếm bàn)
CREATE TABLE orders (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    table_session_id BIGINT      NOT NULL UNIQUE REFERENCES table_sessions (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                     CONSTRAINT ck_orders_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'))
);

CREATE TABLE order_batches (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id     BIGINT      NOT NULL REFERENCES orders (id),
    waiter_id    BIGINT      NOT NULL REFERENCES employees (id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_key  VARCHAR(64) NOT NULL UNIQUE
);
CREATE INDEX idx_order_batches_by_order ON order_batches (order_id, submitted_at);

CREATE TABLE order_items (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_batch_id      BIGINT        NOT NULL REFERENCES order_batches (id),
    daily_menu_item_id  BIGINT        NOT NULL REFERENCES daily_menu_items (id),
    quantity            INT           NOT NULL CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    item_name_snapshot  VARCHAR(100)  NOT NULL,
    unit_price          NUMERIC(12,2) NOT NULL CONSTRAINT ck_order_items_unit_price_nonneg CHECK (unit_price >= 0),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        CONSTRAINT ck_order_items_status CHECK (status IN
                            ('PENDING', 'ACCEPTED', 'COOKING', 'READY', 'SERVED', 'CANCELLED', 'REJECTED')),
    has_stock_exception BOOLEAN       NOT NULL DEFAULT false,
    note                VARCHAR(255),
    -- DB-14: cờ ngoại lệ chỉ có nghĩa khi món đang giữ nguyên liệu (bật bởi rc_mark_batch_unusable)
    CONSTRAINT ck_order_item_exception_accepted CHECK (NOT has_stock_exception OR status = 'ACCEPTED')
);
CREATE INDEX idx_order_items_batch_status ON order_items (order_batch_id, status);
CREATE INDEX idx_order_items_daily_quota ON order_items (daily_menu_item_id, status);

CREATE TABLE order_item_events (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_item_id BIGINT       NOT NULL REFERENCES order_items (id),
    actor_id      BIGINT       NOT NULL REFERENCES employees (id),
    from_status   VARCHAR(20)  CONSTRAINT ck_order_item_events_from CHECK (from_status IN
                      ('PENDING', 'ACCEPTED', 'COOKING', 'READY', 'SERVED', 'CANCELLED', 'REJECTED')),
    to_status     VARCHAR(20)  NOT NULL CONSTRAINT ck_order_item_events_to CHECK (to_status IN
                      ('PENDING', 'ACCEPTED', 'COOKING', 'READY', 'SERVED', 'CANCELLED', 'REJECTED')),
    reason        VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- QĐ-2: từ chối/hủy luôn có lý do không rỗng; người thực hiện là actor_id
    CONSTRAINT ck_order_item_event_reason CHECK (to_status NOT IN ('REJECTED', 'CANCELLED') OR btrim(COALESCE(reason, '')) <> ''),
    CONSTRAINT ck_order_item_event_first CHECK (from_status IS NOT NULL OR to_status = 'PENDING')
);
CREATE INDEX idx_order_item_events_history ON order_item_events (order_item_id, created_at DESC);

-- Bốn kết thúc của hóa đơn phân biệt rõ (không trạng thái nào tạo payments giả):
--   PAID        — đã thu thật (payments khớp tổng);
--   UNCOLLECTED — QĐ-3: có nghĩa vụ nhưng khách bỏ về; total > 0 là số chưa thu được;
--   WAIVED      — QĐ-MT: Manager miễn thu hợp lệ; total (≥ 0) là giá trị được miễn, invoice_items giữ nguyên giá bán.
-- UNCOLLECTED và WAIVED không phải doanh thu thực thu; Manager ghi lý do/người/thời điểm.
CREATE TABLE invoices (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id           BIGINT        NOT NULL UNIQUE REFERENCES orders (id),
    total              NUMERIC(12,2) NOT NULL DEFAULT 0 CONSTRAINT ck_invoices_total_nonneg CHECK (total >= 0),
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                       CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'PAYMENT_PENDING', 'PAID', 'UNCOLLECTED', 'WAIVED')),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),                                              -- DB-13
    created_by_id      BIGINT        NOT NULL REFERENCES employees (id),                                  -- DB-13
    uncollected_reason VARCHAR(255),
    uncollected_by_id  BIGINT REFERENCES employees (id),
    uncollected_at     TIMESTAMPTZ,
    waived_reason      VARCHAR(255),
    waived_by_id       BIGINT REFERENCES employees (id),
    waived_at          TIMESTAMPTZ,
    CONSTRAINT ck_invoice_uncollected_fields CHECK (
        CASE WHEN status = 'UNCOLLECTED'
             THEN num_nonnulls(uncollected_reason, uncollected_by_id, uncollected_at) = 3
                  AND btrim(uncollected_reason) <> '' AND total > 0
             ELSE num_nonnulls(uncollected_reason, uncollected_by_id, uncollected_at) = 0 END),
    CONSTRAINT ck_invoice_waived_fields CHECK (
        CASE WHEN status = 'WAIVED'
             THEN num_nonnulls(waived_reason, waived_by_id, waived_at) = 3 AND btrim(waived_reason) <> ''
             ELSE num_nonnulls(waived_reason, waived_by_id, waived_at) = 0 END)
);
CREATE INDEX idx_invoices_uncollected ON invoices (uncollected_at) WHERE status = 'UNCOLLECTED';
CREATE INDEX idx_invoices_waived ON invoices (waived_at) WHERE status = 'WAIVED';

CREATE TABLE payment_requests (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    invoice_id        BIGINT        NOT NULL REFERENCES invoices (id),
    cashier_id        BIGINT        NOT NULL REFERENCES employees (id),                                   -- DB-11
    amount_due        NUMERIC(12,2) NOT NULL CONSTRAINT ck_payment_requests_amount_due_nonneg CHECK (amount_due >= 0),
    cash_received     NUMERIC(12,2),
    change_amount     NUMERIC(12,2) GENERATED ALWAYS AS (cash_received - amount_due) STORED,              -- DB-10
    method            VARCHAR(20)   NOT NULL CONSTRAINT ck_payment_requests_method CHECK (method IN ('CASH', 'QR')),
    status            VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED'
                      CONSTRAINT ck_payment_requests_status CHECK (status IN
                          ('REQUESTED', 'WAITING_TRANSFER', 'PAID', 'CANCELLED')),
    payment_reference VARCHAR(64)   UNIQUE,
    cancel_reason     VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),                                               -- DB-11
    cancelled_at      TIMESTAMPTZ,                                                                        -- DB-11
    cancelled_by_id   BIGINT REFERENCES employees (id),                                                   -- DB-11
    CONSTRAINT ck_payment_method_fields CHECK (                                                           -- DB-10
        (method = 'CASH' AND cash_received IS NOT NULL AND cash_received >= amount_due AND payment_reference IS NULL)
        OR (method = 'QR' AND cash_received IS NULL AND payment_reference IS NOT NULL)),
    CONSTRAINT ck_payment_request_cancel_fields CHECK (                                                   -- DB-11
        CASE WHEN status = 'CANCELLED'
             THEN num_nonnulls(cancel_reason, cancelled_at, cancelled_by_id) = 3
             ELSE num_nonnulls(cancel_reason, cancelled_at, cancelled_by_id) = 0 END),
    CONSTRAINT ux_payment_requests_id_invoice_method UNIQUE (id, invoice_id, method)                     -- DB-09
);
CREATE UNIQUE INDEX ux_payment_request_active ON payment_requests (invoice_id)
    WHERE status IN ('REQUESTED', 'WAITING_TRANSFER');
CREATE INDEX idx_payrequest_invoice_history ON payment_requests (invoice_id, status);

CREATE TABLE payment_webhook_events (
    id                        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    payment_request_id        BIGINT REFERENCES payment_requests (id),
    provider_transaction_code VARCHAR(64)   NOT NULL UNIQUE,
    payment_reference         VARCHAR(64)   NOT NULL,
    amount                    NUMERIC(12,2) NOT NULL,
    processing_result         VARCHAR(20)   NOT NULL
                              CONSTRAINT ck_webhook_processing_result CHECK (processing_result IN
                                  ('PROCESSED', 'MISMATCH', 'AFTER_CANCEL')),
    received_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    raw_payload               TEXT          NOT NULL,
    resolution_status         VARCHAR(20)   CONSTRAINT ck_webhook_resolution_status CHECK (resolution_status IN ('OPEN', 'RESOLVED')),  -- DB-12
    resolved_by_id            BIGINT REFERENCES employees (id),                                           -- DB-12
    resolved_at               TIMESTAMPTZ,                                                                -- DB-12
    resolution_note           VARCHAR(255),                                                               -- DB-12
    CONSTRAINT ck_webhook_resolution CHECK (                                                              -- DB-12
        CASE
            WHEN processing_result = 'PROCESSED'
                THEN num_nonnulls(resolution_status, resolved_by_id, resolved_at, resolution_note) = 0
            WHEN resolution_status = 'OPEN'
                THEN num_nonnulls(resolved_by_id, resolved_at, resolution_note) = 0
            WHEN resolution_status = 'RESOLVED'
                THEN num_nonnulls(resolved_by_id, resolved_at, resolution_note) = 3
            ELSE false
        END),
    CONSTRAINT ux_webhook_id_request UNIQUE (id, payment_request_id)    -- đích của FK ghép từ payments
);
CREATE INDEX idx_webhook_reference_audit ON payment_webhook_events (payment_reference, received_at DESC);
CREATE INDEX idx_webhook_by_request ON payment_webhook_events (payment_request_id)
    WHERE payment_request_id IS NOT NULL;                                                                 -- DB-27
CREATE INDEX idx_webhook_open_incidents ON payment_webhook_events (received_at)
    WHERE resolution_status = 'OPEN';                                                                     -- DB-12

CREATE TABLE payments (
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    invoice_id               BIGINT        NOT NULL UNIQUE REFERENCES invoices (id),
    payment_request_id       BIGINT        NOT NULL UNIQUE REFERENCES payment_requests (id),
    payment_webhook_event_id BIGINT        UNIQUE REFERENCES payment_webhook_events (id),
    amount                   NUMERIC(12,2) NOT NULL CONSTRAINT ck_payments_amount_nonneg CHECK (amount >= 0),
    method                   VARCHAR(20)   NOT NULL CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'QR')),
    paid_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    business_date            DATE          NOT NULL,
    CONSTRAINT fk_payments_request_invoice_method FOREIGN KEY (payment_request_id, invoice_id, method)     -- DB-09
        REFERENCES payment_requests (id, invoice_id, method),
    CONSTRAINT fk_payments_webhook_request FOREIGN KEY (payment_webhook_event_id, payment_request_id)
        REFERENCES payment_webhook_events (id, payment_request_id),
    CONSTRAINT ck_payments_qr_webhook CHECK ((method = 'QR') = (payment_webhook_event_id IS NOT NULL))     -- DB-09
);
CREATE INDEX idx_payments_business_date ON payments (business_date, method, paid_at);

CREATE TABLE invoice_items (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    invoice_id    BIGINT        NOT NULL REFERENCES invoices (id),
    order_item_id BIGINT        NOT NULL UNIQUE REFERENCES order_items (id),
    item_name     VARCHAR(100)  NOT NULL,
    quantity      INT           NOT NULL CONSTRAINT ck_invoice_items_quantity_positive CHECK (quantity > 0),
    unit_price    NUMERIC(12,2) NOT NULL CONSTRAINT ck_invoice_items_unit_price_nonneg CHECK (unit_price >= 0),
    line_total    NUMERIC(12,2) GENERATED ALWAYS AS (quantity * unit_price) STORED
    -- DB-26: bỏ ck_invoice_item_nonnegative (hệ quả của 2 CHECK trên)
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id, order_item_id);

-- =====================================================================
-- 5. PHÂN HỆ 4 — Kho RAW • bếp • sơ chế • kiểm kê (16 bảng còn lại)
-- =====================================================================
CREATE TABLE stock_receipts (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    supplier_name   VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CONSTRAINT ck_stock_receipts_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by_id   BIGINT       NOT NULL REFERENCES employees (id),                                      -- DB-25
    received_at     TIMESTAMPTZ,
    confirmed_by_id BIGINT REFERENCES employees (id),
    confirmed_at    TIMESTAMPTZ,
    request_key     VARCHAR(64)  NOT NULL UNIQUE,   -- QĐ-ID: một yêu cầu tạo phiếu = một chứng từ
    request_hash    CHAR(64)     NOT NULL,          -- sha256 của payload tạo phiếu: cùng khóa khác payload → RC409
    CONSTRAINT ck_stock_receipt_confirm_fields CHECK (                                                    -- DB-03
        (status = 'CONFIRMED') = (num_nonnulls(received_at, confirmed_by_id, confirmed_at) = 3))
);

CREATE TABLE stock_receipt_items (
    id                        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_receipt_id          BIGINT        NOT NULL REFERENCES stock_receipts (id),
    ingredient_id             BIGINT        NOT NULL REFERENCES ingredients (id),
    input_quantity            NUMERIC(12,3) NOT NULL
                              CONSTRAINT ck_stock_receipt_items_input_quantity_positive CHECK (input_quantity > 0),
    input_unit                VARCHAR(20)   NOT NULL,
    conversion_factor         NUMERIC(12,3) NOT NULL
                              CONSTRAINT ck_stock_receipt_items_conversion_factor_positive CHECK (conversion_factor > 0),
    unit_price_per_input_unit NUMERIC(12,2) NOT NULL
                              CONSTRAINT ck_stock_receipt_price_nonnegative CHECK (unit_price_per_input_unit >= 0),
    lot_code                  VARCHAR(50),
    expiry_date               DATE,
    CONSTRAINT ux_stock_receipt_items_lot UNIQUE NULLS NOT DISTINCT (stock_receipt_id, ingredient_id, lot_code, expiry_date)
);
CREATE INDEX idx_raw_lots_fefo ON stock_receipt_items (ingredient_id, expiry_date, id);

CREATE TABLE inventory_batches (
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_receipt_item_id BIGINT        NOT NULL UNIQUE REFERENCES stock_receipt_items (id),
    received_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),   -- = lúc xác nhận phiếu; tiêu chí phụ FEFO
    quantity_on_hand      NUMERIC(12,3) NOT NULL
                          CONSTRAINT ck_inventory_batches_quantity_on_hand_nonneg CHECK (quantity_on_hand >= 0),
    is_usable             BOOLEAN       NOT NULL DEFAULT true,
    unusable_reason       VARCHAR(255),
    unusable_marked_by_id BIGINT REFERENCES employees (id),
    unusable_marked_at    TIMESTAMPTZ,
    CONSTRAINT ck_inventory_batch_unusable_fields CHECK (                                                 -- DB-03
        CASE WHEN is_usable THEN num_nonnulls(unusable_reason, unusable_marked_by_id, unusable_marked_at) = 0
             ELSE num_nonnulls(unusable_reason, unusable_marked_by_id, unusable_marked_at) = 3 END)
);
CREATE INDEX idx_raw_lots_active ON inventory_batches (stock_receipt_item_id)
    WHERE is_usable AND quantity_on_hand > 0;                                                             -- DB-27

CREATE TABLE stock_issues (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    issued_by_id    BIGINT       NOT NULL REFERENCES employees (id),
    received_by_id  BIGINT REFERENCES employees (id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CONSTRAINT ck_stock_issues_status CHECK (status IN ('DRAFT', 'ISSUED', 'RECEIVED', 'CANCELLED')),
    issued_at       TIMESTAMPTZ,
    received_at     TIMESTAMPTZ,
    note            VARCHAR(255),
    cancel_reason   VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),                                                  -- DB-25
    cancelled_by_id BIGINT REFERENCES employees (id),                                                     -- DB-25
    request_key     VARCHAR(64)  NOT NULL UNIQUE,                                                         -- QĐ-ID
    request_hash    CHAR(64)     NOT NULL,                                                                -- QĐ-ID
    CONSTRAINT ck_stock_issue_fields CHECK (                                                              -- DB-03
        CASE status
            WHEN 'DRAFT'     THEN num_nonnulls(issued_at, received_at, received_by_id,
                                               cancel_reason, cancelled_at, cancelled_by_id) = 0
            WHEN 'ISSUED'    THEN issued_at IS NOT NULL
                                  AND num_nonnulls(received_at, received_by_id, cancel_reason, cancelled_at, cancelled_by_id) = 0
            WHEN 'RECEIVED'  THEN num_nonnulls(issued_at, received_at, received_by_id) = 3
                                  AND num_nonnulls(cancel_reason, cancelled_at, cancelled_by_id) = 0
            WHEN 'CANCELLED' THEN num_nonnulls(cancel_reason, cancelled_at, cancelled_by_id) = 3
                                  AND num_nonnulls(received_at, received_by_id) = 0
        END)
);
CREATE INDEX idx_stock_issues_pending ON stock_issues (status, issued_at);

CREATE TABLE stock_issue_items (
    id                     BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_issue_id         BIGINT        NOT NULL REFERENCES stock_issues (id),
    inventory_batch_id     BIGINT        NOT NULL REFERENCES inventory_batches (id),
    quantity               NUMERIC(12,3) NOT NULL CONSTRAINT ck_stock_issue_items_quantity_positive CHECK (quantity > 0),
    fulfillment_mode       VARCHAR(12)   NOT NULL
                           CONSTRAINT ck_stock_issue_items_mode CHECK (fulfillment_mode IN ('DIRECT', 'PREPARE')),
    wip_remaining_quantity NUMERIC(12,3) NOT NULL DEFAULT 0
                           CONSTRAINT ck_issue_wip_nonneg CHECK (wip_remaining_quantity >= 0),   -- DB-21: giữ 1 CHECK
    CONSTRAINT ck_issue_item_wip_bounds CHECK (                                                           -- DB-21
        wip_remaining_quantity <= quantity AND (fulfillment_mode = 'PREPARE' OR wip_remaining_quantity = 0)),
    CONSTRAINT ux_stock_issue_batch_mode UNIQUE (stock_issue_id, inventory_batch_id, fulfillment_mode)
);
CREATE INDEX idx_stock_issue_lines_batch ON stock_issue_items (inventory_batch_id, stock_issue_id);

CREATE TABLE kitchen_preparations (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_issue_item_id BIGINT        NOT NULL REFERENCES stock_issue_items (id),
    input_quantity      NUMERIC(12,3) NOT NULL
                        CONSTRAINT ck_kitchen_preparations_input_quantity_positive CHECK (input_quantity > 0),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                        CONSTRAINT ck_kitchen_preparations_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    loss_reason         VARCHAR(255),
    prepared_by_id      BIGINT        NOT NULL REFERENCES employees (id),
    prepared_at         TIMESTAMPTZ,
    request_key         VARCHAR(64)   NOT NULL UNIQUE,
    CONSTRAINT ck_kitchen_preparation_confirmed CHECK ((status = 'CONFIRMED') = (prepared_at IS NOT NULL))   -- DB-03
);
CREATE INDEX idx_kitchen_prep_wip ON kitchen_preparations (stock_issue_item_id, prepared_at);

CREATE TABLE kitchen_preparation_outputs (
    id                         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    kitchen_preparation_id     BIGINT        NOT NULL REFERENCES kitchen_preparations (id),
    kitchen_ingredient_type_id BIGINT        NOT NULL REFERENCES kitchen_ingredient_types (id),
    output_quantity            NUMERIC(12,3) NOT NULL
                               CONSTRAINT ck_kitchen_preparation_outputs_output_quantity_positive CHECK (output_quantity > 0),
    usable_until               TIMESTAMPTZ,
    CONSTRAINT ux_kitchen_prep_output_type UNIQUE (kitchen_preparation_id, kitchen_ingredient_type_id),
    CONSTRAINT ux_prep_output_id_type UNIQUE (id, kitchen_ingredient_type_id)                             -- DB-24
);

CREATE TABLE kitchen_batches (
    id                         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    kitchen_ingredient_type_id BIGINT        NOT NULL REFERENCES kitchen_ingredient_types (id),
    preparation_output_id      BIGINT        UNIQUE REFERENCES kitchen_preparation_outputs (id),
    direct_issue_item_id       BIGINT        UNIQUE REFERENCES stock_issue_items (id),
    usable_until               TIMESTAMPTZ,
    quantity_on_hand           NUMERIC(12,3) NOT NULL
                               CONSTRAINT ck_kitchen_batches_quantity_on_hand_nonneg CHECK (quantity_on_hand >= 0),
    reserved_quantity          NUMERIC(12,3) NOT NULL DEFAULT 0
                               CONSTRAINT ck_kitchen_batches_reserved_quantity_nonneg CHECK (reserved_quantity >= 0),
    is_usable                  BOOLEAN       NOT NULL DEFAULT true,
    unusable_reason            VARCHAR(255),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),                                      -- DB-25
    unusable_marked_by_id      BIGINT REFERENCES employees (id),                                          -- DB-25
    unusable_marked_at         TIMESTAMPTZ,                                                               -- DB-25
    CONSTRAINT ck_kitchen_batch_one_source CHECK (num_nonnulls(preparation_output_id, direct_issue_item_id) = 1),
    CONSTRAINT ck_kitchen_reserved_le_on_hand CHECK (reserved_quantity <= quantity_on_hand),              -- DB-19
    CONSTRAINT ck_kitchen_batch_unusable_fields CHECK (                                                   -- DB-03
        CASE WHEN is_usable THEN num_nonnulls(unusable_reason, unusable_marked_by_id, unusable_marked_at) = 0
             ELSE num_nonnulls(unusable_reason, unusable_marked_by_id, unusable_marked_at) = 3 END),
    CONSTRAINT fk_kitchen_batch_output_type FOREIGN KEY (preparation_output_id, kitchen_ingredient_type_id)  -- DB-24
        REFERENCES kitchen_preparation_outputs (id, kitchen_ingredient_type_id),
    CONSTRAINT ux_kitchen_batch_id_direct UNIQUE (id, direct_issue_item_id)                              -- DB-23
);
CREATE INDEX idx_kitchen_lots_fefo ON kitchen_batches (kitchen_ingredient_type_id, usable_until, id)
    WHERE is_usable = TRUE AND quantity_on_hand > 0;

CREATE TABLE stock_reservations (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_item_id    BIGINT        NOT NULL REFERENCES order_items (id),
    kitchen_batch_id BIGINT        NOT NULL REFERENCES kitchen_batches (id),
    quantity         NUMERIC(12,3) NOT NULL
                     CONSTRAINT ck_stock_reservations_quantity_positive CHECK (quantity > 0),     -- DB-26: giữ 1 CHECK
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                     CONSTRAINT ck_stock_reservations_status CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED')),
    release_reason   VARCHAR(30)                                                                          -- DB-14
                     CONSTRAINT ck_reservation_release_reason CHECK (release_reason IN ('ITEM_CANCELLED', 'BATCH_UNUSABLE')),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    CONSTRAINT ck_reservation_close_fields CHECK (                                                        -- DB-14
        CASE status
            WHEN 'ACTIVE'   THEN closed_at IS NULL AND release_reason IS NULL
            WHEN 'CONSUMED' THEN closed_at IS NOT NULL AND release_reason IS NULL
            WHEN 'RELEASED' THEN closed_at IS NOT NULL AND release_reason IS NOT NULL
        END),
    CONSTRAINT ux_stock_reservation_batch UNIQUE (order_item_id, kitchen_batch_id)
);
CREATE INDEX idx_reservations_active_lot ON stock_reservations (kitchen_batch_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_reservations_by_order ON stock_reservations (order_item_id, status);

CREATE TABLE stock_returns (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CONSTRAINT ck_stock_returns_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    requested_by_id BIGINT       NOT NULL REFERENCES employees (id),
    confirmed_by_id BIGINT REFERENCES employees (id),
    reason          VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ,
    request_key     VARCHAR(64)  NOT NULL UNIQUE,
    CONSTRAINT ck_stock_return_confirm_fields CHECK (                                                     -- DB-03
        (status = 'CONFIRMED') = (num_nonnulls(confirmed_by_id, confirmed_at) = 2))
);

CREATE TABLE stock_return_items (
    id                      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_return_id         BIGINT        NOT NULL REFERENCES stock_returns (id),
    source_issue_item_id    BIGINT        NOT NULL REFERENCES stock_issue_items (id),
    direct_kitchen_batch_id BIGINT REFERENCES kitchen_batches (id),
    quantity                NUMERIC(12,3) NOT NULL CONSTRAINT ck_stock_return_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_return_direct_batch_of_issue_item FOREIGN KEY (direct_kitchen_batch_id, source_issue_item_id)  -- DB-23
        REFERENCES kitchen_batches (id, direct_issue_item_id),
    CONSTRAINT ux_return_item_source UNIQUE (stock_return_id, source_issue_item_id)                      -- DB-23
);
CREATE INDEX idx_stock_return_items_issue ON stock_return_items (source_issue_item_id);

CREATE TABLE stock_adjustments (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CONSTRAINT ck_stock_adjustments_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    adjustment_type VARCHAR(20)  NOT NULL
                    CONSTRAINT ck_stock_adjustments_type CHECK (adjustment_type IN ('DAMAGE', 'EXPIRED', 'QUALITY', 'OTHER')),
    reason          VARCHAR(255) NOT NULL,
    created_by_id   BIGINT       NOT NULL REFERENCES employees (id),
    confirmed_by_id BIGINT REFERENCES employees (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ,
    request_key     VARCHAR(64)  NOT NULL UNIQUE,
    CONSTRAINT ck_stock_adjustment_confirm_fields CHECK (                                                 -- DB-03
        (status = 'CONFIRMED') = (num_nonnulls(confirmed_by_id, confirmed_at) = 2))
);

CREATE TABLE stock_adjustment_items (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_adjustment_id BIGINT        NOT NULL REFERENCES stock_adjustments (id),
    raw_batch_id        BIGINT REFERENCES inventory_batches (id),
    wip_issue_item_id   BIGINT REFERENCES stock_issue_items (id),
    kitchen_batch_id    BIGINT REFERENCES kitchen_batches (id),
    quantity_delta      NUMERIC(12,3) NOT NULL CONSTRAINT ck_adjust_delta_negative CHECK (quantity_delta < 0),  -- DB-22
    note                VARCHAR(255),
    CONSTRAINT ck_stock_adjust_one_source CHECK (num_nonnulls(raw_batch_id, wip_issue_item_id, kitchen_batch_id) = 1)
);
CREATE UNIQUE INDEX ux_adjust_item_raw ON stock_adjustment_items (stock_adjustment_id, raw_batch_id)
    WHERE raw_batch_id IS NOT NULL;                                                                       -- DB-22
CREATE UNIQUE INDEX ux_adjust_item_wip ON stock_adjustment_items (stock_adjustment_id, wip_issue_item_id)
    WHERE wip_issue_item_id IS NOT NULL;                                                                  -- DB-22
CREATE UNIQUE INDEX ux_adjust_item_kitchen ON stock_adjustment_items (stock_adjustment_id, kitchen_batch_id)
    WHERE kitchen_batch_id IS NOT NULL;                                                                   -- DB-22

CREATE TABLE stock_counts (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CONSTRAINT ck_stock_counts_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by_id   BIGINT       NOT NULL REFERENCES employees (id),                                      -- DB-25
    confirmed_by_id BIGINT REFERENCES employees (id),
    confirmed_at    TIMESTAMPTZ,
    request_key     VARCHAR(64)  NOT NULL UNIQUE,                                                         -- QĐ-ID
    request_hash    CHAR(64)     NOT NULL,                                                                -- QĐ-ID
    CONSTRAINT ck_stock_count_confirm_fields CHECK (                                                      -- DB-03
        (status = 'CONFIRMED') = (num_nonnulls(confirmed_by_id, confirmed_at) = 2))
);

CREATE TABLE stock_count_items (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    stock_count_id    BIGINT        NOT NULL REFERENCES stock_counts (id),
    raw_batch_id      BIGINT REFERENCES inventory_batches (id),
    wip_issue_item_id BIGINT REFERENCES stock_issue_items (id),
    kitchen_batch_id  BIGINT REFERENCES kitchen_batches (id),
    book_quantity     NUMERIC(12,3) NOT NULL
                      CONSTRAINT ck_stock_count_items_book_quantity_nonneg CHECK (book_quantity >= 0),
    actual_quantity   NUMERIC(12,3) NOT NULL
                      CONSTRAINT ck_stock_count_items_actual_quantity_nonneg CHECK (actual_quantity >= 0),
    reason            VARCHAR(255),
    CONSTRAINT ck_stock_count_one_source CHECK (num_nonnulls(raw_batch_id, wip_issue_item_id, kitchen_batch_id) = 1)
    -- DB-26: bỏ ck_count_total_nonnegative (trùng 2 CHECK không âm ở trên)
);
CREATE UNIQUE INDEX ux_stock_count_raw ON stock_count_items (stock_count_id, raw_batch_id) WHERE raw_batch_id IS NOT NULL;
CREATE UNIQUE INDEX ux_stock_count_wip ON stock_count_items (stock_count_id, wip_issue_item_id) WHERE wip_issue_item_id IS NOT NULL;
CREATE UNIQUE INDEX ux_stock_count_kitchen ON stock_count_items (stock_count_id, kitchen_batch_id) WHERE kitchen_batch_id IS NOT NULL;

-- Sổ kho bất biến: mỗi dòng đúng 1 vị trí tồn + chứng từ nguồn đúng loại (DB-20)
CREATE TABLE stock_movements (
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    movement_type            VARCHAR(30)   NOT NULL
                             CONSTRAINT ck_stock_movements_type CHECK (movement_type IN
                                 ('RECEIPT', 'ISSUE_OUT', 'ISSUE_REVERSAL', 'ISSUE_RECEIVED', 'DIRECT_IN',
                                  'PREP_INPUT', 'PREP_OUTPUT', 'COOK', 'RETURN', 'WASTE', 'COUNT_ADJUST')),
    raw_batch_id             BIGINT REFERENCES inventory_batches (id),
    wip_issue_item_id        BIGINT REFERENCES stock_issue_items (id),
    kitchen_batch_id         BIGINT REFERENCES kitchen_batches (id),
    quantity_delta           NUMERIC(12,3) NOT NULL,
    ref_issue_item_id        BIGINT REFERENCES stock_issue_items (id),
    preparation_id           BIGINT REFERENCES kitchen_preparations (id),
    order_item_id            BIGINT REFERENCES order_items (id),
    stock_count_item_id      BIGINT REFERENCES stock_count_items (id),
    stock_return_item_id     BIGINT REFERENCES stock_return_items (id),
    stock_adjustment_item_id BIGINT REFERENCES stock_adjustment_items (id),
    reason                   VARCHAR(255),
    event_key                VARCHAR(64)   NOT NULL UNIQUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_movement_one_source CHECK (num_nonnulls(raw_batch_id, wip_issue_item_id, kitchen_batch_id) = 1),
    CONSTRAINT ck_movement_type_shape CHECK (                                                             -- DB-20
        quantity_delta <> 0 AND
        CASE movement_type
            WHEN 'RECEIPT'        THEN raw_batch_id IS NOT NULL AND quantity_delta > 0
            WHEN 'ISSUE_OUT'      THEN raw_batch_id IS NOT NULL AND ref_issue_item_id IS NOT NULL AND quantity_delta < 0
            WHEN 'ISSUE_REVERSAL' THEN raw_batch_id IS NOT NULL AND ref_issue_item_id IS NOT NULL AND quantity_delta > 0
            WHEN 'ISSUE_RECEIVED' THEN wip_issue_item_id IS NOT NULL AND quantity_delta > 0
            WHEN 'DIRECT_IN'      THEN kitchen_batch_id IS NOT NULL AND ref_issue_item_id IS NOT NULL AND quantity_delta > 0
            WHEN 'PREP_INPUT'     THEN wip_issue_item_id IS NOT NULL AND preparation_id IS NOT NULL AND quantity_delta < 0
            WHEN 'PREP_OUTPUT'    THEN kitchen_batch_id IS NOT NULL AND preparation_id IS NOT NULL AND quantity_delta > 0
            WHEN 'COOK'           THEN kitchen_batch_id IS NOT NULL AND order_item_id IS NOT NULL AND quantity_delta < 0
            WHEN 'RETURN'         THEN stock_return_item_id IS NOT NULL
                                       AND ((raw_batch_id IS NOT NULL AND quantity_delta > 0)
                                         OR (raw_batch_id IS NULL AND quantity_delta < 0))
            WHEN 'WASTE'          THEN stock_adjustment_item_id IS NOT NULL AND quantity_delta < 0
            WHEN 'COUNT_ADJUST'   THEN stock_count_item_id IS NOT NULL
            ELSE false
        END)
);
CREATE INDEX idx_stock_movements_raw_audit ON stock_movements (raw_batch_id, created_at DESC) WHERE raw_batch_id IS NOT NULL;
CREATE INDEX idx_stock_movements_wip_audit ON stock_movements (wip_issue_item_id, created_at DESC) WHERE wip_issue_item_id IS NOT NULL;
CREATE INDEX idx_stock_movements_kitchen_audit ON stock_movements (kitchen_batch_id, created_at DESC) WHERE kitchen_batch_id IS NOT NULL;
CREATE INDEX brin_stock_movements_created ON stock_movements USING brin (created_at);                     -- DB-27
CREATE INDEX idx_stock_movements_order_item ON stock_movements (order_item_id) WHERE order_item_id IS NOT NULL;      -- DB-27
CREATE INDEX idx_stock_movements_preparation ON stock_movements (preparation_id) WHERE preparation_id IS NOT NULL;   -- DB-27

-- =====================================================================
-- 6. PHÂN HỆ 5 — Chi phí • báo cáo • đối soát (2 bảng)
-- =====================================================================
CREATE TABLE operating_expenses (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_date    DATE          NOT NULL,
    category         VARCHAR(30)   NOT NULL CONSTRAINT ck_expense_category CHECK (category IN          -- DB-30
                         ('INGREDIENT', 'SUPPLIES', 'UTILITY', 'RENT', 'MAINTENANCE', 'OTHER')),
    description      VARCHAR(255)  NOT NULL,
    amount           NUMERIC(12,2) NOT NULL CONSTRAINT ck_expense_amount_positive CHECK (amount > 0),     -- DB-30
    payment_method   VARCHAR(20)   NOT NULL
                     CONSTRAINT ck_expense_payment_method CHECK (payment_method IN ('CASH', 'BANK_TRANSFER')),  -- DB-30
    fund_source      VARCHAR(20)   NOT NULL
                     CONSTRAINT ck_expense_fund_source CHECK (fund_source IN ('CASH_DRAWER', 'OWNER', 'BANK')),  -- DB-30
    stock_receipt_id BIGINT REFERENCES stock_receipts (id),
    recorded_by_id   BIGINT        NOT NULL REFERENCES employees (id),
    paid_at          TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    request_key      VARCHAR(64)   NOT NULL UNIQUE,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PAID'
                     CONSTRAINT ck_operating_expenses_status CHECK (status IN ('PAID', 'VOIDED')),
    void_reason      VARCHAR(255),
    voided_by_id     BIGINT REFERENCES employees (id),
    voided_at        TIMESTAMPTZ,
    CONSTRAINT ck_expense_cash_drawer_is_cash CHECK (fund_source <> 'CASH_DRAWER' OR payment_method = 'CASH'),  -- DB-30
    CONSTRAINT ck_expense_void_fields CHECK (                                                             -- DB-30
        CASE WHEN status = 'VOIDED' THEN num_nonnulls(void_reason, voided_by_id, voided_at) = 3
             ELSE num_nonnulls(void_reason, voided_by_id, voided_at) = 0 END)
);
CREATE UNIQUE INDEX ux_expense_receipt_live ON operating_expenses (stock_receipt_id)
    WHERE stock_receipt_id IS NOT NULL AND status <> 'VOIDED';
CREATE INDEX idx_expense_period_category ON operating_expenses (business_date, category) WHERE status = 'PAID';

CREATE TABLE daily_cash_reconciliations (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_date    DATE          NOT NULL,
    version          INT           NOT NULL CONSTRAINT ck_cash_recon_version_positive CHECK (version > 0),
    status           VARCHAR(20)   NOT NULL DEFAULT 'CURRENT'
                     CONSTRAINT ck_cash_recon_status CHECK (status IN ('CURRENT', 'SUPERSEDED')),
    opening_cash     NUMERIC(12,2) NOT NULL,   -- DB-32: quỹ đầu ngày do Manager xác nhận
    cash_sales       NUMERIC(12,2) NOT NULL,
    cash_expenses    NUMERIC(12,2) NOT NULL,
    expected_cash    NUMERIC(12,2) GENERATED ALWAYS AS (opening_cash + cash_sales - cash_expenses) STORED,               -- DB-31
    actual_cash      NUMERIC(12,2) NOT NULL,
    difference       NUMERIC(12,2) GENERATED ALWAYS AS (actual_cash - (opening_cash + cash_sales - cash_expenses)) STORED,  -- DB-31
    reason           VARCHAR(255),
    revision_reason  VARCHAR(255),
    reconciled_by_id BIGINT        NOT NULL REFERENCES employees (id),
    reconciled_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_cash_recon_date_version UNIQUE (business_date, version),
    CONSTRAINT ck_recon_reason_when_diff CHECK (                                                          -- DB-31
        actual_cash = opening_cash + cash_sales - cash_expenses OR reason IS NOT NULL),
    CONSTRAINT ck_recon_revision_reason CHECK (version = 1 OR revision_reason IS NOT NULL)                -- DB-31
);
CREATE UNIQUE INDEX ux_cash_recon_current ON daily_cash_reconciliations (business_date) WHERE status = 'CURRENT';

-- =====================================================================
-- 7. DỮ LIỆU THAM CHIẾU VÀ HÀM TRỢ GIÚP
-- =====================================================================
-- 4 actor nội bộ (spec A1). `permissions` do migration của backend nạp (spec A2, mục M2).
INSERT INTO roles (role_code, role_name) VALUES
    ('MANAGER', 'Quản lý'), ('WAITER', 'Phục vụ'), ('KITCHEN', 'Bếp'), ('CASHIER', 'Thu ngân');

-- Ném lỗi có SQLSTATE riêng (RC001 RC403 RC409 RC422 RC500)
CREATE FUNCTION rc_raise(p_code TEXT, p_message TEXT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = p_code, MESSAGE = p_message;
END $$;

-- QĐ-BM: mọi hàm nghiệp vụ có p_actor gọi hàm này ĐẦU TIÊN (trước khi khóa hay đọc dữ liệu nghiệp vụ).
-- Đạt khi p_actor là nhân viên ACTIVE, có tài khoản ACTIVE mang ít nhất một vai trò trong p_roles; ngược lại RC403.
-- Ma trận hàm → vai trò ở docs/07 §10. p_actor do backend lấy từ principal đã xác thực; hàm này chặn lỗi ánh xạ
-- quyền (Waiter gọi hàm Manager, tài khoản bị khóa) chứ không thay việc xác thực danh tính.
CREATE FUNCTION rc_authorize(p_actor BIGINT, VARIADIC p_roles TEXT[]) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF p_actor IS NULL OR NOT EXISTS (
        SELECT 1
          FROM employees e
          JOIN accounts a ON a.employee_id = e.id AND a.account_status = 'ACTIVE'
          JOIN account_roles ar ON ar.account_id = a.id
          JOIN roles r ON r.id = ar.role_id
         WHERE e.id = p_actor AND e.work_status = 'ACTIVE' AND r.role_code = ANY (p_roles)) THEN
        PERFORM rc_raise('RC403', format('nhân viên %s không có vai trò %s đang hoạt động', p_actor, array_to_string(p_roles, '/')));
    END IF;
END $$;

-- QĐ-ID: băm payload tạo chứng từ. jsonb chuẩn hóa thứ tự khóa và khoảng trắng nên cùng nội dung → cùng hash.
CREATE FUNCTION rc_request_hash(p_payload JSONB) RETURNS CHAR(64)
LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(sha256(convert_to(p_payload::text, 'UTF8')), 'hex')
$$;

-- QĐ-ID: tra chứng từ đã tạo bằng request_key. Không có → NULL; có và cùng payload → id; khác payload → RC409.
-- Khóa tư vấn theo khóa yêu cầu tuần tự hóa hai lần gửi đồng thời cùng khóa (lần sau thấy bản lần trước đã commit).
CREATE FUNCTION rc_request_lookup(p_table TEXT, p_request_key TEXT, p_hash CHAR(64)) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_id   BIGINT;
    v_hash TEXT;
BEGIN
    IF p_request_key IS NULL OR btrim(p_request_key) = '' OR length(p_request_key) > 64 THEN
        PERFORM rc_raise('RC422', 'request_key bắt buộc, tối đa 64 ký tự');
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext(p_table), hashtext(p_request_key));
    EXECUTE format('SELECT id, request_hash FROM %I WHERE request_key = $1', p_table) INTO v_id, v_hash USING p_request_key;
    IF v_id IS NOT NULL AND v_hash <> p_hash THEN
        PERFORM rc_raise('RC409', format('request_key %s đã dùng cho một yêu cầu %s khác nội dung', p_request_key, p_table));
    END IF;
    RETURN v_id;
END $$;

-- QĐ-8: số suất đã chiếm của một dòng thực đơn ngày — SUY RA từ trạng thái, không lưu.
--   chiếm: ACCEPTED, COOKING, READY, SERVED, và CANCELLED sau khi đã COOKING (hủy ngoại lệ: không hoàn suất)
--   không chiếm: PENDING, REJECTED, CANCELLED trước COOKING (giải phóng đúng một lần vì rời tập chiếm một lần)
CREATE FUNCTION rc_quota_used(p_daily_menu_item BIGINT) RETURNS BIGINT
LANGUAGE sql AS $$
    SELECT COALESCE(SUM(oi.quantity), 0)::bigint
      FROM order_items oi
     WHERE oi.daily_menu_item_id = p_daily_menu_item
       AND (oi.status IN ('ACCEPTED', 'COOKING', 'READY', 'SERVED')
            OR (oi.status = 'CANCELLED'
                AND EXISTS (SELECT 1 FROM order_item_events e
                             WHERE e.order_item_id = oi.id AND e.to_status = 'COOKING')))
$$;

-- Số dư vật lý phải bằng tổng sổ kho; số đang giữ phải bằng tổng reservation ACTIVE (spec A3.4–A3.6).
-- Gọi từ các constraint trigger hoãn tới COMMIT → mọi đường ghi (kể cả DML trực tiếp) đều bị kiểm.
CREATE FUNCTION rc_check_balance(p_kind TEXT, p_id BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_book     NUMERIC;
    v_ledger   NUMERIC;
    v_reserved NUMERIC;
    v_active   NUMERIC;
BEGIN
    IF p_kind = 'RAW' THEN
        SELECT quantity_on_hand INTO v_book FROM inventory_batches WHERE id = p_id;
        SELECT COALESCE(SUM(quantity_delta), 0) INTO v_ledger FROM stock_movements WHERE raw_batch_id = p_id;
    ELSIF p_kind = 'WIP' THEN
        SELECT wip_remaining_quantity INTO v_book FROM stock_issue_items WHERE id = p_id;
        SELECT COALESCE(SUM(quantity_delta), 0) INTO v_ledger FROM stock_movements WHERE wip_issue_item_id = p_id;
    ELSE
        SELECT quantity_on_hand, reserved_quantity INTO v_book, v_reserved FROM kitchen_batches WHERE id = p_id;
        SELECT COALESCE(SUM(quantity_delta), 0) INTO v_ledger FROM stock_movements WHERE kitchen_batch_id = p_id;
        SELECT COALESCE(SUM(quantity), 0) INTO v_active
          FROM stock_reservations WHERE kitchen_batch_id = p_id AND status = 'ACTIVE';
        IF v_reserved IS DISTINCT FROM v_active THEN
            PERFORM rc_raise('RC500', format('lô bếp %s: reserved_quantity %s ≠ tổng reservation ACTIVE %s', p_id, v_reserved, v_active));
        END IF;
    END IF;
    IF v_book IS DISTINCT FROM v_ledger THEN
        PERFORM rc_raise('RC500', format('%s %s: số dư %s ≠ tổng sổ kho %s', p_kind, p_id, v_book, v_ledger));
    END IF;
END $$;

-- =====================================================================
-- 8. TRIGGER BẢO VỆ DỮ LIỆU
-- =====================================================================
-- 8.1 Bảng chỉ-thêm (spec A3.6): sổ kho, giao dịch thu tiền, nhật ký món, nhật ký chỉnh công
CREATE FUNCTION rc_forbid_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = 'RC409', MESSAGE = format('%s là dữ liệu chỉ-thêm (audit): không được %s', TG_TABLE_NAME, TG_OP);
END $$;
CREATE TRIGGER trg_stock_movements_append_only BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION rc_forbid_change();
CREATE TRIGGER trg_payments_append_only BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION rc_forbid_change();
CREATE TRIGGER trg_order_item_events_append_only BEFORE UPDATE OR DELETE ON order_item_events
    FOR EACH ROW EXECUTE FUNCTION rc_forbid_change();
CREATE TRIGGER trg_attendance_adjustments_append_only BEFORE UPDATE OR DELETE ON attendance_adjustments
    FOR EACH ROW EXECUTE FUNCTION rc_forbid_change();

-- 8.2 Dữ liệu giao dịch không xóa (đổi trạng thái / VOIDED thay vì xóa)
CREATE FUNCTION rc_forbid_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = 'RC409', MESSAGE = format('%s: không xóa dữ liệu giao dịch, dùng chuyển trạng thái', TG_TABLE_NAME);
END $$;
CREATE TRIGGER trg_orders_no_delete BEFORE DELETE ON orders FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_order_batches_no_delete BEFORE DELETE ON order_batches FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_order_items_no_delete BEFORE DELETE ON order_items FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_table_sessions_no_delete BEFORE DELETE ON table_sessions FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_payment_requests_no_delete BEFORE DELETE ON payment_requests FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_stock_reservations_no_delete BEFORE DELETE ON stock_reservations FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_inventory_batches_no_delete BEFORE DELETE ON inventory_batches FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_kitchen_batches_no_delete BEFORE DELETE ON kitchen_batches FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();
CREATE TRIGGER trg_operating_expenses_no_delete BEFORE DELETE ON operating_expenses FOR EACH ROW EXECUTE FUNCTION rc_forbid_delete();

-- 8.3 Thực đơn: công thức đã dùng bất biến (DB-06); dòng thực đơn ngày; trạng thái thực đơn
CREATE FUNCTION rc_guard_used_recipe() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE')
       AND EXISTS (SELECT 1 FROM daily_menu_items d WHERE d.recipe_id = OLD.recipe_id) THEN
        PERFORM rc_raise('RC409', format('recipe %s đã dùng trong daily_menu_items: tạo version mới thay vì sửa', OLD.recipe_id));
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE')
       AND EXISTS (SELECT 1 FROM daily_menu_items d WHERE d.recipe_id = NEW.recipe_id) THEN
        PERFORM rc_raise('RC409', format('recipe %s đã dùng trong daily_menu_items: tạo version mới thay vì sửa', NEW.recipe_id));
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_recipe_ingredients_guard BEFORE INSERT OR UPDATE OR DELETE ON recipe_ingredients
    FOR EACH ROW EXECUTE FUNCTION rc_guard_used_recipe();

-- QĐ-1/QĐ-8: không hạ quota xuống dưới số suất đã chiếm; đã có món gọi thì không đổi món/recipe của dòng
CREATE FUNCTION rc_guard_daily_menu_item() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (SELECT 1 FROM order_items WHERE daily_menu_item_id = OLD.id) THEN
            PERFORM rc_raise('RC409', format('daily_menu_items %s đã có món gọi: không xóa', OLD.id));
        END IF;
        RETURN OLD;
    END IF;
    IF (NEW.daily_menu_id, NEW.menu_item_id, NEW.recipe_id) IS DISTINCT FROM (OLD.daily_menu_id, OLD.menu_item_id, OLD.recipe_id)
       AND EXISTS (SELECT 1 FROM order_items WHERE daily_menu_item_id = OLD.id) THEN
        PERFORM rc_raise('RC409', format('daily_menu_items %s đã có món gọi: không đổi món/công thức', OLD.id));
    END IF;
    IF NEW.quota_total IS NOT NULL AND NEW.quota_total >= 0 THEN      -- số âm để CHECK ck_daily_menu_quota_nonneg báo lỗi
        IF NEW.quota_total < rc_quota_used(OLD.id) THEN
            PERFORM rc_raise('RC409', format('quota %s thấp hơn số suất đã nhận %s', NEW.quota_total, rc_quota_used(OLD.id)));
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_daily_menu_items_guard BEFORE UPDATE OR DELETE ON daily_menu_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_daily_menu_item();

CREATE FUNCTION rc_guard_daily_menu() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.business_date <> OLD.business_date THEN
        PERFORM rc_raise('RC409', 'daily_menus.business_date bất biến');
    END IF;
    IF NEW.status <> OLD.status THEN
        IF NOT ((OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED') OR (OLD.status = 'PUBLISHED' AND NEW.status = 'CLOSED')) THEN
            PERFORM rc_raise('RC409', format('thực đơn ngày: không chuyển %s → %s', OLD.status, NEW.status));
        END IF;
        IF NEW.status = 'PUBLISHED' AND NOT EXISTS (SELECT 1 FROM daily_menu_items WHERE daily_menu_id = OLD.id) THEN
            PERFORM rc_raise('RC409', 'không công bố thực đơn ngày chưa có món nào');
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_daily_menus_guard BEFORE UPDATE ON daily_menus
    FOR EACH ROW EXECUTE FUNCTION rc_guard_daily_menu();

-- 8.4 Bàn, phiên, đơn, dòng món (spec E2): chuyển trạng thái hợp lệ; không đóng bàn/đơn sai điều kiện
CREATE FUNCTION rc_guard_dining_table() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'OCCUPIED' AND OLD.status <> 'OCCUPIED'
       AND NOT EXISTS (SELECT 1 FROM table_sessions WHERE dining_table_id = OLD.id AND status = 'OPEN') THEN
        PERFORM rc_raise('RC409', format('bàn %s chỉ OCCUPIED khi có phiên OPEN', OLD.code));
    END IF;
    IF NEW.status = 'AVAILABLE' AND OLD.status <> 'AVAILABLE'
       AND EXISTS (SELECT 1 FROM table_sessions WHERE dining_table_id = OLD.id AND status = 'OPEN') THEN
        PERFORM rc_raise('RC409', format('bàn %s còn phiên OPEN: chưa trả bàn được', OLD.code));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_dining_tables_guard BEFORE UPDATE ON dining_tables
    FOR EACH ROW EXECUTE FUNCTION rc_guard_dining_table();

CREATE FUNCTION rc_guard_table_session() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.dining_table_id, NEW.opened_at, NEW.opened_by_id) IS DISTINCT FROM (OLD.dining_table_id, OLD.opened_at, OLD.opened_by_id) THEN
        PERFORM rc_raise('RC409', 'table_sessions: bàn, giờ mở, người mở bất biến');
    END IF;
    IF OLD.status = 'CLOSED' AND NEW IS DISTINCT FROM OLD THEN
        PERFORM rc_raise('RC409', format('phiên %s đã đóng: bất biến', OLD.id));
    END IF;
    IF NEW.status = 'CLOSED' AND OLD.status = 'OPEN'
       AND EXISTS (SELECT 1 FROM orders WHERE table_session_id = OLD.id AND status = 'OPEN') THEN
        PERFORM rc_raise('RC409', format('phiên %s: đơn còn OPEN, chưa đóng phiên được', OLD.id));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_table_sessions_guard BEFORE UPDATE ON table_sessions
    FOR EACH ROW EXECUTE FUNCTION rc_guard_table_session();

CREATE FUNCTION rc_guard_order() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.table_session_id <> OLD.table_session_id THEN
        PERFORM rc_raise('RC409', 'orders.table_session_id bất biến');
    END IF;
    IF NEW.status <> OLD.status THEN
        IF OLD.status <> 'OPEN' THEN
            PERFORM rc_raise('RC409', format('đơn %s đã %s: bất biến', OLD.id, OLD.status));
        END IF;
        -- CLOSED = kết thúc có hóa đơn ở trạng thái cuối: PAID, UNCOLLECTED (QĐ-3) hoặc WAIVED (QĐ-MT)
        IF NEW.status = 'CLOSED' AND NOT EXISTS (SELECT 1 FROM invoices WHERE order_id = OLD.id AND status IN ('PAID', 'UNCOLLECTED', 'WAIVED')) THEN
            PERFORM rc_raise('RC409', format('đơn %s chỉ đóng sau khi hóa đơn PAID, UNCOLLECTED hoặc WAIVED', OLD.id));
        END IF;
        -- CANCELLED = phiên không phát sinh chứng từ thu: không có hóa đơn (kể cả DRAFT — QĐ-MT không để hóa đơn treo)
        IF NEW.status = 'CANCELLED' AND (
               EXISTS (SELECT 1 FROM invoices WHERE order_id = OLD.id)
            OR EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                        WHERE b.order_id = OLD.id AND oi.status NOT IN ('CANCELLED', 'REJECTED'))) THEN
            PERFORM rc_raise('RC409', format('đơn %s còn món hoặc đã có hóa đơn: không hủy phiên trống được', OLD.id));
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_orders_guard BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION rc_guard_order();

-- Dòng món: snapshot bất biến; chỉ các bước chuyển trạng thái của spec E2 + hủy ngoại lệ (QĐ-2)
CREATE FUNCTION rc_guard_order_item() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.order_batch_id, NEW.daily_menu_item_id, NEW.quantity, NEW.item_name_snapshot, NEW.unit_price)
       IS DISTINCT FROM
       (OLD.order_batch_id, OLD.daily_menu_item_id, OLD.quantity, OLD.item_name_snapshot, OLD.unit_price) THEN
        PERFORM rc_raise('RC409', format('order_items %s: tên, giá, số lượng, món là snapshot bất biến', OLD.id));
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'PENDING'  AND NEW.status IN ('ACCEPTED', 'REJECTED', 'CANCELLED'))
        OR (OLD.status = 'ACCEPTED' AND NEW.status IN ('COOKING', 'CANCELLED'))
        OR (OLD.status = 'COOKING'  AND NEW.status IN ('READY', 'CANCELLED'))   -- COOKING→CANCELLED: hủy ngoại lệ của Manager
        OR (OLD.status = 'READY'    AND NEW.status IN ('SERVED', 'CANCELLED'))
        OR (OLD.status = 'SERVED'   AND NEW.status = 'CANCELLED')) THEN
        PERFORM rc_raise('RC409', format('order_items %s: không chuyển %s → %s', OLD.id, OLD.status, NEW.status));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_order_items_guard BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_order_item();

-- Nhật ký món: sự kiện nối đúng chuỗi và khớp trạng thái hiện tại của dòng (QĐ-2: có người, lý do, lịch sử)
CREATE FUNCTION rc_guard_order_item_event() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_last   TEXT;
    v_status TEXT;
BEGIN
    SELECT to_status INTO v_last FROM order_item_events WHERE order_item_id = NEW.order_item_id ORDER BY id DESC LIMIT 1;
    SELECT status INTO v_status FROM order_items WHERE id = NEW.order_item_id;
    IF NEW.from_status IS DISTINCT FROM v_last THEN
        PERFORM rc_raise('RC409', format('order_item_events: from_status %s không nối tiếp sự kiện trước (%s)', NEW.from_status, v_last));
    END IF;
    IF NEW.to_status IS DISTINCT FROM v_status THEN
        PERFORM rc_raise('RC409', format('order_item_events: to_status %s phải khớp trạng thái hiện tại của dòng món (%s)', NEW.to_status, v_status));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_order_item_events_chain BEFORE INSERT ON order_item_events
    FOR EACH ROW EXECUTE FUNCTION rc_guard_order_item_event();

-- Đổi trạng thái dòng món mà không ghi sự kiện bị bắt ở COMMIT
CREATE FUNCTION rc_check_item_event_sync() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    v_last   TEXT;
BEGIN
    SELECT status INTO v_status FROM order_items WHERE id = NEW.id;
    SELECT to_status INTO v_last FROM order_item_events WHERE order_item_id = NEW.id ORDER BY id DESC LIMIT 1;
    IF v_last IS DISTINCT FROM v_status THEN
        PERFORM rc_raise('RC500', format('order_items %s: trạng thái %s không có sự kiện tương ứng (sự kiện cuối: %s)', NEW.id, v_status, v_last));
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_order_items_event_sync AFTER INSERT OR UPDATE OF status ON order_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_check_item_event_sync();

-- 8.5 Hóa đơn và thanh toán
-- Hóa đơn: DRAFT → PAYMENT_PENDING → PAID; PAYMENT_PENDING → DRAFT (mở lại);
-- DRAFT/PAYMENT_PENDING → UNCOLLECTED (QĐ-3) hoặc WAIVED (QĐ-MT). PAID, UNCOLLECTED, WAIVED là cuối;
-- PAID bắt buộc có payments khớp tổng; UNCOLLECTED/WAIVED không được có payments; PAID không thể thành WAIVED.
CREATE FUNCTION rc_guard_invoice() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM rc_raise('RC409', 'invoices: không xóa hóa đơn');
    END IF;
    IF (NEW.order_id, NEW.created_at, NEW.created_by_id) IS DISTINCT FROM (OLD.order_id, OLD.created_at, OLD.created_by_id) THEN
        PERFORM rc_raise('RC409', format('invoice %s: đơn, người lập, giờ lập bất biến', OLD.id));
    END IF;
    IF OLD.status IN ('PAID', 'UNCOLLECTED', 'WAIVED') THEN
        IF NEW IS DISTINCT FROM OLD THEN
            PERFORM rc_raise('RC409', format('invoice %s đã %s: bất biến', OLD.id, OLD.status));
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'DRAFT' AND NEW.status IN ('PAYMENT_PENDING', 'UNCOLLECTED', 'WAIVED'))
        OR (OLD.status = 'PAYMENT_PENDING' AND NEW.status IN ('DRAFT', 'PAID', 'UNCOLLECTED', 'WAIVED'))) THEN
        PERFORM rc_raise('RC409', format('invoice %s: không chuyển %s → %s', OLD.id, OLD.status, NEW.status));
    END IF;
    IF OLD.status = 'PAYMENT_PENDING' AND NEW.total <> OLD.total THEN
        PERFORM rc_raise('RC409', format('invoice %s đang PAYMENT_PENDING: không đổi total', OLD.id));
    END IF;
    IF NEW.status = 'PAID' AND NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = OLD.id AND amount = NEW.total) THEN
        PERFORM rc_raise('RC409', format('invoice %s chỉ PAID khi có payments bằng đúng số phải thu', OLD.id));
    END IF;
    IF NEW.status IN ('UNCOLLECTED', 'WAIVED') AND EXISTS (SELECT 1 FROM payments WHERE invoice_id = OLD.id) THEN
        PERFORM rc_raise('RC409', format('invoice %s đã có payments: không thể %s', OLD.id, NEW.status));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_guard BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION rc_guard_invoice();

-- Dòng hóa đơn: chỉ khi hóa đơn DRAFT; luôn là bản chụp đúng của một dòng SERVED cùng đơn (QĐ-4)
CREATE FUNCTION rc_guard_invoice_items() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
    v_item    RECORD;
BEGIN
    SELECT * INTO v_invoice FROM invoices
     WHERE id = CASE WHEN TG_OP = 'INSERT' THEN NEW.invoice_id ELSE OLD.invoice_id END;
    IF v_invoice.status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('invoice %s đang %s: invoice_items bị khóa', v_invoice.id, v_invoice.status));
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    SELECT oi.status, oi.item_name_snapshot, oi.quantity, oi.unit_price, b.order_id INTO v_item
      FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE oi.id = NEW.order_item_id;
    IF v_item.order_id IS DISTINCT FROM v_invoice.order_id OR v_item.status IS DISTINCT FROM 'SERVED' THEN
        PERFORM rc_raise('RC409', format('invoice_items: dòng món %s không phải dòng SERVED của đơn %s', NEW.order_item_id, v_invoice.order_id));
    END IF;
    IF (NEW.item_name, NEW.quantity, NEW.unit_price) IS DISTINCT FROM (v_item.item_name_snapshot, v_item.quantity, v_item.unit_price) THEN
        PERFORM rc_raise('RC409', format('invoice_items: dòng %s phải là bản chụp đúng của order_items', NEW.order_item_id));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_items_guard BEFORE INSERT OR UPDATE OR DELETE ON invoice_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_invoice_items();

-- Yêu cầu thanh toán: chỉ tạo cho hóa đơn PAYMENT_PENDING, đúng số phải thu; cột lõi bất biến; trạng thái cuối khóa
CREATE FUNCTION rc_guard_payment_request() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT * INTO v_invoice FROM invoices WHERE id = NEW.invoice_id;
        IF v_invoice.status <> 'PAYMENT_PENDING' THEN
            PERFORM rc_raise('RC409', format('chỉ tạo yêu cầu thanh toán cho hóa đơn PAYMENT_PENDING (hóa đơn %s đang %s)', NEW.invoice_id, v_invoice.status));
        END IF;
        IF NEW.amount_due <> v_invoice.total THEN
            PERFORM rc_raise('RC409', format('amount_due %s phải bằng invoices.total %s', NEW.amount_due, v_invoice.total));
        END IF;
        IF NEW.status = 'CANCELLED' THEN
            PERFORM rc_raise('RC409', 'không tạo yêu cầu thanh toán ở trạng thái CANCELLED');
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IN ('PAID', 'CANCELLED') THEN
        PERFORM rc_raise('RC409', format('yêu cầu thanh toán %s đã %s: bất biến', OLD.id, OLD.status));
    END IF;
    IF (NEW.invoice_id, NEW.amount_due, NEW.cash_received, NEW.method, NEW.payment_reference, NEW.cashier_id, NEW.created_at)
       IS DISTINCT FROM
       (OLD.invoice_id, OLD.amount_due, OLD.cash_received, OLD.method, OLD.payment_reference, OLD.cashier_id, OLD.created_at) THEN
        PERFORM rc_raise('RC409', format('yêu cầu thanh toán %s: số tiền, phương thức, mã tham chiếu bất biến', OLD.id));
    END IF;
    IF NEW.status <> OLD.status AND NOT (
           (OLD.status = 'REQUESTED' AND NEW.status IN ('WAITING_TRANSFER', 'PAID', 'CANCELLED'))
        OR (OLD.status = 'WAITING_TRANSFER' AND NEW.status IN ('PAID', 'CANCELLED'))) THEN
        PERFORM rc_raise('RC409', format('yêu cầu thanh toán %s: không chuyển %s → %s', OLD.id, OLD.status, NEW.status));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_payment_requests_guard BEFORE INSERT OR UPDATE ON payment_requests
    FOR EACH ROW EXECUTE FUNCTION rc_guard_payment_request();

-- Giao dịch thu tiền: chỉ ghi cho hóa đơn PAYMENT_PENDING, bằng đúng số phải thu, đúng ngày kinh doanh
CREATE FUNCTION rc_guard_payment() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
    v_request TEXT;
BEGIN
    SELECT * INTO v_invoice FROM invoices WHERE id = NEW.invoice_id;
    IF v_invoice.status <> 'PAYMENT_PENDING' THEN
        PERFORM rc_raise('RC409', format('payments: hóa đơn %s đang %s, chỉ thu được khi PAYMENT_PENDING', NEW.invoice_id, v_invoice.status));
    END IF;
    IF NEW.amount <> v_invoice.total THEN
        PERFORM rc_raise('RC409', format('payments.amount %s phải bằng số phải thu %s', NEW.amount, v_invoice.total));
    END IF;
    IF NEW.business_date <> rc_business_date(NEW.paid_at) THEN
        PERFORM rc_raise('RC409', 'payments.business_date phải theo quy tắc ngày kinh doanh của paid_at');
    END IF;
    SELECT status INTO v_request FROM payment_requests WHERE id = NEW.payment_request_id;
    IF v_request NOT IN ('WAITING_TRANSFER', 'PAID') THEN
        PERFORM rc_raise('RC409', format('payments: yêu cầu thanh toán %s đang %s', NEW.payment_request_id, v_request));
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_payments_guard BEFORE INSERT ON payments
    FOR EACH ROW EXECUTE FUNCTION rc_guard_payment();

-- Webhook: chứng cứ bất biến, chỉ cập nhật cột xử lý sự cố; sự cố mới tự mở (OPEN) — DB-12
CREATE FUNCTION rc_guard_webhook_event() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM rc_raise('RC409', 'payment_webhook_events là chứng cứ audit, không được xóa');
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.processing_result <> 'PROCESSED' AND NEW.resolution_status IS NULL THEN
            NEW.resolution_status := 'OPEN';
        END IF;
    ELSIF (NEW.payment_request_id, NEW.provider_transaction_code, NEW.payment_reference, NEW.amount,
           NEW.processing_result, NEW.received_at, NEW.raw_payload)
          IS DISTINCT FROM
          (OLD.payment_request_id, OLD.provider_transaction_code, OLD.payment_reference, OLD.amount,
           OLD.processing_result, OLD.received_at, OLD.raw_payload) THEN
        PERFORM rc_raise('RC409', 'payment_webhook_events: chỉ được cập nhật các cột resolution_*');
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_webhook_event_guard BEFORE INSERT OR UPDATE OR DELETE ON payment_webhook_events
    FOR EACH ROW EXECUTE FUNCTION rc_guard_webhook_event();

-- 8.6 Kho: số dư luôn bằng sổ (kiểm ở COMMIT); đặt giữ chỉ ACTIVE → CONSUMED/RELEASED
CREATE FUNCTION rc_trg_balance() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    CASE TG_ARGV[0]
        WHEN 'RAW'         THEN PERFORM rc_check_balance('RAW', NEW.id);
        WHEN 'WIP'         THEN PERFORM rc_check_balance('WIP', NEW.id);
        WHEN 'KITCHEN'     THEN PERFORM rc_check_balance('KITCHEN', NEW.id);
        WHEN 'RESERVATION' THEN PERFORM rc_check_balance('KITCHEN', NEW.kitchen_batch_id);
        ELSE
            IF NEW.raw_batch_id IS NOT NULL THEN PERFORM rc_check_balance('RAW', NEW.raw_batch_id);
            ELSIF NEW.wip_issue_item_id IS NOT NULL THEN PERFORM rc_check_balance('WIP', NEW.wip_issue_item_id);
            ELSE PERFORM rc_check_balance('KITCHEN', NEW.kitchen_batch_id);
            END IF;
    END CASE;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_inventory_batches_balance AFTER INSERT OR UPDATE OF quantity_on_hand ON inventory_batches
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_trg_balance('RAW');
CREATE CONSTRAINT TRIGGER trg_stock_issue_items_balance AFTER INSERT OR UPDATE OF wip_remaining_quantity ON stock_issue_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_trg_balance('WIP');
CREATE CONSTRAINT TRIGGER trg_kitchen_batches_balance AFTER INSERT OR UPDATE OF quantity_on_hand, reserved_quantity ON kitchen_batches
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_trg_balance('KITCHEN');
CREATE CONSTRAINT TRIGGER trg_stock_reservations_balance AFTER INSERT OR UPDATE ON stock_reservations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_trg_balance('RESERVATION');
CREATE CONSTRAINT TRIGGER trg_stock_movements_balance AFTER INSERT ON stock_movements
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rc_trg_balance('MOVEMENT');

CREATE FUNCTION rc_guard_reservation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'ACTIVE' THEN
        PERFORM rc_raise('RC409', format('stock_reservations %s đã %s: bất biến', OLD.id, OLD.status));
    END IF;
    IF (NEW.order_item_id, NEW.kitchen_batch_id) IS DISTINCT FROM (OLD.order_item_id, OLD.kitchen_batch_id) THEN
        PERFORM rc_raise('RC409', 'stock_reservations: món và lô bếp bất biến');
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_stock_reservations_guard BEFORE UPDATE ON stock_reservations
    FOR EACH ROW EXECUTE FUNCTION rc_guard_reservation();

-- 8.7 Chứng từ kho: header DRAFT → CONFIRMED/CANCELLED (phiếu xuất: DRAFT → ISSUED → RECEIVED, hủy được trước RECEIVED);
--     trạng thái cuối bất biến; dòng chi tiết chỉ sửa khi header còn DRAFT (spec E2: chứng từ CONFIRMED bất biến)
CREATE FUNCTION rc_guard_doc_header() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_ok BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            PERFORM rc_raise('RC409', format('%s %s đã %s: không xóa', TG_TABLE_NAME, OLD.id, OLD.status));
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = NEW.status THEN
        IF OLD.status <> 'DRAFT' AND NEW IS DISTINCT FROM OLD THEN
            PERFORM rc_raise('RC409', format('%s %s đã %s: bất biến', TG_TABLE_NAME, OLD.id, OLD.status));
        END IF;
        RETURN NEW;
    END IF;
    v_ok := CASE TG_TABLE_NAME
        WHEN 'stock_issues' THEN (OLD.status = 'DRAFT' AND NEW.status IN ('ISSUED', 'CANCELLED'))
                              OR (OLD.status = 'ISSUED' AND NEW.status IN ('RECEIVED', 'CANCELLED'))
        ELSE OLD.status = 'DRAFT' AND NEW.status IN ('CONFIRMED', 'CANCELLED')
    END;
    IF NOT v_ok THEN
        PERFORM rc_raise('RC409', format('%s %s: không chuyển %s → %s', TG_TABLE_NAME, OLD.id, OLD.status, NEW.status));
    END IF;
    -- Chuyển sang trạng thái có hiệu lực kho chỉ hợp lệ khi bút toán sổ kho của MỌI dòng đã được ghi trước
    -- (hàm rc_* ghi sổ rồi mới đổi trạng thái). Chặn UPDATE trạng thái trực tiếp tạo/xóa tồn "từ không khí",
    -- ví dụ đặt phiếu xuất ISSUED mà RAW chưa bị trừ rồi để bếp nhận.
    IF rc_doc_effect_missing(TG_TABLE_NAME, OLD.id, OLD.status, NEW.status) THEN
        PERFORM rc_raise('RC409', format('%s %s → %s: thiếu bút toán sổ kho của chứng từ — chỉ chuyển bằng hàm rc_*',
                                         TG_TABLE_NAME, OLD.id, NEW.status));
    END IF;
    RETURN NEW;
END $$;

-- true khi chứng từ chuyển sang trạng thái có hiệu lực kho mà còn dòng chưa có bút toán (event_key) tương ứng,
-- hoặc chứng từ không có dòng nào. Chuyển không có hiệu lực kho (DRAFT → CANCELLED) luôn false.
CREATE FUNCTION rc_doc_effect_missing(p_table TEXT, p_id BIGINT, p_from TEXT, p_to TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql AS $$
BEGIN
    RETURN CASE
        WHEN p_table = 'stock_receipts' AND p_to = 'CONFIRMED' THEN
            NOT EXISTS (SELECT 1 FROM stock_receipt_items WHERE stock_receipt_id = p_id)
            OR EXISTS (SELECT 1 FROM stock_receipt_items l WHERE l.stock_receipt_id = p_id
                          AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'RECEIPT:' || l.id))
        WHEN p_table = 'stock_issues' AND p_to = 'ISSUED' THEN
            NOT EXISTS (SELECT 1 FROM stock_issue_items WHERE stock_issue_id = p_id)
            OR EXISTS (SELECT 1 FROM stock_issue_items l WHERE l.stock_issue_id = p_id
                          AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'ISSUE_OUT:' || l.id))
        WHEN p_table = 'stock_issues' AND p_to = 'RECEIVED' THEN
            EXISTS (SELECT 1 FROM stock_issue_items l WHERE l.stock_issue_id = p_id
                       AND NOT EXISTS (SELECT 1 FROM stock_movements m
                                        WHERE m.event_key IN ('ISSUE_RECEIVED:' || l.id, 'DIRECT_IN:' || l.id)))
        WHEN p_table = 'stock_issues' AND p_from = 'ISSUED' AND p_to = 'CANCELLED' THEN
            EXISTS (SELECT 1 FROM stock_issue_items l WHERE l.stock_issue_id = p_id
                       AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'ISSUE_REVERSAL:' || l.id))
        WHEN p_table = 'kitchen_preparations' AND p_to = 'CONFIRMED' THEN
            NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'PREP_INPUT:' || p_id)
        WHEN p_table = 'stock_returns' AND p_to = 'CONFIRMED' THEN
            NOT EXISTS (SELECT 1 FROM stock_return_items WHERE stock_return_id = p_id)
            OR EXISTS (SELECT 1 FROM stock_return_items l WHERE l.stock_return_id = p_id
                          AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'RETURN-IN:' || l.id))
        WHEN p_table = 'stock_adjustments' AND p_to = 'CONFIRMED' THEN
            NOT EXISTS (SELECT 1 FROM stock_adjustment_items WHERE stock_adjustment_id = p_id)
            OR EXISTS (SELECT 1 FROM stock_adjustment_items l WHERE l.stock_adjustment_id = p_id
                          AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'WASTE:' || l.id))
        WHEN p_table = 'stock_counts' AND p_to = 'CONFIRMED' THEN
            NOT EXISTS (SELECT 1 FROM stock_count_items WHERE stock_count_id = p_id)
            OR EXISTS (SELECT 1 FROM stock_count_items l WHERE l.stock_count_id = p_id AND l.actual_quantity <> l.book_quantity
                          AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'COUNT:' || l.id))
        ELSE false
    END;
END $$;
CREATE TRIGGER trg_stock_receipts_guard BEFORE UPDATE OR DELETE ON stock_receipts
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();
CREATE TRIGGER trg_stock_issues_guard BEFORE UPDATE OR DELETE ON stock_issues
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();
CREATE TRIGGER trg_kitchen_preparations_guard BEFORE UPDATE OR DELETE ON kitchen_preparations
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();
CREATE TRIGGER trg_stock_returns_guard BEFORE UPDATE OR DELETE ON stock_returns
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();
CREATE TRIGGER trg_stock_adjustments_guard BEFORE UPDATE OR DELETE ON stock_adjustments
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();
CREATE TRIGGER trg_stock_counts_guard BEFORE UPDATE OR DELETE ON stock_counts
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_header();

-- TG_ARGV[0] = bảng header, TG_ARGV[1] = cột khóa ngoại tới header
CREATE FUNCTION rc_guard_doc_lines() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_parent BIGINT;
    v_status TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        EXECUTE format('SELECT ($1).%I', TG_ARGV[1]) INTO v_parent USING OLD;
    ELSE
        EXECUTE format('SELECT ($1).%I', TG_ARGV[1]) INTO v_parent USING NEW;
    END IF;
    EXECUTE format('SELECT status FROM %I WHERE id = $1', TG_ARGV[0]) INTO v_status USING v_parent;
    IF v_status <> 'DRAFT' THEN
        -- ngoại lệ duy nhất: số WIP còn lại của dòng xuất đổi theo nhận/sơ chế/hoàn trả/hủy hàng/kiểm kê.
        -- IF lồng nhau (không dùng AND): plpgsql không đảm bảo đoản mạch nên NEW.stock_issue_id sẽ bị đánh giá trên bảng khác.
        IF TG_TABLE_NAME = 'stock_issue_items' THEN
            IF TG_OP = 'UPDATE' THEN
                IF (NEW.stock_issue_id, NEW.inventory_batch_id, NEW.quantity, NEW.fulfillment_mode)
                   IS NOT DISTINCT FROM (OLD.stock_issue_id, OLD.inventory_batch_id, OLD.quantity, OLD.fulfillment_mode) THEN
                    RETURN NEW;
                END IF;
            END IF;
        END IF;
        PERFORM rc_raise('RC409', format('%s: chứng từ %s %s đã %s, dòng chi tiết bị khóa', TG_TABLE_NAME, TG_ARGV[0], v_parent, v_status));
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER trg_stock_receipt_items_lines BEFORE INSERT OR UPDATE OR DELETE ON stock_receipt_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('stock_receipts', 'stock_receipt_id');
CREATE TRIGGER trg_stock_issue_items_lines BEFORE INSERT OR UPDATE OR DELETE ON stock_issue_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('stock_issues', 'stock_issue_id');
CREATE TRIGGER trg_kitchen_preparation_outputs_lines BEFORE INSERT OR UPDATE OR DELETE ON kitchen_preparation_outputs
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('kitchen_preparations', 'kitchen_preparation_id');
CREATE TRIGGER trg_stock_return_items_lines BEFORE INSERT OR UPDATE OR DELETE ON stock_return_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('stock_returns', 'stock_return_id');
CREATE TRIGGER trg_stock_adjustment_items_lines BEFORE INSERT OR UPDATE OR DELETE ON stock_adjustment_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('stock_adjustments', 'stock_adjustment_id');
CREATE TRIGGER trg_stock_count_items_lines BEFORE INSERT OR UPDATE OR DELETE ON stock_count_items
    FOR EACH ROW EXECUTE FUNCTION rc_guard_doc_lines('stock_counts', 'stock_count_id');

-- 8.8 Tài chính: khoản chi theo đúng ngày kinh doanh của paid_at, chỉ PAID → VOIDED; đối soát là snapshot
CREATE FUNCTION rc_guard_expense() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.business_date <> rc_business_date(NEW.paid_at) THEN
        PERFORM rc_raise('RC409', 'operating_expenses.business_date phải theo quy tắc ngày kinh doanh của paid_at');
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'VOIDED' THEN
            PERFORM rc_raise('RC409', format('khoản chi %s đã VOIDED: bất biến', OLD.id));
        END IF;
        IF (NEW.business_date, NEW.category, NEW.description, NEW.amount, NEW.payment_method, NEW.fund_source,
            NEW.stock_receipt_id, NEW.recorded_by_id, NEW.paid_at, NEW.created_at, NEW.request_key)
           IS DISTINCT FROM
           (OLD.business_date, OLD.category, OLD.description, OLD.amount, OLD.payment_method, OLD.fund_source,
            OLD.stock_receipt_id, OLD.recorded_by_id, OLD.paid_at, OLD.created_at, OLD.request_key) THEN
            PERFORM rc_raise('RC409', format('khoản chi %s: chỉ được hủy (VOIDED), không sửa nội dung', OLD.id));
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_operating_expenses_guard BEFORE INSERT OR UPDATE ON operating_expenses
    FOR EACH ROW EXECUTE FUNCTION rc_guard_expense();

CREATE FUNCTION rc_guard_cash_recon() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM rc_raise('RC409', 'daily_cash_reconciliations: không xóa bản đối soát');
    END IF;
    IF NOT (OLD.status = 'CURRENT' AND NEW.status = 'SUPERSEDED')
       OR (NEW.business_date, NEW.version, NEW.opening_cash, NEW.cash_sales, NEW.cash_expenses, NEW.actual_cash,
           NEW.reason, NEW.revision_reason, NEW.reconciled_by_id, NEW.reconciled_at)
          IS DISTINCT FROM
          (OLD.business_date, OLD.version, OLD.opening_cash, OLD.cash_sales, OLD.cash_expenses, OLD.actual_cash,
           OLD.reason, OLD.revision_reason, OLD.reconciled_by_id, OLD.reconciled_at) THEN
        PERFORM rc_raise('RC409', 'daily_cash_reconciliations: chỉ được chuyển CURRENT → SUPERSEDED, không sửa số liệu');
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_cash_recon_guard BEFORE UPDATE OR DELETE ON daily_cash_reconciliations
    FOR EACH ROW EXECUTE FUNCTION rc_guard_cash_recon();

-- =====================================================================
-- 9. HÀM NỘI BỘ DÙNG CHUNG CHO CÁC HÀM NGHIỆP VỤ
-- =====================================================================
-- Đổi trạng thái dòng món + ghi nhật ký (một chỗ duy nhất). Khóa dòng món (mức 5).
CREATE FUNCTION rc_item_transition(p_item BIGINT, p_from TEXT[], p_to TEXT, p_actor BIGINT, p_reason TEXT DEFAULT NULL)
RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    v_old TEXT;
BEGIN
    SELECT status INTO v_old FROM order_items WHERE id = p_item FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('order_item %s không tồn tại', p_item));
    END IF;
    IF NOT (v_old = ANY (p_from)) THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s: không chuyển sang %s được', p_item, v_old, p_to));
    END IF;
    UPDATE order_items SET status = p_to WHERE id = p_item;
    INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status, reason)
    VALUES (p_item, p_actor, v_old, p_to, p_reason);
    RETURN v_old;
END $$;

-- Đặt giữ phần còn thiếu của một món theo FEFO. Khóa mọi lô bếp có thể dùng của các loại trong công thức
-- theo (loại, id) tăng dần (mức 9); thiếu bất kỳ nguyên liệu nào → RC001 (người gọi rollback phần đã giữ).
CREATE FUNCTION rc_allocate_item(p_item BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    r_need RECORD;
    r_lot  RECORD;
    v_left NUMERIC;
    v_take NUMERIC;
BEGIN
    PERFORM 1
      FROM kitchen_batches kb
     WHERE kb.kitchen_ingredient_type_id IN (
               SELECT ri.kitchen_ingredient_type_id
                 FROM order_items oi
                 JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
                 JOIN recipe_ingredients ri ON ri.recipe_id = d.recipe_id
                WHERE oi.id = p_item)
       AND kb.is_usable AND kb.quantity_on_hand > 0
       AND (kb.usable_until IS NULL OR kb.usable_until > now())
     ORDER BY kb.kitchen_ingredient_type_id, kb.id
       FOR UPDATE;
    FOR r_need IN
        SELECT ri.kitchen_ingredient_type_id AS type_id,
               oi.quantity * ri.quantity_per_serving
               - COALESCE((SELECT SUM(sr.quantity)
                             FROM stock_reservations sr
                             JOIN kitchen_batches kb ON kb.id = sr.kitchen_batch_id
                            WHERE sr.order_item_id = oi.id AND sr.status = 'ACTIVE'
                              AND kb.kitchen_ingredient_type_id = ri.kitchen_ingredient_type_id), 0) AS missing
          FROM order_items oi
          JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
          JOIN recipe_ingredients ri ON ri.recipe_id = d.recipe_id
         WHERE oi.id = p_item
         ORDER BY ri.kitchen_ingredient_type_id
    LOOP
        v_left := r_need.missing;
        CONTINUE WHEN v_left <= 0;
        FOR r_lot IN
            SELECT kb.id, kb.quantity_on_hand - kb.reserved_quantity AS free
              FROM kitchen_batches kb
             WHERE kb.kitchen_ingredient_type_id = r_need.type_id AND kb.is_usable
               AND kb.quantity_on_hand > kb.reserved_quantity
               AND (kb.usable_until IS NULL OR kb.usable_until > now())
               AND NOT EXISTS (SELECT 1 FROM stock_reservations x
                                WHERE x.order_item_id = p_item AND x.kitchen_batch_id = kb.id
                                  AND x.status <> 'ACTIVE')
             ORDER BY kb.usable_until ASC NULLS LAST, kb.id
        LOOP
            EXIT WHEN v_left <= 0;
            v_take := LEAST(v_left, r_lot.free);
            UPDATE kitchen_batches SET reserved_quantity = reserved_quantity + v_take WHERE id = r_lot.id;
            INSERT INTO stock_reservations (order_item_id, kitchen_batch_id, quantity)
            VALUES (p_item, r_lot.id, v_take)
            ON CONFLICT (order_item_id, kitchen_batch_id)
            DO UPDATE SET quantity = stock_reservations.quantity + EXCLUDED.quantity;
            v_left := v_left - v_take;
        END LOOP;
        IF v_left > 0 THEN
            RAISE EXCEPTION USING ERRCODE = 'RC001',
                MESSAGE = format('Thiếu nguyên liệu: loại bếp %s còn thiếu %s', r_need.type_id, v_left);
        END IF;
    END LOOP;
END $$;

-- Giải phóng mọi phần giữ ACTIVE của một món (hủy trước COOKING, khách bỏ về). Không tạo biến động vật lý.
CREATE FUNCTION rc_release_item_reservations(p_item BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
BEGIN
    PERFORM 1 FROM kitchen_batches
     WHERE id IN (SELECT kitchen_batch_id FROM stock_reservations WHERE order_item_id = p_item AND status = 'ACTIVE')
     ORDER BY kitchen_ingredient_type_id, id FOR UPDATE;
    FOR r IN SELECT id, kitchen_batch_id, quantity FROM stock_reservations
              WHERE order_item_id = p_item AND status = 'ACTIVE' ORDER BY kitchen_batch_id
    LOOP
        UPDATE kitchen_batches SET reserved_quantity = reserved_quantity - r.quantity WHERE id = r.kitchen_batch_id;
        UPDATE stock_reservations SET status = 'RELEASED', release_reason = p_reason, closed_at = now() WHERE id = r.id;
    END LOOP;
    UPDATE order_items SET has_stock_exception = false WHERE id = p_item AND has_stock_exception;
END $$;

-- Khóa theo thứ tự chung orders → invoices → payment_requests đang mở; trả về hóa đơn đã khóa
CREATE FUNCTION rc_lock_invoice(p_invoice BIGINT) RETURNS invoices
LANGUAGE plpgsql AS $$
DECLARE
    v_order   BIGINT;
    v_invoice invoices%ROWTYPE;
BEGIN
    SELECT order_id INTO v_order FROM invoices WHERE id = p_invoice;   -- order_id bất biến nên đọc không khóa là an toàn
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('invoice %s không tồn tại', p_invoice));
    END IF;
    PERFORM 1 FROM orders WHERE id = v_order FOR UPDATE;
    SELECT * INTO v_invoice FROM invoices WHERE id = p_invoice FOR UPDATE;
    PERFORM 1 FROM payment_requests WHERE invoice_id = p_invoice AND status IN ('REQUESTED', 'WAITING_TRANSFER')
     ORDER BY id FOR UPDATE;
    RETURN v_invoice;
END $$;

-- Đối chiếu hóa đơn với đơn hàng (QĐ-4): dòng hóa đơn = đúng tập dòng SERVED của đơn, cùng tên/số lượng/đơn giá.
-- Trả về tổng tiền tính lại từ invoice_items; lệch → RC409.
CREATE FUNCTION rc_invoice_total_checked(p_invoice BIGINT) RETURNS NUMERIC
LANGUAGE plpgsql AS $$
DECLARE
    v_order   BIGINT;
    v_missing INT;
    v_invalid INT;
    v_total   NUMERIC;
BEGIN
    SELECT order_id INTO v_order FROM invoices WHERE id = p_invoice;
    SELECT count(*) INTO v_missing
      FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
     WHERE b.order_id = v_order AND oi.status = 'SERVED'
       AND NOT EXISTS (SELECT 1 FROM invoice_items ii WHERE ii.order_item_id = oi.id AND ii.invoice_id = p_invoice);
    SELECT count(*) INTO v_invalid
      FROM invoice_items ii
      LEFT JOIN order_items oi ON oi.id = ii.order_item_id
      LEFT JOIN order_batches b ON b.id = oi.order_batch_id
     WHERE ii.invoice_id = p_invoice
       AND (oi.id IS NULL OR b.order_id <> v_order OR oi.status <> 'SERVED'
            OR ii.item_name <> oi.item_name_snapshot OR ii.quantity <> oi.quantity OR ii.unit_price <> oi.unit_price);
    IF v_missing > 0 OR v_invalid > 0 THEN
        PERFORM rc_raise('RC409', format('hóa đơn %s không khớp đơn hàng: thiếu %s dòng SERVED, %s dòng hóa đơn không hợp lệ', p_invoice, v_missing, v_invalid));
    END IF;
    SELECT COALESCE(SUM(line_total), 0) INTO v_total FROM invoice_items WHERE invoice_id = p_invoice;
    RETURN v_total;
END $$;

-- Thanh toán thành công → đóng hóa đơn, đơn, phiên, trả bàn (G10). Người gọi đã giữ khóa orders → invoices.
CREATE FUNCTION rc_settle_invoice(p_invoice BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_order   BIGINT;
    v_session BIGINT;
    v_table   BIGINT;
BEGIN
    UPDATE invoices SET status = 'PAID' WHERE id = p_invoice RETURNING order_id INTO v_order;
    UPDATE orders SET status = 'CLOSED' WHERE id = v_order RETURNING table_session_id INTO v_session;
    UPDATE table_sessions SET status = 'CLOSED', closed_at = now(), closed_by_id = p_actor
     WHERE id = v_session RETURNING dining_table_id INTO v_table;
    UPDATE dining_tables SET status = 'AVAILABLE' WHERE id = v_table;
END $$;

-- =====================================================================
-- 10. HÀM NGHIỆP VỤ rc_* (32 hàm)
-- =====================================================================

-- ---------- 10.1 Bàn và phiên phục vụ (3.1)
CREATE FUNCTION rc_open_session(p_table BIGINT, p_actor BIGINT) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_table   dining_tables%ROWTYPE;
    v_session BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'WAITER', 'MANAGER');
    SELECT * INTO v_table FROM dining_tables WHERE id = p_table FOR UPDATE;
    IF NOT FOUND OR NOT v_table.is_active THEN
        PERFORM rc_raise('RC422', format('bàn %s không tồn tại hoặc ngừng sử dụng', p_table));
    END IF;
    IF v_table.status <> 'AVAILABLE' THEN
        PERFORM rc_raise('RC409', format('bàn %s đang có khách', v_table.code));
    END IF;
    INSERT INTO table_sessions (dining_table_id, opened_by_id) VALUES (p_table, p_actor) RETURNING id INTO v_session;
    INSERT INTO orders (table_session_id) VALUES (v_session);
    UPDATE dining_tables SET status = 'OCCUPIED' WHERE id = p_table;
    RETURN v_session;
END $$;

-- Đóng phiên chưa có món nào có nghĩa vụ (mọi dòng CANCELLED/REJECTED hoặc chưa gọi) và chưa có hóa đơn
CREATE FUNCTION rc_close_empty_session(p_session BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_order   orders%ROWTYPE;
    v_session table_sessions%ROWTYPE;
BEGIN
    PERFORM rc_authorize(p_actor, 'WAITER', 'MANAGER');
    SELECT * INTO v_order FROM orders WHERE table_session_id = p_session FOR UPDATE;            -- mức 1
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('phiên %s không tồn tại', p_session));
    END IF;
    SELECT * INTO v_session FROM table_sessions WHERE id = p_session FOR UPDATE;                -- mức 4
    IF v_session.status <> 'OPEN' OR v_order.status <> 'OPEN' THEN
        PERFORM rc_raise('RC409', format('phiên %s không mở', p_session));
    END IF;
    IF EXISTS (SELECT 1 FROM invoices WHERE order_id = v_order.id) THEN
        PERFORM rc_raise('RC409', format('phiên %s đã có hóa đơn: chỉ đóng bằng thanh toán, rc_waive_invoice hoặc rc_abandon_session', p_session));
    END IF;
    IF EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                WHERE b.order_id = v_order.id AND oi.status NOT IN ('CANCELLED', 'REJECTED')) THEN
        PERFORM rc_raise('RC409', format('phiên %s còn món đang xử lý hoặc đã phục vụ', p_session));
    END IF;
    PERFORM 1 FROM dining_tables WHERE id = v_session.dining_table_id FOR UPDATE;
    UPDATE orders SET status = 'CANCELLED' WHERE id = v_order.id;
    UPDATE table_sessions SET status = 'CLOSED', closed_at = now(), closed_by_id = p_actor WHERE id = p_session;
    UPDATE dining_tables SET status = 'AVAILABLE' WHERE id = v_session.dining_table_id;
END $$;

-- ---------- 10.2 Gửi món (3.2)
-- p_items: [{"daily_menu_item_id": 1, "quantity": 2, "note": "ít cay"}, ...]
-- Trả về {"batch_id": n, "replayed": bool, "warnings": [{"code": "QUOTA_EXCEEDED", "order_item_id", "daily_menu_item_id",
--          "item_name", "quantity", "quota_total", "quota_used", "remaining"}]}.
-- Idempotent theo request_key (gửi lặp trả đợt cũ, cảnh báo tính lại theo số suất hiện tại).
-- QĐ-4: hết quota KHÔNG chặn gửi món — chỉ cảnh báo; kiểm cứng (không nhận vượt quota) nằm ở rc_accept_item.
CREATE FUNCTION rc_submit_batch(p_order BIGINT, p_actor BIGINT, p_request_key TEXT, p_items JSONB) RETURNS JSONB
LANGUAGE plpgsql AS $$
DECLARE
    v_batch    BIGINT;
    v_existing BIGINT;
    v_status   TEXT;
    v_inv      TEXT;
    v_item     BIGINT;
    r          RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'WAITER', 'MANAGER');
    IF p_request_key IS NULL OR btrim(p_request_key) = '' THEN
        PERFORM rc_raise('RC422', 'thiếu request_key');
    END IF;
    SELECT id, order_id INTO v_batch, v_existing FROM order_batches WHERE request_key = p_request_key;
    IF FOUND THEN
        IF v_existing <> p_order THEN
            PERFORM rc_raise('RC409', format('request_key %s đã dùng cho đơn khác', p_request_key));
        END IF;
        RETURN rc_batch_result(v_batch, true);                                -- gửi lặp: trả đợt cũ (ORDER-01)
    END IF;
    IF jsonb_typeof(p_items) IS DISTINCT FROM 'array' OR jsonb_array_length(p_items) = 0 THEN
        PERFORM rc_raise('RC422', 'đợt món rỗng');
    END IF;
    SELECT status INTO v_status FROM orders WHERE id = p_order FOR UPDATE;    -- mức 1: tuần tự với chốt hóa đơn
    IF v_status IS DISTINCT FROM 'OPEN' THEN
        PERFORM rc_raise('RC409', format('order %s không mở', p_order));
    END IF;
    SELECT id, order_id INTO v_batch, v_existing FROM order_batches WHERE request_key = p_request_key;
    IF FOUND THEN                                                              -- cùng request_key vừa commit từ giao dịch song song
        IF v_existing <> p_order THEN
            PERFORM rc_raise('RC409', format('request_key %s đã dùng cho đơn khác', p_request_key));
        END IF;
        RETURN rc_batch_result(v_batch, true);
    END IF;
    SELECT status INTO v_inv FROM invoices WHERE order_id = p_order;
    IF v_inv IN ('PAYMENT_PENDING', 'PAID', 'UNCOLLECTED') THEN
        PERFORM rc_raise('RC409', format('order %s đang thanh toán: mở lại hóa đơn trước khi gọi thêm', p_order));
    END IF;
    INSERT INTO order_batches (order_id, waiter_id, request_key) VALUES (p_order, p_actor, p_request_key)
    RETURNING id INTO v_batch;
    FOR r IN
        SELECT x.daily_menu_item_id, x.quantity, x.note,
               d.id AS dmi_id, d.is_paused, d.quota_total,
               dm.status AS menu_status, dm.business_date, mi.name, mi.price, mi.is_active
          FROM jsonb_to_recordset(p_items) AS x (daily_menu_item_id BIGINT, quantity INT, note TEXT)
          LEFT JOIN daily_menu_items d ON d.id = x.daily_menu_item_id
          LEFT JOIN daily_menus dm ON dm.id = d.daily_menu_id
          LEFT JOIN menu_items mi ON mi.id = d.menu_item_id
    LOOP
        IF r.quantity IS NULL OR r.quantity <= 0 THEN
            PERFORM rc_raise('RC422', 'số lượng món phải lớn hơn 0');
        END IF;
        IF r.dmi_id IS NULL OR r.menu_status IS DISTINCT FROM 'PUBLISHED' OR r.business_date <> rc_business_date(now()) THEN
            PERFORM rc_raise('RC409', format('món %s không có trong thực đơn đang bán hôm nay', r.daily_menu_item_id));
        END IF;
        IF NOT r.is_active THEN
            PERFORM rc_raise('RC409', format('món %s đã ngừng kinh doanh', r.name));
        END IF;
        IF r.is_paused THEN
            PERFORM rc_raise('RC409', format('món %s đang tạm dừng', r.name));
        END IF;
        INSERT INTO order_items (order_batch_id, daily_menu_item_id, quantity, item_name_snapshot, unit_price, note)
        VALUES (v_batch, r.daily_menu_item_id, r.quantity, r.name, r.price, r.note)
        RETURNING id INTO v_item;
        INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status)
        VALUES (v_item, p_actor, NULL, 'PENDING');
    END LOOP;
    RETURN rc_batch_result(v_batch, false);
END $$;

-- Kết quả gửi món: cảnh báo QUOTA_EXCEEDED cho dòng có số lượng vượt phần suất còn lại (quota NULL = không giới hạn,
-- không cảnh báo). Chỉ đọc; số suất đã chiếm suy ra như rc_quota_used (QĐ-8).
CREATE FUNCTION rc_batch_result(p_batch BIGINT, p_replayed BOOLEAN) RETURNS JSONB
LANGUAGE sql AS $$
    SELECT jsonb_build_object(
        'batch_id', p_batch,
        'replayed', p_replayed,
        'warnings', COALESCE(jsonb_agg(jsonb_build_object(
                        'code', 'QUOTA_EXCEEDED', 'order_item_id', w.id, 'daily_menu_item_id', w.daily_menu_item_id,
                        'item_name', w.item_name_snapshot, 'quantity', w.quantity, 'quota_total', w.quota_total,
                        'quota_used', w.used, 'remaining', GREATEST(w.quota_total - w.used, 0)) ORDER BY w.id), '[]'::jsonb))
      FROM (SELECT oi.id, oi.daily_menu_item_id, oi.item_name_snapshot, oi.quantity, d.quota_total,
                   rc_quota_used(d.id) AS used
              FROM order_items oi JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
             WHERE oi.order_batch_id = p_batch AND oi.status = 'PENDING' AND d.quota_total IS NOT NULL) w
     WHERE w.quantity > GREATEST(w.quota_total - w.used, 0)
$$;

-- ---------- 10.3 Bếp xử lý món (3.3, 3.4)
-- Nhận món (QĐ-8): quota chiếm khi ACCEPTED. Khóa dòng thực đơn ngày (mức 6) tuần tự hóa mọi lần nhận cùng một
-- món → không vượt quota khi hai Kitchen nhận suất cuối. Quota và đặt giữ nguyên liệu cùng một transaction;
-- không đạt (quota/tồn) → REJECTED kèm lý do, mọi phần giữ dở dang được hoàn tác. NULL = không giới hạn suất
-- nhưng vẫn phải đủ nguyên liệu.
CREATE FUNCTION rc_accept_item(p_item BIGINT, p_actor BIGINT) RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    v_item   order_items%ROWTYPE;
    v_dmi    daily_menu_items%ROWTYPE;
    v_menu   daily_menus%ROWTYPE;
    v_used   BIGINT;
    v_reason TEXT;
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    SELECT * INTO v_item FROM order_items WHERE id = p_item FOR UPDATE;                  -- mức 5
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('order_item %s không tồn tại', p_item));
    END IF;
    IF v_item.status <> 'PENDING' THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s, không phải PENDING', p_item, v_item.status));
    END IF;
    SELECT * INTO v_dmi FROM daily_menu_items WHERE id = v_item.daily_menu_item_id FOR UPDATE;   -- mức 6
    SELECT * INTO v_menu FROM daily_menus WHERE id = v_dmi.daily_menu_id;
    IF v_menu.status <> 'PUBLISHED' OR v_menu.business_date <> rc_business_date(now()) THEN
        v_reason := 'Thực đơn ngày không còn mở bán';
    ELSIF v_dmi.is_paused THEN
        v_reason := 'Món đang tạm dừng: ' || v_dmi.pause_reason;
    ELSIF v_dmi.quota_total IS NOT NULL THEN
        v_used := rc_quota_used(v_dmi.id);
        IF v_used + v_item.quantity > v_dmi.quota_total THEN
            v_reason := CASE WHEN v_dmi.quota_total = 0
                             THEN 'Món không còn suất được bán (hạn mức 0)'
                             ELSE format('Vượt hạn mức: đã nhận %s/%s suất, cần thêm %s', v_used, v_dmi.quota_total, v_item.quantity) END;
        END IF;
    END IF;
    IF v_reason IS NULL THEN
        BEGIN                                            -- savepoint: thiếu nguyên liệu → hoàn tác mọi phần đã giữ
            PERFORM rc_allocate_item(p_item);
        EXCEPTION WHEN SQLSTATE 'RC001' THEN
            v_reason := SQLERRM;
        END;
    END IF;
    IF v_reason IS NOT NULL THEN
        PERFORM rc_item_transition(p_item, ARRAY['PENDING'], 'REJECTED', p_actor, v_reason);
        RETURN 'REJECTED';
    END IF;
    PERFORM rc_item_transition(p_item, ARRAY['PENDING'], 'ACCEPTED', p_actor);
    RETURN 'ACCEPTED';
END $$;

-- Kitchen từ chối chủ động (bắt buộc lý do). REJECTED không chiếm quota.
CREATE FUNCTION rc_reject_item(p_item BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'từ chối món bắt buộc có lý do');
    END IF;
    PERFORM rc_item_transition(p_item, ARRAY['PENDING'], 'REJECTED', p_actor, btrim(p_reason));
END $$;

-- Bắt đầu nấu (3.4): tiêu hao phần đã giữ, ghi sổ COOK. Từ đây món không còn hủy thường được.
CREATE FUNCTION rc_start_cooking(p_item BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_item order_items%ROWTYPE;
    r      RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    SELECT * INTO v_item FROM order_items WHERE id = p_item FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('order_item %s không tồn tại', p_item));
    END IF;
    IF v_item.status <> 'ACCEPTED' THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s, không phải ACCEPTED', p_item, v_item.status));
    END IF;
    IF v_item.has_stock_exception THEN
        PERFORM rc_raise('RC409', format('order_item %s có lô giữ bị hỏng: cấp lại bằng rc_reallocate_item hoặc hủy món', p_item));
    END IF;
    PERFORM 1 FROM kitchen_batches
     WHERE id IN (SELECT kitchen_batch_id FROM stock_reservations WHERE order_item_id = p_item AND status = 'ACTIVE')
     ORDER BY kitchen_ingredient_type_id, id FOR UPDATE;
    IF EXISTS (SELECT 1 FROM stock_reservations sr JOIN kitchen_batches kb ON kb.id = sr.kitchen_batch_id
                WHERE sr.order_item_id = p_item AND sr.status = 'ACTIVE'
                  AND (NOT kb.is_usable OR kb.usable_until <= now())) THEN
        PERFORM rc_raise('RC409', format('order_item %s đang giữ lô hết hạn/không dùng được: cấp lại trước khi nấu', p_item));
    END IF;
    FOR r IN SELECT sr.id, sr.kitchen_batch_id, sr.quantity
               FROM stock_reservations sr
              WHERE sr.order_item_id = p_item AND sr.status = 'ACTIVE'
              ORDER BY sr.kitchen_batch_id
    LOOP
        UPDATE kitchen_batches
           SET quantity_on_hand = quantity_on_hand - r.quantity, reserved_quantity = reserved_quantity - r.quantity
         WHERE id = r.kitchen_batch_id;
        UPDATE stock_reservations SET status = 'CONSUMED', closed_at = now() WHERE id = r.id;
        INSERT INTO stock_movements (movement_type, kitchen_batch_id, order_item_id, quantity_delta, event_key)
        VALUES ('COOK', r.kitchen_batch_id, p_item, -r.quantity, 'COOK:' || r.id);
    END LOOP;
    PERFORM rc_item_transition(p_item, ARRAY['ACCEPTED'], 'COOKING', p_actor);
END $$;

CREATE FUNCTION rc_finish_dish(p_item BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    PERFORM rc_item_transition(p_item, ARRAY['COOKING'], 'READY', p_actor);
END $$;

-- Waiter mang món ra
CREATE FUNCTION rc_serve_item(p_item BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM rc_authorize(p_actor, 'WAITER', 'MANAGER');
    PERFORM rc_item_transition(p_item, ARRAY['READY'], 'SERVED', p_actor);
END $$;

-- Hủy THƯỜNG trước khi nấu (QĐ-2, QĐ-8): PENDING/ACCEPTED, bắt buộc lý do. Giải phóng phần giữ (không tăng tồn vật lý);
-- suất được giải phóng đúng một lần vì dòng rời tập "đã chiếm". Từ COOKING chỉ Manager dùng rc_cancel_item_exception.
CREATE FUNCTION rc_cancel_item(p_item BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
BEGIN
    PERFORM rc_authorize(p_actor, 'WAITER', 'KITCHEN', 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'hủy món bắt buộc có lý do');
    END IF;
    SELECT status INTO v_status FROM order_items WHERE id = p_item FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('order_item %s không tồn tại', p_item));
    END IF;
    IF v_status IN ('COOKING', 'READY', 'SERVED') THEN
        PERFORM rc_raise('RC409', format('order_item %s đã %s: chỉ Manager hủy ngoại lệ bằng rc_cancel_item_exception', p_item, v_status));
    END IF;
    IF v_status NOT IN ('PENDING', 'ACCEPTED') THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s, không hủy được', p_item, v_status));
    END IF;
    PERFORM rc_release_item_reservations(p_item, 'ITEM_CANCELLED');
    PERFORM rc_item_transition(p_item, ARRAY['PENDING', 'ACCEPTED'], 'CANCELLED', p_actor, btrim(p_reason));
END $$;

-- Hủy NGOẠI LỆ từ COOKING trở đi (QĐ-2): chỉ Manager, bắt buộc lý do. Không hoàn nguyên liệu đã tiêu hao
-- (reservation đã CONSUMED, không ghi sổ kho), không hoàn quota (rc_quota_used vẫn tính), không tính tiền:
-- hóa đơn chưa PAID thì dòng bị loại khỏi hóa đơn DRAFT. Hóa đơn PAYMENT_PENDING phải mở lại trước; PAID thì không hủy được.
CREATE FUNCTION rc_cancel_item_exception(p_item BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_order   BIGINT;
    v_status  TEXT;
    v_invoice invoices%ROWTYPE;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'hủy ngoại lệ bắt buộc có lý do');
    END IF;
    SELECT b.order_id INTO v_order
      FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE oi.id = p_item;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('order_item %s không tồn tại', p_item));
    END IF;
    SELECT status INTO v_status FROM orders WHERE id = v_order FOR UPDATE;                -- mức 1: tuần tự với chốt hóa đơn/thanh toán
    IF v_status <> 'OPEN' THEN
        PERFORM rc_raise('RC409', format('đơn %s đã %s: không hủy món ngoại lệ', v_order, v_status));
    END IF;
    SELECT * INTO v_invoice FROM invoices WHERE order_id = v_order FOR UPDATE;            -- mức 2
    IF FOUND THEN
        IF v_invoice.status = 'PAYMENT_PENDING' THEN
            PERFORM rc_raise('RC409', format('hóa đơn %s đang PAYMENT_PENDING: mở lại bằng rc_reopen_invoice trước khi hủy món', v_invoice.id));
        ELSIF v_invoice.status <> 'DRAFT' THEN
            PERFORM rc_raise('RC409', format('hóa đơn %s đã %s: không hủy món bằng cơ chế ngoại lệ', v_invoice.id, v_invoice.status));
        END IF;
    END IF;
    SELECT status INTO v_status FROM order_items WHERE id = p_item FOR UPDATE;            -- mức 5
    IF v_status NOT IN ('COOKING', 'READY', 'SERVED') THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s: hủy ngoại lệ chỉ áp dụng từ COOKING (trước đó dùng rc_cancel_item)', p_item, v_status));
    END IF;
    IF v_invoice.id IS NOT NULL THEN                                                       -- hóa đơn DRAFT: loại dòng, tính lại tổng
        DELETE FROM invoice_items WHERE invoice_id = v_invoice.id AND order_item_id = p_item;
    END IF;
    PERFORM rc_item_transition(p_item, ARRAY['COOKING', 'READY', 'SERVED'], 'CANCELLED', p_actor, btrim(p_reason));
    IF v_invoice.id IS NOT NULL THEN
        UPDATE invoices SET total = (SELECT COALESCE(SUM(line_total), 0) FROM invoice_items WHERE invoice_id = v_invoice.id)
         WHERE id = v_invoice.id;
    END IF;
END $$;

-- ---------- 10.4 Lô hỏng khi đang được giữ (G7.8) — DB-14
CREATE FUNCTION rc_mark_batch_unusable(p_kind TEXT, p_batch BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE
    v_flagged INT := 0;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'đánh dấu lô không dùng được bắt buộc có lý do');
    END IF;
    IF p_kind = 'RAW' THEN
        UPDATE inventory_batches
           SET is_usable = false, unusable_reason = btrim(p_reason), unusable_marked_by_id = p_actor, unusable_marked_at = now()
         WHERE id = p_batch AND is_usable;
    ELSIF p_kind = 'KITCHEN' THEN
        PERFORM 1 FROM order_items
         WHERE id IN (SELECT order_item_id FROM stock_reservations WHERE kitchen_batch_id = p_batch AND status = 'ACTIVE')
         ORDER BY id FOR UPDATE;
        UPDATE kitchen_batches
           SET is_usable = false, unusable_reason = btrim(p_reason), unusable_marked_by_id = p_actor, unusable_marked_at = now()
         WHERE id = p_batch AND is_usable;
        IF FOUND THEN
            UPDATE order_items SET has_stock_exception = true
             WHERE id IN (SELECT order_item_id FROM stock_reservations
                           WHERE kitchen_batch_id = p_batch AND status = 'ACTIVE');
            GET DIAGNOSTICS v_flagged = ROW_COUNT;
            RETURN v_flagged;
        END IF;
    ELSE
        PERFORM rc_raise('RC422', 'p_kind phải là RAW hoặc KITCHEN');
    END IF;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC409', format('lô %s %s không tồn tại hoặc đã đánh dấu', p_kind, p_batch));
    END IF;
    RETURN v_flagged;
END $$;

CREATE FUNCTION rc_reallocate_item(p_item BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    SELECT status INTO v_status FROM order_items WHERE id = p_item FOR UPDATE;
    IF v_status IS DISTINCT FROM 'ACCEPTED' THEN
        PERFORM rc_raise('RC409', format('order_item %s đang %s, không phải ACCEPTED', p_item, v_status));
    END IF;
    -- khóa lô (loại, id): các lô đang giữ + mọi lô có thể dùng của các loại trong công thức
    PERFORM 1
      FROM kitchen_batches kb
     WHERE kb.id IN (SELECT kitchen_batch_id FROM stock_reservations WHERE order_item_id = p_item AND status = 'ACTIVE')
        OR (kb.kitchen_ingredient_type_id IN (
                SELECT ri.kitchen_ingredient_type_id
                  FROM order_items oi
                  JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
                  JOIN recipe_ingredients ri ON ri.recipe_id = d.recipe_id
                 WHERE oi.id = p_item)
            AND kb.is_usable AND kb.quantity_on_hand > 0 AND (kb.usable_until IS NULL OR kb.usable_until > now()))
     ORDER BY kb.kitchen_ingredient_type_id, kb.id
       FOR UPDATE;
    FOR r IN SELECT sr.id, sr.kitchen_batch_id, sr.quantity
               FROM stock_reservations sr JOIN kitchen_batches kb ON kb.id = sr.kitchen_batch_id
              WHERE sr.order_item_id = p_item AND sr.status = 'ACTIVE'
                AND (NOT kb.is_usable OR kb.usable_until <= now())
    LOOP
        UPDATE kitchen_batches SET reserved_quantity = reserved_quantity - r.quantity WHERE id = r.kitchen_batch_id;
        UPDATE stock_reservations SET status = 'RELEASED', release_reason = 'BATCH_UNUSABLE', closed_at = now()
         WHERE id = r.id;
    END LOOP;
    PERFORM rc_allocate_item(p_item);    -- vẫn thiếu → RC001, cả lệnh rollback; Kitchen hủy món
    UPDATE order_items SET has_stock_exception = false WHERE id = p_item;
END $$;

-- ---------- 10.5 Hóa đơn (3.5) — QĐ-4
-- Cashier chốt hóa đơn: tạo đủ invoice_items từ các dòng SERVED, tự tính tổng (không nhận tổng từ client),
-- đối chiếu với đơn hàng, chuyển PAYMENT_PENDING. Khóa orders → invoices tuần tự với gọi món và thanh toán.
CREATE FUNCTION rc_checkout_invoice(p_order BIGINT, p_actor BIGINT) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_status  TEXT;
    v_invoice invoices%ROWTYPE;
    v_total   NUMERIC;
BEGIN
    PERFORM rc_authorize(p_actor, 'CASHIER', 'MANAGER');
    SELECT status INTO v_status FROM orders WHERE id = p_order FOR UPDATE;                -- mức 1
    IF v_status IS DISTINCT FROM 'OPEN' THEN
        PERFORM rc_raise('RC409', format('order %s không mở', p_order));
    END IF;
    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order FOR UPDATE;            -- mức 2
    IF FOUND AND v_invoice.status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('invoice %s đang %s', v_invoice.id, v_invoice.status));
    END IF;
    IF EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                WHERE b.order_id = p_order AND oi.status NOT IN ('SERVED', 'CANCELLED', 'REJECTED')) THEN
        PERFORM rc_raise('RC409', format('order %s còn món chưa phục vụ xong', p_order));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                    WHERE b.order_id = p_order AND oi.status = 'SERVED') THEN
        PERFORM rc_raise('RC409', format('order %s không có món phải thu: dùng rc_close_empty_session', p_order));
    END IF;
    IF v_invoice.id IS NULL THEN
        INSERT INTO invoices (order_id, created_by_id) VALUES (p_order, p_actor) RETURNING * INTO v_invoice;
    END IF;
    INSERT INTO invoice_items (invoice_id, order_item_id, item_name, quantity, unit_price)
    SELECT v_invoice.id, oi.id, oi.item_name_snapshot, oi.quantity, oi.unit_price
      FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
     WHERE b.order_id = p_order AND oi.status = 'SERVED'
       AND NOT EXISTS (SELECT 1 FROM invoice_items ii WHERE ii.order_item_id = oi.id);
    v_total := rc_invoice_total_checked(v_invoice.id);
    IF v_total <= 0 THEN                                  -- không có gì để thu: Manager đóng bằng rc_waive_invoice (QĐ-MT)
        PERFORM rc_raise('RC422', format('hóa đơn %s tổng 0 đồng: không chờ thu, Manager đóng bằng rc_waive_invoice', v_invoice.id));
    END IF;
    UPDATE invoices SET total = v_total, status = 'PAYMENT_PENDING' WHERE id = v_invoice.id;
    RETURN v_invoice.id;
END $$;

-- Mở lại hóa đơn để thay đổi trước thanh toán: hủy yêu cầu thanh toán đang mở (có lý do, người, giờ), hóa đơn → DRAFT
CREATE FUNCTION rc_reopen_invoice(p_invoice BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
BEGIN
    PERFORM rc_authorize(p_actor, 'CASHIER', 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'mở lại hóa đơn bắt buộc có lý do');
    END IF;
    v_invoice := rc_lock_invoice(p_invoice);
    IF v_invoice.status IS DISTINCT FROM 'PAYMENT_PENDING' THEN
        PERFORM rc_raise('RC409', format('invoice %s đang %s, không phải PAYMENT_PENDING', p_invoice, v_invoice.status));
    END IF;
    UPDATE payment_requests
       SET status = 'CANCELLED', cancel_reason = btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor
     WHERE invoice_id = p_invoice AND status IN ('REQUESTED', 'WAITING_TRANSFER');
    UPDATE invoices SET status = 'DRAFT' WHERE id = p_invoice;
END $$;

-- ---------- 10.6 Thanh toán (3.6, 3.7) — khóa orders → invoices → payment_requests
CREATE FUNCTION rc_pay_cash(p_invoice BIGINT, p_actor BIGINT, p_cash_received NUMERIC) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
    v_request BIGINT;
    v_payment BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'CASHIER', 'MANAGER');
    v_invoice := rc_lock_invoice(p_invoice);
    IF v_invoice.status IS DISTINCT FROM 'PAYMENT_PENDING' THEN
        PERFORM rc_raise('RC409', format('invoice %s đang %s, không chờ thanh toán', p_invoice, v_invoice.status));
    END IF;
    IF EXISTS (SELECT 1 FROM payment_requests WHERE invoice_id = p_invoice AND status IN ('REQUESTED', 'WAITING_TRANSFER')) THEN
        PERFORM rc_raise('RC409', format('invoice %s đang có yêu cầu QR mở: hủy trước khi thu tiền mặt', p_invoice));
    END IF;
    IF p_cash_received IS NULL OR p_cash_received < v_invoice.total THEN
        PERFORM rc_raise('RC422', format('tiền khách đưa %s nhỏ hơn số phải thu %s', p_cash_received, v_invoice.total));
    END IF;
    INSERT INTO payment_requests (invoice_id, cashier_id, amount_due, cash_received, method, status)
    VALUES (p_invoice, p_actor, v_invoice.total, p_cash_received, 'CASH', 'PAID')
    RETURNING id INTO v_request;
    INSERT INTO payments (invoice_id, payment_request_id, amount, method, business_date)
    VALUES (p_invoice, v_request, v_invoice.total, 'CASH', rc_business_date(now()))
    RETURNING id INTO v_payment;
    PERFORM rc_settle_invoice(p_invoice, p_actor);
    RETURN v_payment;
END $$;

CREATE FUNCTION rc_start_qr(p_invoice BIGINT, p_actor BIGINT) RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice invoices%ROWTYPE;
    v_ref     TEXT;
BEGIN
    PERFORM rc_authorize(p_actor, 'CASHIER', 'MANAGER');
    v_invoice := rc_lock_invoice(p_invoice);
    IF v_invoice.status IS DISTINCT FROM 'PAYMENT_PENDING' THEN
        PERFORM rc_raise('RC409', format('invoice %s đang %s, không chờ thanh toán', p_invoice, v_invoice.status));
    END IF;
    IF EXISTS (SELECT 1 FROM payment_requests WHERE invoice_id = p_invoice AND status IN ('REQUESTED', 'WAITING_TRANSFER')) THEN
        PERFORM rc_raise('RC409', format('invoice %s đã có yêu cầu thanh toán đang mở', p_invoice));
    END IF;
    -- mã tham chiếu đưa vào nội dung chuyển khoản: chỉ chữ + số
    v_ref := 'RC' || p_invoice || 'Q' || (1 + (SELECT count(*) FROM payment_requests WHERE invoice_id = p_invoice));
    INSERT INTO payment_requests (invoice_id, cashier_id, amount_due, method, status, payment_reference)
    VALUES (p_invoice, p_actor, v_invoice.total, 'QR', 'WAITING_TRANSFER', v_ref);
    RETURN v_ref;
END $$;

-- Chỉ gọi SAU KHI backend đã xác thực chữ ký/API key/IP của SePay.
-- Idempotent: cùng provider_transaction_code luôn trả kết quả đã lưu, kể cả khi hai webhook đến cùng lúc.
CREATE FUNCTION rc_process_verified_qr(p_transaction_code TEXT, p_reference TEXT, p_amount NUMERIC, p_payload TEXT)
RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
    v_result     TEXT;
    v_request_id BIGINT;
    v_invoice_id BIGINT;
    v_request    payment_requests%ROWTYPE;
    v_invoice    invoices%ROWTYPE;
    v_event      BIGINT;
BEGIN
    IF p_transaction_code IS NULL OR btrim(p_transaction_code) = '' OR p_reference IS NULL
       OR p_amount IS NULL OR p_payload IS NULL THEN
        PERFORM rc_raise('RC422', 'webhook thiếu mã giao dịch, mã tham chiếu, số tiền hoặc payload');
    END IF;
    SELECT processing_result INTO v_result FROM payment_webhook_events WHERE provider_transaction_code = p_transaction_code;
    IF FOUND THEN
        RETURN v_result;                                 -- webhook lặp: không ghi lần hai
    END IF;
    SELECT id, invoice_id INTO v_request_id, v_invoice_id FROM payment_requests WHERE payment_reference = p_reference;
    IF v_request_id IS NOT NULL THEN
        v_invoice := rc_lock_invoice(v_invoice_id);                                   -- mức 1 → 2 → 3
        SELECT * INTO v_request FROM payment_requests WHERE id = v_request_id FOR UPDATE;
        SELECT processing_result INTO v_result FROM payment_webhook_events WHERE provider_transaction_code = p_transaction_code;
        IF FOUND THEN
            RETURN v_result;                             -- cùng webhook vừa commit từ giao dịch song song trong lúc chờ khóa
        END IF;
    END IF;
    v_result := CASE
        WHEN v_request_id IS NULL THEN 'MISMATCH'
        WHEN v_request.status <> 'WAITING_TRANSFER' OR v_invoice.status <> 'PAYMENT_PENDING' THEN 'AFTER_CANCEL'
        WHEN p_amount <> v_request.amount_due THEN 'MISMATCH'
        ELSE 'PROCESSED'
    END;
    INSERT INTO payment_webhook_events
        (payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, raw_payload)
    VALUES (v_request_id, p_transaction_code, p_reference, p_amount, v_result, p_payload)
    ON CONFLICT (provider_transaction_code) DO NOTHING
    RETURNING id INTO v_event;
    IF v_event IS NULL THEN                              -- thua cuộc đua ghi cùng mã (tham chiếu lạ không có khóa): trả kết quả đã lưu
        SELECT processing_result INTO v_result FROM payment_webhook_events WHERE provider_transaction_code = p_transaction_code;
        RETURN v_result;
    END IF;
    IF v_result = 'PROCESSED' THEN
        INSERT INTO payments (invoice_id, payment_request_id, payment_webhook_event_id, amount, method, business_date)
        VALUES (v_invoice.id, v_request.id, v_event, p_amount, 'QR', rc_business_date(now()));
        UPDATE payment_requests SET status = 'PAID' WHERE id = v_request.id;
        PERFORM rc_settle_invoice(v_invoice.id, v_request.cashier_id);
    END IF;
    RETURN v_result;
END $$;

CREATE FUNCTION rc_cancel_payment_request(p_request BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_invoice BIGINT;
    v_status  TEXT;
BEGIN
    PERFORM rc_authorize(p_actor, 'CASHIER', 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'hủy yêu cầu thanh toán bắt buộc có lý do');
    END IF;
    SELECT invoice_id INTO v_invoice FROM payment_requests WHERE id = p_request;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('payment_request %s không tồn tại', p_request));
    END IF;
    PERFORM rc_lock_invoice(v_invoice);                                                 -- mức 1 → 2 → 3
    SELECT status INTO v_status FROM payment_requests WHERE id = p_request FOR UPDATE;
    IF v_status NOT IN ('REQUESTED', 'WAITING_TRANSFER') THEN
        PERFORM rc_raise('RC409', format('payment_request %s đang %s, không còn mở', p_request, v_status));
    END IF;
    UPDATE payment_requests
       SET status = 'CANCELLED', cancel_reason = btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor
     WHERE id = p_request;
END $$;

-- ---------- 10.7 Khách bỏ về chưa thanh toán (QĐ-3) — chỉ Manager, bắt buộc lý do
-- Một transaction: hủy/loại các dòng còn mở (trước COOKING giải phóng phần giữ; từ COOKING không hoàn nguyên liệu/quota),
-- ghi nhận nghĩa vụ vào hóa đơn (tạo nếu chưa có; hủy yêu cầu thanh toán đang mở), hóa đơn → UNCOLLECTED,
-- đơn → CLOSED, phiên → CLOSED, bàn → AVAILABLE. KHÔNG tạo payments, KHÔNG PAID, KHÔNG ghi doanh thu thực thu,
-- KHÔNG xóa dữ liệu. Tổng phải thu bằng 0 (không còn món SERVED có giá, đã có hóa đơn) → hóa đơn WAIVED 0 đồng thay vì
-- UNCOLLECTED (QĐ-MT). Không món SERVED và chưa có hóa đơn → đơn CANCELLED, trả NULL.
-- Trả về id hóa đơn UNCOLLECTED/WAIVED.
CREATE FUNCTION rc_abandon_session(p_session BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_order   orders%ROWTYPE;
    v_session table_sessions%ROWTYPE;
    v_invoice invoices%ROWTYPE;
    v_reason  TEXT;
    v_served  INT;
    v_total   NUMERIC;
    r         RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'xử lý khách bỏ về bắt buộc có lý do');
    END IF;
    v_reason := 'Khách bỏ về: ' || btrim(p_reason);
    SELECT * INTO v_order FROM orders WHERE table_session_id = p_session FOR UPDATE;      -- mức 1
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('phiên %s không tồn tại', p_session));
    END IF;
    IF v_order.status <> 'OPEN' THEN
        PERFORM rc_raise('RC409', format('đơn của phiên %s đã %s', p_session, v_order.status));
    END IF;
    SELECT * INTO v_invoice FROM invoices WHERE order_id = v_order.id FOR UPDATE;         -- mức 2
    IF FOUND THEN
        PERFORM 1 FROM payment_requests WHERE invoice_id = v_invoice.id AND status IN ('REQUESTED', 'WAITING_TRANSFER')
         ORDER BY id FOR UPDATE;                                                           -- mức 3
    END IF;
    SELECT * INTO v_session FROM table_sessions WHERE id = p_session FOR UPDATE;          -- mức 4
    IF v_session.status <> 'OPEN' THEN
        PERFORM rc_raise('RC409', format('phiên %s không mở', p_session));
    END IF;
    PERFORM 1 FROM dining_tables WHERE id = v_session.dining_table_id FOR UPDATE;
    FOR r IN SELECT oi.id, oi.status
               FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
              WHERE b.order_id = v_order.id ORDER BY oi.id FOR UPDATE OF oi                -- mức 5
    LOOP
        IF r.status IN ('PENDING', 'ACCEPTED') THEN
            PERFORM rc_release_item_reservations(r.id, 'ITEM_CANCELLED');
            PERFORM rc_item_transition(r.id, ARRAY['PENDING', 'ACCEPTED'], 'CANCELLED', p_actor, v_reason);
        ELSIF r.status IN ('COOKING', 'READY') THEN
            PERFORM rc_item_transition(r.id, ARRAY['COOKING', 'READY'], 'CANCELLED', p_actor, v_reason);
        END IF;
    END LOOP;
    SELECT count(*) INTO v_served
      FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
     WHERE b.order_id = v_order.id AND oi.status = 'SERVED';
    IF v_served = 0 AND v_invoice.id IS NULL THEN
        UPDATE orders SET status = 'CANCELLED' WHERE id = v_order.id;          -- không món phải thu, không chứng từ thu
    ELSE
        IF v_invoice.id IS NULL THEN
            INSERT INTO invoices (order_id, created_by_id) VALUES (v_order.id, p_actor) RETURNING * INTO v_invoice;
        END IF;
        IF v_invoice.status = 'DRAFT' THEN
            INSERT INTO invoice_items (invoice_id, order_item_id, item_name, quantity, unit_price)
            SELECT v_invoice.id, oi.id, oi.item_name_snapshot, oi.quantity, oi.unit_price
              FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
             WHERE b.order_id = v_order.id AND oi.status = 'SERVED'
               AND NOT EXISTS (SELECT 1 FROM invoice_items ii WHERE ii.order_item_id = oi.id);
        END IF;
        v_total := rc_invoice_total_checked(v_invoice.id);
        UPDATE payment_requests
           SET status = 'CANCELLED', cancel_reason = v_reason, cancelled_at = now(), cancelled_by_id = p_actor
         WHERE invoice_id = v_invoice.id AND status IN ('REQUESTED', 'WAITING_TRANSFER');
        IF v_total > 0 THEN                                                     -- còn nghĩa vụ thu: UNCOLLECTED
            UPDATE invoices
               SET total = v_total, status = 'UNCOLLECTED',
                   uncollected_reason = btrim(p_reason), uncollected_by_id = p_actor, uncollected_at = now()
             WHERE id = v_invoice.id;
        ELSE                                                                    -- hóa đơn 0 đồng: đóng dứt điểm bằng WAIVED
            UPDATE invoices
               SET total = 0, status = 'WAIVED',
                   waived_reason = v_reason || ' (hóa đơn 0 đồng)', waived_by_id = p_actor, waived_at = now()
             WHERE id = v_invoice.id;
        END IF;
        UPDATE orders SET status = 'CLOSED' WHERE id = v_order.id;
    END IF;
    UPDATE table_sessions SET status = 'CLOSED', closed_at = now(), closed_by_id = p_actor WHERE id = p_session;
    UPDATE dining_tables SET status = 'AVAILABLE' WHERE id = v_session.dining_table_id;
    RETURN v_invoice.id;
END $$;

-- ---------- 10.7b Miễn thu (QĐ-MT) — chỉ Manager, bắt buộc lý do
-- Đóng hóa đơn không thu tiền: tạo hóa đơn nếu chưa có, bổ sung đủ dòng SERVED (giữ nguyên giá bán, không sửa snapshot),
-- đối chiếu với đơn, hủy yêu cầu thanh toán đang mở, hóa đơn → WAIVED (total = giá trị được miễn, có thể 0),
-- đơn → CLOSED, phiên → CLOSED, bàn → AVAILABLE. Không payments, không doanh thu. Mọi món phải đã kết thúc
-- (SERVED/CANCELLED/REJECTED). PAID/UNCOLLECTED/WAIVED không miễn thu được. Khóa orders → invoices → payment_requests
-- như thanh toán nên miễn thu và thu tiền đồng thời không thể cùng thành công. Trả về id hóa đơn.
CREATE FUNCTION rc_waive_invoice(p_order BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_order   orders%ROWTYPE;
    v_session table_sessions%ROWTYPE;
    v_invoice invoices%ROWTYPE;
    v_total   NUMERIC;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'miễn thu bắt buộc có lý do');
    END IF;
    SELECT * INTO v_order FROM orders WHERE id = p_order FOR UPDATE;                     -- mức 1
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('order %s không tồn tại', p_order));
    END IF;
    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order FOR UPDATE;           -- mức 2
    IF FOUND AND v_invoice.status NOT IN ('DRAFT', 'PAYMENT_PENDING') THEN
        PERFORM rc_raise('RC409', format('invoice %s đã %s: không miễn thu được', v_invoice.id, v_invoice.status));
    END IF;
    IF v_order.status <> 'OPEN' THEN
        PERFORM rc_raise('RC409', format('order %s đã %s', p_order, v_order.status));
    END IF;
    IF v_invoice.id IS NOT NULL THEN
        PERFORM 1 FROM payment_requests WHERE invoice_id = v_invoice.id AND status IN ('REQUESTED', 'WAITING_TRANSFER')
         ORDER BY id FOR UPDATE;                                                         -- mức 3
    END IF;
    SELECT * INTO v_session FROM table_sessions WHERE id = v_order.table_session_id FOR UPDATE;   -- mức 4
    PERFORM 1 FROM dining_tables WHERE id = v_session.dining_table_id FOR UPDATE;
    IF EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                WHERE b.order_id = p_order AND oi.status NOT IN ('SERVED', 'CANCELLED', 'REJECTED')) THEN
        PERFORM rc_raise('RC409', format('order %s còn món chưa kết thúc: phục vụ hoặc hủy trước khi miễn thu', p_order));
    END IF;
    IF v_invoice.id IS NULL THEN
        IF NOT EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                        WHERE b.order_id = p_order AND oi.status = 'SERVED') THEN
            PERFORM rc_raise('RC409', format('order %s không có món phải thu và chưa có hóa đơn: dùng rc_close_empty_session', p_order));
        END IF;
        INSERT INTO invoices (order_id, created_by_id) VALUES (p_order, p_actor) RETURNING * INTO v_invoice;
    END IF;
    IF v_invoice.status = 'DRAFT' THEN
        INSERT INTO invoice_items (invoice_id, order_item_id, item_name, quantity, unit_price)
        SELECT v_invoice.id, oi.id, oi.item_name_snapshot, oi.quantity, oi.unit_price
          FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
         WHERE b.order_id = p_order AND oi.status = 'SERVED'
           AND NOT EXISTS (SELECT 1 FROM invoice_items ii WHERE ii.order_item_id = oi.id);
    END IF;
    v_total := rc_invoice_total_checked(v_invoice.id);
    IF v_invoice.status = 'PAYMENT_PENDING' AND v_total <> v_invoice.total THEN
        PERFORM rc_raise('RC500', format('invoice %s: tổng đã chốt %s khác tổng dòng %s', v_invoice.id, v_invoice.total, v_total));
    END IF;
    UPDATE payment_requests
       SET status = 'CANCELLED', cancel_reason = 'Miễn thu: ' || btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor
     WHERE invoice_id = v_invoice.id AND status IN ('REQUESTED', 'WAITING_TRANSFER');
    UPDATE invoices
       SET total = v_total, status = 'WAIVED', waived_reason = btrim(p_reason), waived_by_id = p_actor, waived_at = now()
     WHERE id = v_invoice.id;
    UPDATE orders SET status = 'CLOSED' WHERE id = p_order;
    UPDATE table_sessions SET status = 'CLOSED', closed_at = now(), closed_by_id = p_actor WHERE id = v_session.id;
    UPDATE dining_tables SET status = 'AVAILABLE' WHERE id = v_session.dining_table_id;
    RETURN v_invoice.id;
END $$;

-- ---------- 10.8a Tạo chứng từ kho idempotent (QĐ-ID) — header + dòng trong một transaction
-- Cùng request_key + cùng payload → trả id chứng từ đã tạo (kể cả khi đã xác nhận); cùng khóa khác payload → RC409.
-- Hai yêu cầu nghiệp vụ khác nhau dùng hai khóa khác nhau → hai chứng từ. Bước xác nhận vẫn idempotent riêng
-- (chỉ chạy từ DRAFT; event_key UNIQUE của sổ kho chặn ghi trùng).

-- p_payload: {"supplier_name", "note", "items": [{"ingredient_id", "input_quantity", "input_unit", "conversion_factor",
--             "unit_price_per_input_unit", "lot_code", "expiry_date"}]}
CREATE FUNCTION rc_create_stock_receipt(p_actor BIGINT, p_request_key TEXT, p_payload JSONB) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_hash CHAR(64);
    v_id   BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF jsonb_typeof(p_payload) IS DISTINCT FROM 'object' OR jsonb_typeof(p_payload -> 'items') IS DISTINCT FROM 'array'
       OR jsonb_array_length(p_payload -> 'items') = 0 THEN
        PERFORM rc_raise('RC422', 'phiếu nhập cần payload {"items": [...]} không rỗng');
    END IF;
    v_hash := rc_request_hash(p_payload);
    v_id := rc_request_lookup('stock_receipts', p_request_key, v_hash);
    IF v_id IS NOT NULL THEN
        RETURN v_id;                                                           -- gửi lặp: trả phiếu đã tạo
    END IF;
    INSERT INTO stock_receipts (supplier_name, note, created_by_id, request_key, request_hash)
    VALUES (p_payload ->> 'supplier_name', p_payload ->> 'note', p_actor, p_request_key, v_hash)
    RETURNING id INTO v_id;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor,
                                     unit_price_per_input_unit, lot_code, expiry_date)
    SELECT v_id, x.ingredient_id, x.input_quantity, x.input_unit, x.conversion_factor,
           x.unit_price_per_input_unit, x.lot_code, x.expiry_date
      FROM jsonb_to_recordset(p_payload -> 'items')
           AS x (ingredient_id BIGINT, input_quantity NUMERIC, input_unit TEXT, conversion_factor NUMERIC,
                 unit_price_per_input_unit NUMERIC, lot_code TEXT, expiry_date DATE);
    RETURN v_id;
END $$;

-- p_payload: {"note", "items": [{"inventory_batch_id", "quantity", "fulfillment_mode": "DIRECT"|"PREPARE"}]}
CREATE FUNCTION rc_create_stock_issue(p_actor BIGINT, p_request_key TEXT, p_payload JSONB) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_hash CHAR(64);
    v_id   BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF jsonb_typeof(p_payload) IS DISTINCT FROM 'object' OR jsonb_typeof(p_payload -> 'items') IS DISTINCT FROM 'array'
       OR jsonb_array_length(p_payload -> 'items') = 0 THEN
        PERFORM rc_raise('RC422', 'phiếu xuất cần payload {"items": [...]} không rỗng');
    END IF;
    v_hash := rc_request_hash(p_payload);
    v_id := rc_request_lookup('stock_issues', p_request_key, v_hash);
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    INSERT INTO stock_issues (issued_by_id, note, request_key, request_hash)
    VALUES (p_actor, p_payload ->> 'note', p_request_key, v_hash)
    RETURNING id INTO v_id;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    SELECT v_id, x.inventory_batch_id, x.quantity, x.fulfillment_mode
      FROM jsonb_to_recordset(p_payload -> 'items') AS x (inventory_batch_id BIGINT, quantity NUMERIC, fulfillment_mode TEXT);
    RETURN v_id;
END $$;

-- p_payload: {"note", "items": [{"raw_batch_id" | "wip_issue_item_id" | "kitchen_batch_id", "actual_quantity", "reason"}]}
-- book_quantity chụp từ số sổ lúc tạo phiếu; rc_confirm_count chặn nếu sổ đã đổi (stale).
CREATE FUNCTION rc_create_stock_count(p_actor BIGINT, p_request_key TEXT, p_payload JSONB) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_hash CHAR(64);
    v_id   BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF jsonb_typeof(p_payload) IS DISTINCT FROM 'object' OR jsonb_typeof(p_payload -> 'items') IS DISTINCT FROM 'array'
       OR jsonb_array_length(p_payload -> 'items') = 0 THEN
        PERFORM rc_raise('RC422', 'phiếu kiểm kê cần payload {"items": [...]} không rỗng');
    END IF;
    v_hash := rc_request_hash(p_payload);
    v_id := rc_request_lookup('stock_counts', p_request_key, v_hash);
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    INSERT INTO stock_counts (note, created_by_id, request_key, request_hash)
    VALUES (p_payload ->> 'note', p_actor, p_request_key, v_hash)
    RETURNING id INTO v_id;
    INSERT INTO stock_count_items (stock_count_id, raw_batch_id, wip_issue_item_id, kitchen_batch_id,
                                   book_quantity, actual_quantity, reason)
    SELECT v_id, x.raw_batch_id, x.wip_issue_item_id, x.kitchen_batch_id,
           COALESCE((SELECT quantity_on_hand FROM inventory_batches WHERE id = x.raw_batch_id),
                    (SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = x.wip_issue_item_id),
                    (SELECT quantity_on_hand FROM kitchen_batches WHERE id = x.kitchen_batch_id)),
           x.actual_quantity, x.reason
      FROM jsonb_to_recordset(p_payload -> 'items')
           AS x (raw_batch_id BIGINT, wip_issue_item_id BIGINT, kitchen_batch_id BIGINT, actual_quantity NUMERIC, reason TEXT);
    RETURN v_id;
END $$;

-- ---------- 10.8 Nhập kho (4.2) — khóa header; lô RAW mới không cần khóa tài nguyên
CREATE FUNCTION rc_confirm_receipt(p_receipt BIGINT, p_actor BIGINT, p_received_at TIMESTAMPTZ DEFAULT now())
RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    v_batch  BIGINT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    SELECT status INTO v_status FROM stock_receipts WHERE id = p_receipt FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu nhập %s không tồn tại', p_receipt));
    END IF;
    IF v_status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu nhập %s đang %s, không phải DRAFT', p_receipt, v_status));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM stock_receipt_items WHERE stock_receipt_id = p_receipt) THEN
        PERFORM rc_raise('RC422', format('phiếu nhập %s rỗng', p_receipt));
    END IF;
    IF EXISTS (SELECT 1 FROM stock_receipt_items i JOIN ingredients g ON g.id = i.ingredient_id
                WHERE i.stock_receipt_id = p_receipt AND NOT g.is_active) THEN
        PERFORM rc_raise('RC422', format('phiếu nhập %s có nguyên liệu đã ngừng sử dụng', p_receipt));
    END IF;
    IF EXISTS (SELECT 1 FROM stock_receipt_items i JOIN ingredients g ON g.id = i.ingredient_id
                WHERE i.stock_receipt_id = p_receipt AND g.is_expiry_tracked AND i.expiry_date IS NULL) THEN
        PERFORM rc_raise('RC422', format('phiếu nhập %s: nguyên liệu theo dõi hạn dùng phải có expiry_date', p_receipt));
    END IF;
    IF EXISTS (SELECT 1 FROM stock_receipt_items WHERE stock_receipt_id = p_receipt AND expiry_date < rc_business_date(now())) THEN
        PERFORM rc_raise('RC422', format('phiếu nhập %s có lô đã hết hạn: không nhận vào kho', p_receipt));
    END IF;
    FOR r IN SELECT id, input_quantity * conversion_factor AS qty
               FROM stock_receipt_items WHERE stock_receipt_id = p_receipt ORDER BY id
    LOOP
        INSERT INTO inventory_batches (stock_receipt_item_id, quantity_on_hand) VALUES (r.id, r.qty)
        RETURNING id INTO v_batch;
        INSERT INTO stock_movements (movement_type, raw_batch_id, quantity_delta, event_key)
        VALUES ('RECEIPT', v_batch, r.qty, 'RECEIPT:' || r.id);
    END LOOP;
    -- ghi sổ trước, đổi trạng thái sau: guard chứng từ kiểm mọi dòng đã có bút toán RECEIPT
    UPDATE stock_receipts
       SET status = 'CONFIRMED', received_at = p_received_at, confirmed_by_id = p_actor, confirmed_at = now()
     WHERE id = p_receipt;
END $$;

-- ---------- 10.9 Xuất kho, nhận, hủy (4.3) — khóa header → inventory_batches (mức 7)
CREATE FUNCTION rc_issue_stock(p_issue BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    SELECT status INTO v_status FROM stock_issues WHERE id = p_issue FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu xuất %s không tồn tại', p_issue));
    END IF;
    IF v_status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu xuất %s đang %s, không phải DRAFT', p_issue, v_status));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM stock_issue_items WHERE stock_issue_id = p_issue) THEN
        PERFORM rc_raise('RC422', format('phiếu xuất %s rỗng', p_issue));
    END IF;
    -- dòng DIRECT cần loại bếp DIRECT đang dùng của nguyên liệu gốc (báo sớm thay vì lỗi lúc bếp nhận)
    IF EXISTS (SELECT 1
                 FROM stock_issue_items l
                 JOIN inventory_batches b ON b.id = l.inventory_batch_id
                 JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                WHERE l.stock_issue_id = p_issue AND l.fulfillment_mode = 'DIRECT'
                  AND NOT EXISTS (SELECT 1 FROM kitchen_ingredient_types t
                                   WHERE t.raw_ingredient_id = s.ingredient_id AND t.handling_type = 'DIRECT' AND t.is_active)) THEN
        PERFORM rc_raise('RC422', format('phiếu xuất %s có dòng DIRECT chưa khai báo loại bếp DIRECT', p_issue));
    END IF;
    -- khóa mọi lô của các nguyên liệu liên quan (cần cho kiểm tra FEFO), theo id
    PERFORM 1 FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.ingredient_id IN (SELECT s2.ingredient_id
                                 FROM stock_issue_items l
                                 JOIN inventory_batches b2 ON b2.id = l.inventory_batch_id
                                 JOIN stock_receipt_items s2 ON s2.id = b2.stock_receipt_item_id
                                WHERE l.stock_issue_id = p_issue)
     ORDER BY b.id FOR UPDATE OF b;
    FOR r IN SELECT l.inventory_batch_id AS batch_id, SUM(l.quantity) AS qty,
                    b.quantity_on_hand, b.is_usable, s.expiry_date
               FROM stock_issue_items l
               JOIN inventory_batches b ON b.id = l.inventory_batch_id
               JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
              WHERE l.stock_issue_id = p_issue
              GROUP BY l.inventory_batch_id, b.quantity_on_hand, b.is_usable, s.expiry_date
    LOOP
        IF NOT r.is_usable OR r.expiry_date < rc_business_date(now()) THEN
            PERFORM rc_raise('RC409', format('lô RAW %s không dùng được hoặc đã hết hạn', r.batch_id));
        END IF;
        IF r.qty > r.quantity_on_hand THEN
            PERFORM rc_raise('RC409', format('lô RAW %s chỉ còn %s, phiếu cần %s', r.batch_id, r.quantity_on_hand, r.qty));
        END IF;
    END LOOP;
    -- FEFO: lô cùng nguyên liệu có hạn sớm hơn (cùng hạn: nhận trước) phải được dùng hết trước
    IF EXISTS (
        SELECT 1
          FROM (SELECT inventory_batch_id, SUM(quantity) AS q FROM stock_issue_items
                 WHERE stock_issue_id = p_issue GROUP BY inventory_batch_id) used
          JOIN inventory_batches b ON b.id = used.inventory_batch_id
          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
          JOIN stock_receipt_items s2 ON s2.ingredient_id = s.ingredient_id
          JOIN inventory_batches b2 ON b2.stock_receipt_item_id = s2.id AND b2.id <> b.id AND b2.is_usable
         WHERE (s2.expiry_date IS NULL OR s2.expiry_date >= rc_business_date(now()))
           AND (   (s2.expiry_date IS NOT NULL AND s.expiry_date IS NULL)
                OR s2.expiry_date < s.expiry_date
                OR (s2.expiry_date IS NOT DISTINCT FROM s.expiry_date AND (b2.received_at, b2.id) < (b.received_at, b.id)))
           AND b2.quantity_on_hand - COALESCE((SELECT SUM(l2.quantity) FROM stock_issue_items l2
                                                WHERE l2.stock_issue_id = p_issue AND l2.inventory_batch_id = b2.id), 0) > 0)
    THEN
        PERFORM rc_raise('RC409', format('phiếu xuất %s không theo FEFO: còn lô hạn sớm hơn chưa dùng hết', p_issue));
    END IF;
    FOR r IN SELECT id, inventory_batch_id, quantity FROM stock_issue_items WHERE stock_issue_id = p_issue ORDER BY id LOOP
        UPDATE inventory_batches SET quantity_on_hand = quantity_on_hand - r.quantity WHERE id = r.inventory_batch_id;
        INSERT INTO stock_movements (movement_type, raw_batch_id, ref_issue_item_id, quantity_delta, event_key)
        VALUES ('ISSUE_OUT', r.inventory_batch_id, r.id, -r.quantity, 'ISSUE_OUT:' || r.id);
    END LOOP;
    UPDATE stock_issues SET status = 'ISSUED', issued_at = now(), issued_by_id = p_actor WHERE id = p_issue;
END $$;

-- Bếp nhận bàn giao: toàn bộ hoặc không (PREPARE → WIP; DIRECT → lô bếp). Nhận một lần.
CREATE FUNCTION rc_receive_issue(p_issue BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    v_type   BIGINT;
    v_shelf  INT;
    v_kb     BIGINT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    SELECT status INTO v_status FROM stock_issues WHERE id = p_issue FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu xuất %s không tồn tại', p_issue));
    END IF;
    IF v_status <> 'ISSUED' THEN
        PERFORM rc_raise('RC409', format('phiếu xuất %s đang %s, không phải ISSUED (nhận một lần, nhận đủ cả phiếu)', p_issue, v_status));
    END IF;
    FOR r IN SELECT l.id, l.quantity, l.fulfillment_mode, s.ingredient_id, s.expiry_date
               FROM stock_issue_items l
               JOIN inventory_batches b ON b.id = l.inventory_batch_id
               JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
              WHERE l.stock_issue_id = p_issue ORDER BY l.id
    LOOP
        IF r.fulfillment_mode = 'PREPARE' THEN
            UPDATE stock_issue_items SET wip_remaining_quantity = quantity WHERE id = r.id;
            INSERT INTO stock_movements (movement_type, wip_issue_item_id, ref_issue_item_id, quantity_delta, event_key)
            VALUES ('ISSUE_RECEIVED', r.id, r.id, r.quantity, 'ISSUE_RECEIVED:' || r.id);
        ELSE
            SELECT id, shelf_life_hours INTO v_type, v_shelf FROM kitchen_ingredient_types
             WHERE raw_ingredient_id = r.ingredient_id AND handling_type = 'DIRECT' AND is_active;
            IF NOT FOUND THEN
                PERFORM rc_raise('RC422', format('nguyên liệu %s chưa khai báo loại bếp DIRECT', r.ingredient_id));
            END IF;
            INSERT INTO kitchen_batches (kitchen_ingredient_type_id, direct_issue_item_id, usable_until, quantity_on_hand)
            VALUES (v_type, r.id, rc_usable_until(r.expiry_date, v_shelf), r.quantity)
            RETURNING id INTO v_kb;
            INSERT INTO stock_movements (movement_type, kitchen_batch_id, ref_issue_item_id, quantity_delta, event_key)
            VALUES ('DIRECT_IN', v_kb, r.id, r.quantity, 'DIRECT_IN:' || r.id);
        END IF;
    END LOOP;
    UPDATE stock_issues SET status = 'RECEIVED', received_by_id = p_actor, received_at = now() WHERE id = p_issue;
END $$;

CREATE FUNCTION rc_cancel_issue(p_issue BIGINT, p_actor BIGINT, p_reason TEXT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_reason IS NULL OR btrim(p_reason) = '' THEN
        PERFORM rc_raise('RC422', 'hủy phiếu xuất bắt buộc có lý do');
    END IF;
    SELECT status INTO v_status FROM stock_issues WHERE id = p_issue FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu xuất %s không tồn tại', p_issue));
    END IF;
    IF v_status NOT IN ('DRAFT', 'ISSUED') THEN
        PERFORM rc_raise('RC409', format('phiếu xuất %s ở %s không hủy được (đã nhận → dùng phiếu hoàn trả)', p_issue, v_status));
    END IF;
    IF v_status = 'ISSUED' THEN
        PERFORM 1 FROM inventory_batches
         WHERE id IN (SELECT inventory_batch_id FROM stock_issue_items WHERE stock_issue_id = p_issue)
         ORDER BY id FOR UPDATE;
        FOR r IN SELECT id, inventory_batch_id, quantity FROM stock_issue_items WHERE stock_issue_id = p_issue ORDER BY id LOOP
            UPDATE inventory_batches SET quantity_on_hand = quantity_on_hand + r.quantity WHERE id = r.inventory_batch_id;
            INSERT INTO stock_movements (movement_type, raw_batch_id, ref_issue_item_id, quantity_delta, event_key)
            VALUES ('ISSUE_REVERSAL', r.inventory_batch_id, r.id, r.quantity, 'ISSUE_REVERSAL:' || r.id);
        END LOOP;
    END IF;
    UPDATE stock_issues
       SET status = 'CANCELLED', cancel_reason = btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor
     WHERE id = p_issue;
END $$;

-- ---------- 10.10 Sơ chế (4.4) — khóa phiếu → dòng WIP (mức 8)
CREATE FUNCTION rc_confirm_preparation(p_prep BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_prep  kitchen_preparations%ROWTYPE;
    v_line  RECORD;
    v_out   NUMERIC;
    v_n     INT;
    v_until TIMESTAMPTZ;
    v_kb    BIGINT;
    r       RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'KITCHEN', 'MANAGER');
    SELECT * INTO v_prep FROM kitchen_preparations WHERE id = p_prep FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('phiếu sơ chế %s không tồn tại', p_prep));
    END IF;
    IF v_prep.status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu sơ chế %s đang %s, không phải DRAFT', p_prep, v_prep.status));
    END IF;
    SELECT l.id, l.fulfillment_mode, l.wip_remaining_quantity, si.status AS issue_status,
           s.ingredient_id, s.expiry_date
      INTO v_line
      FROM stock_issue_items l
      JOIN stock_issues si ON si.id = l.stock_issue_id
      JOIN inventory_batches b ON b.id = l.inventory_batch_id
      JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE l.id = v_prep.stock_issue_item_id
       FOR UPDATE OF l;
    IF v_line.fulfillment_mode <> 'PREPARE' OR v_line.issue_status <> 'RECEIVED' THEN
        PERFORM rc_raise('RC409', format('dòng xuất %s không phải WIP đã nhận', v_prep.stock_issue_item_id));
    END IF;
    IF v_prep.input_quantity > v_line.wip_remaining_quantity THEN
        PERFORM rc_raise('RC409', format('đầu vào %s vượt WIP còn %s', v_prep.input_quantity, v_line.wip_remaining_quantity));
    END IF;
    SELECT COALESCE(SUM(output_quantity), 0), count(*) INTO v_out, v_n
      FROM kitchen_preparation_outputs WHERE kitchen_preparation_id = p_prep;
    IF v_n = 0 OR v_out <= 0 OR v_out > v_prep.input_quantity THEN
        PERFORM rc_raise('RC422', format('tổng đầu ra %s phải lớn hơn 0 và không vượt đầu vào %s', v_out, v_prep.input_quantity));
    END IF;
    IF EXISTS (SELECT 1 FROM kitchen_preparation_outputs o
                 JOIN kitchen_ingredient_types t ON t.id = o.kitchen_ingredient_type_id
                WHERE o.kitchen_preparation_id = p_prep
                  AND (t.handling_type <> 'PREPARED' OR t.raw_ingredient_id <> v_line.ingredient_id)) THEN
        PERFORM rc_raise('RC422', 'đầu ra phải là loại PREPARED của cùng nguyên liệu gốc');
    END IF;
    IF EXISTS (SELECT 1 FROM kitchen_preparation_outputs
                WHERE kitchen_preparation_id = p_prep
                  AND usable_until > rc_usable_until(v_line.expiry_date, NULL)) THEN
        PERFORM rc_raise('RC422', 'hạn dùng đầu ra vượt hạn lô nguyên liệu gốc');
    END IF;
    UPDATE stock_issue_items SET wip_remaining_quantity = wip_remaining_quantity - v_prep.input_quantity
     WHERE id = v_line.id;
    INSERT INTO stock_movements (movement_type, wip_issue_item_id, preparation_id, quantity_delta, event_key)
    VALUES ('PREP_INPUT', v_line.id, p_prep, -v_prep.input_quantity, 'PREP_INPUT:' || p_prep);
    FOR r IN SELECT o.id, o.kitchen_ingredient_type_id, o.output_quantity, o.usable_until, t.shelf_life_hours
               FROM kitchen_preparation_outputs o
               JOIN kitchen_ingredient_types t ON t.id = o.kitchen_ingredient_type_id
              WHERE o.kitchen_preparation_id = p_prep ORDER BY o.id
    LOOP
        v_until := COALESCE(r.usable_until, rc_usable_until(v_line.expiry_date, r.shelf_life_hours));
        IF r.usable_until IS NULL AND v_until IS NOT NULL THEN
            UPDATE kitchen_preparation_outputs SET usable_until = v_until WHERE id = r.id;
        END IF;
        INSERT INTO kitchen_batches (kitchen_ingredient_type_id, preparation_output_id, usable_until, quantity_on_hand)
        VALUES (r.kitchen_ingredient_type_id, r.id, v_until, r.output_quantity)
        RETURNING id INTO v_kb;
        INSERT INTO stock_movements (movement_type, kitchen_batch_id, preparation_id, quantity_delta, event_key)
        VALUES ('PREP_OUTPUT', v_kb, p_prep, r.output_quantity, 'PREP_OUTPUT:' || r.id);
    END LOOP;
    UPDATE kitchen_preparations SET status = 'CONFIRMED', prepared_at = now(), prepared_by_id = p_actor
     WHERE id = p_prep;
END $$;

-- ---------- 10.11 Hoàn trả, hủy hàng, kiểm kê (4.3, 4.7)
-- Khóa tài nguyên theo thứ tự chung: inventory_batches (7) → stock_issue_items (8) → kitchen_batches (9, theo loại rồi id)
CREATE FUNCTION rc_confirm_return(p_return BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status TEXT;
    r        RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    SELECT status INTO v_status FROM stock_returns WHERE id = p_return FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu trả %s không tồn tại', p_return));
    END IF;
    IF v_status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu trả %s đang %s, không phải DRAFT', p_return, v_status));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM stock_return_items WHERE stock_return_id = p_return) THEN
        PERFORM rc_raise('RC422', format('phiếu trả %s rỗng', p_return));
    END IF;
    PERFORM 1 FROM inventory_batches
     WHERE id IN (SELECT l.inventory_batch_id FROM stock_return_items ri JOIN stock_issue_items l ON l.id = ri.source_issue_item_id
                   WHERE ri.stock_return_id = p_return)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM stock_issue_items
     WHERE id IN (SELECT source_issue_item_id FROM stock_return_items WHERE stock_return_id = p_return)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM kitchen_batches
     WHERE id IN (SELECT direct_kitchen_batch_id FROM stock_return_items
                   WHERE stock_return_id = p_return AND direct_kitchen_batch_id IS NOT NULL)
     ORDER BY kitchen_ingredient_type_id, id FOR UPDATE;
    FOR r IN SELECT ri.id, ri.source_issue_item_id, ri.direct_kitchen_batch_id, ri.quantity,
                    l.fulfillment_mode, l.inventory_batch_id, si.status AS issue_status
               FROM stock_return_items ri
               JOIN stock_issue_items l ON l.id = ri.source_issue_item_id
               JOIN stock_issues si ON si.id = l.stock_issue_id
              WHERE ri.stock_return_id = p_return ORDER BY ri.id
    LOOP
        IF r.issue_status <> 'RECEIVED' THEN
            PERFORM rc_raise('RC409', format('dòng trả %s: chỉ trả hàng của phiếu xuất đã nhận', r.id));
        END IF;
        IF r.fulfillment_mode = 'PREPARE' THEN
            IF r.direct_kitchen_batch_id IS NOT NULL THEN
                PERFORM rc_raise('RC422', format('dòng trả %s: hàng WIP không kèm lô bếp', r.id));
            END IF;
            UPDATE stock_issue_items SET wip_remaining_quantity = wip_remaining_quantity - r.quantity
             WHERE id = r.source_issue_item_id AND wip_remaining_quantity >= r.quantity;
            IF NOT FOUND THEN
                PERFORM rc_raise('RC409', format('dòng trả %s: vượt lượng WIP còn lại', r.id));
            END IF;
            INSERT INTO stock_movements (movement_type, wip_issue_item_id, stock_return_item_id, quantity_delta, event_key)
            VALUES ('RETURN', r.source_issue_item_id, r.id, -r.quantity, 'RETURN-OUT:' || r.id);
        ELSE
            IF r.direct_kitchen_batch_id IS NULL THEN
                PERFORM rc_raise('RC422', format('dòng trả %s: hàng DIRECT phải chỉ rõ lô bếp', r.id));
            END IF;
            UPDATE kitchen_batches SET quantity_on_hand = quantity_on_hand - r.quantity
             WHERE id = r.direct_kitchen_batch_id AND is_usable
               AND quantity_on_hand - reserved_quantity >= r.quantity;
            IF NOT FOUND THEN
                PERFORM rc_raise('RC409', format('dòng trả %s: vượt lượng khả dụng (phần đang giữ cho món không trả được)', r.id));
            END IF;
            INSERT INTO stock_movements (movement_type, kitchen_batch_id, stock_return_item_id, quantity_delta, event_key)
            VALUES ('RETURN', r.direct_kitchen_batch_id, r.id, -r.quantity, 'RETURN-OUT:' || r.id);
        END IF;
        UPDATE inventory_batches SET quantity_on_hand = quantity_on_hand + r.quantity WHERE id = r.inventory_batch_id;
        INSERT INTO stock_movements (movement_type, raw_batch_id, stock_return_item_id, quantity_delta, event_key)
        VALUES ('RETURN', r.inventory_batch_id, r.id, r.quantity, 'RETURN-IN:' || r.id);
    END LOOP;
    UPDATE stock_returns SET status = 'CONFIRMED', confirmed_by_id = p_actor, confirmed_at = now() WHERE id = p_return;
END $$;

CREATE FUNCTION rc_confirm_adjustment(p_adjustment BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_adj stock_adjustments%ROWTYPE;
    r     RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    SELECT * INTO v_adj FROM stock_adjustments WHERE id = p_adjustment FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM rc_raise('RC422', format('phiếu hủy hàng %s không tồn tại', p_adjustment));
    END IF;
    IF v_adj.status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu hủy hàng %s đang %s, không phải DRAFT', p_adjustment, v_adj.status));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM stock_adjustment_items WHERE stock_adjustment_id = p_adjustment) THEN
        PERFORM rc_raise('RC422', format('phiếu hủy hàng %s rỗng', p_adjustment));
    END IF;
    PERFORM 1 FROM inventory_batches
     WHERE id IN (SELECT raw_batch_id FROM stock_adjustment_items WHERE stock_adjustment_id = p_adjustment AND raw_batch_id IS NOT NULL)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM stock_issue_items
     WHERE id IN (SELECT wip_issue_item_id FROM stock_adjustment_items WHERE stock_adjustment_id = p_adjustment AND wip_issue_item_id IS NOT NULL)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM kitchen_batches
     WHERE id IN (SELECT kitchen_batch_id FROM stock_adjustment_items WHERE stock_adjustment_id = p_adjustment AND kitchen_batch_id IS NOT NULL)
     ORDER BY kitchen_ingredient_type_id, id FOR UPDATE;
    FOR r IN SELECT * FROM stock_adjustment_items WHERE stock_adjustment_id = p_adjustment ORDER BY id LOOP
        IF r.raw_batch_id IS NOT NULL THEN
            UPDATE inventory_batches SET quantity_on_hand = quantity_on_hand + r.quantity_delta
             WHERE id = r.raw_batch_id AND quantity_on_hand + r.quantity_delta >= 0;
        ELSIF r.wip_issue_item_id IS NOT NULL THEN
            UPDATE stock_issue_items SET wip_remaining_quantity = wip_remaining_quantity + r.quantity_delta
             WHERE id = r.wip_issue_item_id AND wip_remaining_quantity + r.quantity_delta >= 0;
        ELSE
            UPDATE kitchen_batches SET quantity_on_hand = quantity_on_hand + r.quantity_delta
             WHERE id = r.kitchen_batch_id AND quantity_on_hand + r.quantity_delta >= reserved_quantity;
        END IF;
        IF NOT FOUND THEN
            PERFORM rc_raise('RC409', format('dòng hủy %s: vượt lượng khả dụng (lô bếp: phải cấp lại món đang giữ trước)', r.id));
        END IF;
        INSERT INTO stock_movements (movement_type, raw_batch_id, wip_issue_item_id, kitchen_batch_id,
                                     stock_adjustment_item_id, quantity_delta, reason, event_key)
        VALUES ('WASTE', r.raw_batch_id, r.wip_issue_item_id, r.kitchen_batch_id,
                r.id, r.quantity_delta, v_adj.adjustment_type, 'WASTE:' || r.id);
    END LOOP;
    UPDATE stock_adjustments SET status = 'CONFIRMED', confirmed_by_id = p_actor, confirmed_at = now()
     WHERE id = p_adjustment;
END $$;

CREATE FUNCTION rc_confirm_count(p_count BIGINT, p_actor BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    v_status   TEXT;
    v_now      NUMERIC;
    v_reserved NUMERIC;
    v_diff     NUMERIC;
    r          RECORD;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    SELECT status INTO v_status FROM stock_counts WHERE id = p_count FOR UPDATE;
    IF v_status IS NULL THEN
        PERFORM rc_raise('RC422', format('phiếu kiểm kê %s không tồn tại', p_count));
    END IF;
    IF v_status <> 'DRAFT' THEN
        PERFORM rc_raise('RC409', format('phiếu kiểm kê %s đang %s, không phải DRAFT', p_count, v_status));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM stock_count_items WHERE stock_count_id = p_count) THEN
        PERFORM rc_raise('RC422', format('phiếu kiểm kê %s rỗng', p_count));
    END IF;
    PERFORM 1 FROM inventory_batches
     WHERE id IN (SELECT raw_batch_id FROM stock_count_items WHERE stock_count_id = p_count AND raw_batch_id IS NOT NULL)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM stock_issue_items
     WHERE id IN (SELECT wip_issue_item_id FROM stock_count_items WHERE stock_count_id = p_count AND wip_issue_item_id IS NOT NULL)
     ORDER BY id FOR UPDATE;
    PERFORM 1 FROM kitchen_batches
     WHERE id IN (SELECT kitchen_batch_id FROM stock_count_items WHERE stock_count_id = p_count AND kitchen_batch_id IS NOT NULL)
     ORDER BY kitchen_ingredient_type_id, id FOR UPDATE;
    FOR r IN SELECT * FROM stock_count_items WHERE stock_count_id = p_count ORDER BY id LOOP
        v_reserved := 0;
        IF r.raw_batch_id IS NOT NULL THEN
            SELECT quantity_on_hand INTO v_now FROM inventory_batches WHERE id = r.raw_batch_id;
        ELSIF r.wip_issue_item_id IS NOT NULL THEN
            SELECT wip_remaining_quantity INTO v_now FROM stock_issue_items WHERE id = r.wip_issue_item_id;
        ELSE
            SELECT quantity_on_hand, reserved_quantity INTO v_now, v_reserved FROM kitchen_batches WHERE id = r.kitchen_batch_id;
        END IF;
        IF v_now <> r.book_quantity THEN
            PERFORM rc_raise('RC409', format('dòng kiểm kê %s: số sổ đã đổi từ %s thành %s — lập lại phiếu (stale)', r.id, r.book_quantity, v_now));
        END IF;
        IF r.actual_quantity < v_reserved THEN
            PERFORM rc_raise('RC409', format('dòng kiểm kê %s: thực đếm %s nhỏ hơn lượng đang giữ %s', r.id, r.actual_quantity, v_reserved));
        END IF;
        v_diff := r.actual_quantity - r.book_quantity;
        CONTINUE WHEN v_diff = 0;
        IF r.raw_batch_id IS NOT NULL THEN
            UPDATE inventory_batches SET quantity_on_hand = r.actual_quantity WHERE id = r.raw_batch_id;
        ELSIF r.wip_issue_item_id IS NOT NULL THEN
            UPDATE stock_issue_items SET wip_remaining_quantity = r.actual_quantity WHERE id = r.wip_issue_item_id;
        ELSE
            UPDATE kitchen_batches SET quantity_on_hand = r.actual_quantity WHERE id = r.kitchen_batch_id;
        END IF;
        INSERT INTO stock_movements (movement_type, raw_batch_id, wip_issue_item_id, kitchen_batch_id,
                                     stock_count_item_id, quantity_delta, reason, event_key)
        VALUES ('COUNT_ADJUST', r.raw_batch_id, r.wip_issue_item_id, r.kitchen_batch_id,
                r.id, v_diff, r.reason, 'COUNT:' || r.id);
    END LOOP;
    UPDATE stock_counts SET status = 'CONFIRMED', confirmed_by_id = p_actor, confirmed_at = now() WHERE id = p_count;
END $$;

-- ---------- 10.12 Đối soát tiền mặt (5.3) — mỗi lần gọi tạo một phiên bản mới (không idempotent theo thiết kế)
CREATE FUNCTION rc_reconcile_cash(p_business_date DATE, p_opening_cash NUMERIC, p_actual_cash NUMERIC, p_actor BIGINT,
                                  p_reason TEXT DEFAULT NULL, p_revision_reason TEXT DEFAULT NULL)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_version INT;
    v_id      BIGINT;
BEGIN
    PERFORM rc_authorize(p_actor, 'MANAGER');
    IF p_business_date IS NULL OR p_business_date > rc_business_date(now()) THEN
        PERFORM rc_raise('RC422', 'ngày kinh doanh không hợp lệ hoặc ở tương lai');
    END IF;
    IF p_opening_cash IS NULL OR p_actual_cash IS NULL THEN
        PERFORM rc_raise('RC422', 'thiếu quỹ đầu ngày hoặc số tiền thực đếm');
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('rc_reconcile_cash'), p_business_date - DATE '2000-01-01');
    SELECT COALESCE(MAX(version), 0) INTO v_version FROM daily_cash_reconciliations WHERE business_date = p_business_date;
    UPDATE daily_cash_reconciliations SET status = 'SUPERSEDED'
     WHERE business_date = p_business_date AND status = 'CURRENT';
    INSERT INTO daily_cash_reconciliations
        (business_date, version, status, opening_cash, cash_sales, cash_expenses, actual_cash,
         reason, revision_reason, reconciled_by_id)
    VALUES (p_business_date, v_version + 1, 'CURRENT', p_opening_cash,
            (SELECT COALESCE(SUM(amount), 0) FROM payments
              WHERE business_date = p_business_date AND method = 'CASH'),
            (SELECT COALESCE(SUM(amount), 0) FROM operating_expenses
              WHERE business_date = p_business_date AND status = 'PAID' AND fund_source = 'CASH_DRAWER'),
            p_actual_cash, p_reason, p_revision_reason, p_actor)
    RETURNING id INTO v_id;
    RETURN v_id;
END $$;

-- =====================================================================
-- 11. QUYỀN: hàm nghiệp vụ chạy với quyền chủ sở hữu, không mở cho PUBLIC (spec I4)
-- =====================================================================
DO $$
DECLARE
    f RECORD;
BEGIN
    FOR f IN SELECT p.oid::regprocedure AS sig, p.prorettype = 'trigger'::regtype AS is_trigger
               FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
              WHERE n.nspname = current_schema() AND p.proname LIKE 'rc\_%'
    LOOP
        EXECUTE format('ALTER FUNCTION %s SECURITY DEFINER SET search_path = %I, pg_temp', f.sig, current_schema());
        IF NOT f.is_trigger THEN
            EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f.sig);
        END IF;
    END LOOP;
END $$;
-- GRANT/REVOKE theo role ứng dụng (rc_app, rc_webhook, rc_readonly) và chủ sở hữu rc_owner: 09-acl-v4.1.sql.

COMMIT;
