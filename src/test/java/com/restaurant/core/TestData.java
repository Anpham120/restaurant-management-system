package com.restaurant.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Dựng dữ liệu test bằng tài khoản quản trị. Mỗi lần gọi dùng mã/tên mới nên các test không giẫm lên nhau. */
public class TestData {

    public static final String PASSWORD = "secret-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    public record Staff(long employeeId, String username) {
    }

    private final JdbcClient admin;
    private final String passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);

    public TestData(JdbcClient admin) {
        this.admin = admin;
    }

    public Staff staff(String role) {
        int n = SEQ.incrementAndGet();
        long dept = admin.sql("""
                INSERT INTO departments (department_name) VALUES ('Test') ON CONFLICT (department_name) DO UPDATE SET department_name = EXCLUDED.department_name
                RETURNING id""").query(Long.class).single();
        long emp = admin.sql("INSERT INTO employees (employee_code, full_name, department_id) VALUES (:c, :n, :d) RETURNING id")
                .param("c", "T" + n).param("n", "Nhân viên " + role + " " + n).param("d", dept).query(Long.class).single();
        String username = role.toLowerCase() + n;
        long acc = admin.sql("INSERT INTO accounts (employee_id, username, password_hash) VALUES (:e, :u, :h) RETURNING id")
                .param("e", emp).param("u", username).param("h", passwordHash).query(Long.class).single();
        admin.sql("INSERT INTO account_roles (account_id, role_id) SELECT :a, id FROM roles WHERE role_code = :r")
                .param("a", acc).param("r", role).update();
        return new Staff(emp, username);
    }

    public void lockAccount(Staff staff) {
        admin.sql("UPDATE accounts SET account_status = 'LOCKED' WHERE employee_id = :e").param("e", staff.employeeId()).update();
    }

    public long table() {
        return admin.sql("INSERT INTO dining_tables (code, capacity) VALUES (:c, 4) RETURNING id")
                .param("c", "TT" + SEQ.incrementAndGet()).query(Long.class).single();
    }

    private Staff setupManager;
    private Staff setupKitchen;

    /** Nhân viên dùng nội bộ để dựng kho (nhập/xuất/nhận) — không dùng trong kiểm tra quyền. */
    private synchronized Staff manager() {
        if (setupManager == null) {
            setupManager = staff("MANAGER");
            setupKitchen = staff("KITCHEN");
        }
        return setupManager;
    }

    /**
     * Một món trong thực đơn PUBLISHED của ngày kinh doanh hiện tại.
     * quota NULL = không giới hạn suất. stock NULL = công thức không nguyên liệu; có giá trị = 1 đơn vị nguyên liệu/suất,
     * tồn bếp thật đúng bằng stock (dựng qua rc_create_stock_receipt → rc_confirm_receipt → rc_create_stock_issue →
     * rc_issue_stock → rc_receive_issue, như nghiệp vụ kho thật).
     */
    public Dish dish(long price, Integer quota, Integer stock) {
        int n = SEQ.incrementAndGet();
        Long type = stock == null ? null : kitchenStock("Nguyên liệu " + n, stock);
        long cat = admin.sql("INSERT INTO menu_categories (name) VALUES (:n) RETURNING id")
                .param("n", "Nhóm " + n).query(Long.class).single();
        long item = admin.sql("INSERT INTO menu_items (menu_category_id, name, price) VALUES (:c, :n, :p) RETURNING id")
                .param("c", cat).param("n", "Món " + n).param("p", price).query(Long.class).single();
        long recipe = admin.sql("INSERT INTO recipes (menu_item_id, version, is_current) VALUES (:i, 1, true) RETURNING id")
                .param("i", item).query(Long.class).single();
        if (type != null) {
            admin.sql("INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES (:r, :t, 1)")
                    .param("r", recipe).param("t", type).update();
        }
        long menu = admin.sql("""
                INSERT INTO daily_menus (business_date, status) VALUES (rc_business_date(now()), 'DRAFT')
                ON CONFLICT (business_date) DO UPDATE SET business_date = EXCLUDED.business_date RETURNING id""")
                .query(Long.class).single();
        long dmi = admin.sql("INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES (:m, :i, :r, :q) RETURNING id")
                .param("m", menu).param("i", item).param("r", recipe).param("q", quota).query(Long.class).single();
        admin.sql("UPDATE daily_menus SET status = 'PUBLISHED', published_at = now() WHERE id = :m AND status = 'DRAFT'")
                .param("m", menu).update();
        return new Dish(dmi, type);
    }

    public record Dish(long dailyMenuItemId, Long kitchenTypeId) {
    }

    private long kitchenStock(String name, int quantity) {
        Staff m = manager();
        long ing = admin.sql("INSERT INTO ingredients (name, base_unit, is_expiry_tracked) VALUES (:n, 'phần', false) RETURNING id")
                .param("n", name).query(Long.class).single();
        long type = admin.sql("INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type) VALUES (:i, :n, 'DIRECT') RETURNING id")
                .param("i", ing).param("n", name).query(Long.class).single();
        long receipt = admin.sql("SELECT rc_create_stock_receipt(:a, :k, CAST(:p AS jsonb))").param("a", m.employeeId())
                .param("k", "td-r-" + ing).param("p", "{\"items\":[{\"ingredient_id\":" + ing + ",\"input_quantity\":" + quantity
                        + ",\"input_unit\":\"phần\",\"conversion_factor\":1,\"unit_price_per_input_unit\":1000}]}")
                .query(Long.class).single();
        admin.sql("SELECT rc_confirm_receipt(:r, :a, now())").param("r", receipt).param("a", m.employeeId()).query().singleRow();
        long lot = admin.sql("SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = :r")
                .param("r", receipt).query(Long.class).single();
        long issue = admin.sql("SELECT rc_create_stock_issue(:a, :k, CAST(:p AS jsonb))").param("a", m.employeeId())
                .param("k", "td-i-" + ing).param("p", "{\"items\":[{\"inventory_batch_id\":" + lot + ",\"quantity\":" + quantity
                        + ",\"fulfillment_mode\":\"DIRECT\"}]}")
                .query(Long.class).single();
        admin.sql("SELECT rc_issue_stock(:i, :a)").param("i", issue).param("a", m.employeeId()).query().singleRow();
        admin.sql("SELECT rc_receive_issue(:i, :a)").param("i", issue).param("a", setupKitchen.employeeId()).query().singleRow();
        return type;
    }

    /** Tồn bếp vật lý / đang giữ của loại nguyên liệu (kiểm tra tiêu hao, không hoàn kho). */
    public java.math.BigDecimal kitchenOnHand(long typeId) {
        return admin.sql("SELECT COALESCE(SUM(quantity_on_hand), 0) FROM kitchen_batches WHERE kitchen_ingredient_type_id = :t")
                .param("t", typeId).query(java.math.BigDecimal.class).single();
    }

    public java.math.BigDecimal kitchenReserved(long typeId) {
        return admin.sql("SELECT COALESCE(SUM(reserved_quantity), 0) FROM kitchen_batches WHERE kitchen_ingredient_type_id = :t")
                .param("t", typeId).query(java.math.BigDecimal.class).single();
    }

    public long count(String sql, Object... params) {
        var spec = admin.sql(sql);
        for (int i = 0; i < params.length; i++) {
            spec = spec.param(i + 1, params[i]);
        }
        return spec.query(Long.class).single();
    }

    public String value(String sql, Object... params) {
        var spec = admin.sql(sql);
        for (int i = 0; i < params.length; i++) {
            spec = spec.param(i + 1, params[i]);
        }
        return spec.query(String.class).optional().orElse(null);
    }

    /** Đi hết luồng thật bằng các hàm rc_*: mở bàn → gửi 1 món → nhận → nấu → xong → phục vụ → chốt hóa đơn (50 000). */
    public long pendingInvoice(Staff waiter, Staff kitchen, Staff cashier) {
        long session = admin.sql("SELECT rc_open_session(:t, :w)").param("t", table()).param("w", waiter.employeeId())
                .query(Long.class).single();
        long order = admin.sql("SELECT id FROM orders WHERE table_session_id = :s").param("s", session).query(Long.class).single();
        long batch = admin.sql("""
                SELECT (rc_submit_batch(:o, :w, :k, jsonb_build_array(jsonb_build_object('daily_menu_item_id', :d, 'quantity', 1))) ->> 'batch_id')::bigint""")
                .param("o", order).param("w", waiter.employeeId()).param("k", "test-" + SEQ.incrementAndGet())
                .param("d", dish(50000, null, null).dailyMenuItemId()).query(Long.class).single();
        long item = admin.sql("SELECT id FROM order_items WHERE order_batch_id = :b").param("b", batch).query(Long.class).single();
        admin.sql("SELECT rc_accept_item(:i, :k)").param("i", item).param("k", kitchen.employeeId()).query(String.class).single();
        admin.sql("SELECT rc_start_cooking(:i, :k)").param("i", item).param("k", kitchen.employeeId()).query().singleRow();
        admin.sql("SELECT rc_finish_dish(:i, :k)").param("i", item).param("k", kitchen.employeeId()).query().singleRow();
        admin.sql("SELECT rc_serve_item(:i, :w)").param("i", item).param("w", waiter.employeeId()).query().singleRow();
        return admin.sql("SELECT rc_checkout_invoice(:o, :c)").param("o", order).param("c", cashier.employeeId())
                .query(Long.class).single();
    }

    public String invoiceStatus(long invoiceId) {
        return admin.sql("SELECT status FROM invoices WHERE id = :i").param("i", invoiceId).query(String.class).single();
    }
}
