package com.restaurant.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.service.InvoiceService;
import com.restaurant.core.inventory.dto.request.StockDocumentRequests.IssueLine;
import com.restaurant.core.inventory.dto.request.StockDocumentRequests.IssueRequest;
import com.restaurant.core.inventory.dto.request.StockDocumentRequests.ReceiptLine;
import com.restaurant.core.inventory.dto.request.StockDocumentRequests.ReceiptRequest;
import com.restaurant.core.inventory.service.StockDocumentService;
import com.restaurant.core.order.dto.request.SubmitBatchRequest;
import com.restaurant.core.order.service.KitchenService;
import com.restaurant.core.order.service.OrderService;
import com.restaurant.core.order.service.TableService;

/** Dựng dữ liệu test bằng tài khoản quản trị. Mỗi lần gọi dùng mã/tên mới nên các test không giẫm lên nhau. */
public class TestData {

    public static final String PASSWORD = "secret-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    public record Staff(long employeeId, String username) {
    }

    private final JdbcClient admin;
    private final String passwordHash = new BCryptPasswordEncoder().encode(PASSWORD);
    private ApplicationContext services;

    public TestData(JdbcClient admin) {
        this.admin = admin;
    }

    /** Gắn Spring context của test đang chạy: dữ liệu kho / đơn / hóa đơn được dựng qua các Service thật. */
    public void useServicesFrom(ApplicationContext context) {
        this.services = context;
    }

    public Staff staff(String role) {
        int n = SEQ.incrementAndGet();
        long dept = admin.sql("""
                INSERT INTO departments (department_name) VALUES ('Test') ON CONFLICT (department_name) DO UPDATE SET department_name = EXCLUDED.department_name
                RETURNING id""").query(Long.class).single();
        long emp = admin.sql("INSERT INTO employees (employee_code, full_name, department_id) VALUES (:c, :n, :d) RETURNING id")
                .param("c", "T" + n).param("n", "Nhân viên " + role + " " + n).param("d", dept).query(Long.class).single();
        String username = role.toLowerCase() + n;
        long acc = admin.sql("INSERT INTO accounts (employee_id, username, password_hash, must_change_password) VALUES (:e, :u, :h, false) RETURNING id")
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
     * tồn bếp thật đúng bằng stock (dựng qua StockDocumentService: tạo phiếu nhập → xác nhận → tạo phiếu xuất →
     * xuất kho → bếp nhận, như nghiệp vụ kho thật).
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
                INSERT INTO daily_menus (business_date, status) VALUES (:today, 'DRAFT')
                ON CONFLICT (business_date) DO UPDATE SET business_date = EXCLUDED.business_date RETURNING id""")
                .param("today", BusinessDateProvider.businessDateOf(java.time.Instant.now())).query(Long.class).single();
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
        StockDocumentService stock = services.getBean(StockDocumentService.class);
        long receipt = stock.createStockReceipt(m.employeeId(), new ReceiptRequest("td-r-" + ing, null, null, List.of(new ReceiptLine(ing,
                BigDecimal.valueOf(quantity), "phần", BigDecimal.ONE, BigDecimal.valueOf(1000), null, null)))).id();
        long lot = stock.confirmStockReceipt(receipt, m.employeeId(), null).lines().getFirst().inventoryBatchId();
        long issue = stock.createStockIssue(m.employeeId(), new IssueRequest("td-i-" + ing, null,
                List.of(new IssueLine(lot, BigDecimal.valueOf(quantity), "DIRECT")))).id();
        stock.issueStockToKitchen(issue, m.employeeId());
        stock.receiveIssuedStock(issue, setupKitchen.employeeId());
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

    /** Đi hết luồng thật qua các Service: mở bàn → gửi 1 món → nhận → nấu → xong → phục vụ → chốt hóa đơn (50 000). */
    public long pendingInvoice(Staff waiter, Staff kitchen, Staff cashier) {
        long order = services.getBean(TableService.class).openTableSession(table(), waiter.employeeId()).orderId();
        long dishId = dish(50000, null, null).dailyMenuItemId();
        OrderService orders = services.getBean(OrderService.class);
        long item = orders.submitOrderBatch(order, waiter.employeeId(), new SubmitBatchRequest("test-" + SEQ.incrementAndGet(),
                List.of(new SubmitBatchRequest.Item(dishId, 1, null)))).items().getFirst().id();
        KitchenService kitchenService = services.getBean(KitchenService.class);
        kitchenService.acceptOrderItem(item, kitchen.employeeId());
        kitchenService.startCookingItem(item, kitchen.employeeId());
        kitchenService.finishCookingItem(item, kitchen.employeeId());
        orders.markItemServed(item, waiter.employeeId());
        return services.getBean(InvoiceService.class).checkoutOrder(order, cashier.employeeId()).invoiceId();
    }

    public String invoiceStatus(long invoiceId) {
        return admin.sql("SELECT status FROM invoices WHERE id = :i").param("i", invoiceId).query(String.class).single();
    }
}
