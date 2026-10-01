package vn.khoibep.rms;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for API tests against a real PostgreSQL (Testcontainers). All subclasses share one Spring context and one
 * database, so every test creates its own tables and dishes instead of relying on a clean database.
 */
@SpringBootTest(properties = {
        "app.demo-accounts.enabled=true",
        "app.demo-accounts.password=" + IntegrationTest.PASSWORD,
        "app.sepay.api-key=" + IntegrationTest.SEPAY_KEY})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class IntegrationTest {

    protected static final String PASSWORD = "secret123";
    protected static final String SEPAY_KEY = "test-sepay-key";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100_000);

    @Autowired
    protected MockMvc mvc;

    /** Real time unless a test pins it; released after every test. */
    @Autowired
    protected MutableClock clock;

    /** Cash needs an open shift (BR-39); like a restaurant in service, every test starts with one open. */
    @BeforeEach
    void openCashShift() throws Exception {
        if (get("/api/cash-shifts/current", as("thungan")).andReturn().getResponse().getStatus() == 204) {
            post("/api/cash-shifts", as("thungan"), Map.of("openingFloat", 1_000_000)).andExpect(status().isCreated());
        }
    }

    @AfterEach
    void releaseClock() {
        clock.reset();
    }

    protected record TableRef(long id, String name, String qrToken) {
    }

    protected record EmployeeRef(long id, String username) {
    }

    // ---- HTTP helpers ------------------------------------------------------------------------------------

    /** Token of a demo account (admin, quanly, phucvu, bep, thungan), cached for the whole run. */
    protected String as(String username) throws Exception {
        String token = TOKENS.get(username);
        if (token == null) {
            token = login(username, PASSWORD);
            TOKENS.put(username, token);
        }
        return token;
    }

    protected String login(String username, String password) throws Exception {
        ResultActions result = post("/api/auth/login", null, Map.of("username", username, "password", password))
                .andExpect(status().isOk());
        return read(result, "$.token");
    }

    protected ResultActions get(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.get(url), token, null);
    }

    protected ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url), token, body);
    }

    protected ResultActions put(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(url), token, body);
    }

    protected ResultActions patch(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.patch(url), token, body);
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), token, null);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected static String body(ResultActions result) throws Exception {
        MvcResult mvcResult = result.andReturn();
        return mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    protected static long readLong(ResultActions result, String path) throws Exception {
        return ((Number) read(result, path)).longValue();
    }

    protected static String unique(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }

    /** A Vietnam-time moment as the ISO instant the API takes. */
    protected static String vn(LocalDateTime local) {
        return local.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toString();
    }

    // ---- Fixtures ----------------------------------------------------------------------------------------

    /** A new employee who signs in with {@link #PASSWORD}. */
    protected EmployeeRef newEmployee(String role, String payType, long payRate) throws Exception {
        String username = unique("nv");
        ResultActions created = post("/api/employees", as("admin"), Map.of("fullName", "Nhân viên " + username,
                        "username", username, "role", role, "password", PASSWORD, "payType", payType,
                        "payRate", payRate))
                .andExpect(status().isCreated());
        return new EmployeeRef(readLong(created, "$.id"), username);
    }

    /** A new shift template; times as "HH:mm". */
    protected long newWorkShift(String start, String end) throws Exception {
        return readLong(post("/api/work-shifts", as("quanly"),
                        Map.of("name", unique("Ca"), "startTime", start, "endTime", end))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    protected long assign(long employeeId, long workShiftId, LocalDate day) throws Exception {
        return readLong(post("/api/schedule", as("quanly"),
                        Map.of("employeeId", employeeId, "workShiftId", workShiftId, "workDate", day.toString()))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    protected TableRef newTable() throws Exception {
        ResultActions created = post("/api/tables", as("quanly"),
                Map.of("name", unique("T"), "area", "Test", "seats", 4))
                .andExpect(status().isCreated());
        return new TableRef(readLong(created, "$.id"), read(created, "$.name"), read(created, "$.qrToken"));
    }

    protected long newDish(long price) throws Exception {
        return newDish(unique("Món thử"), price);
    }

    protected long newDish(String name, long price) throws Exception {
        long categoryId = readLong(get("/api/categories", as("quanly")), "$[0].id");
        return readLong(post("/api/menu-items", as("quanly"),
                        Map.of("categoryId", categoryId, "name", name, "price", price))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    protected long openOrder(long tableId) throws Exception {
        return readLong(post("/api/orders", as("phucvu"),
                        Map.of("type", "DINE_IN", "tableId", tableId, "guestCount", 2))
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /** Adds one line and returns the id of the new dish in the order. */
    protected long addDish(long orderId, long dishId, int quantity) throws Exception {
        ResultActions result = post("/api/orders/" + orderId + "/items", as("phucvu"),
                Map.of("items", List.of(Map.of("menuItemId", dishId, "quantity", quantity))))
                .andExpect(status().isOk());
        List<Number> ids = read(result, "$.items[*].id");
        return ids.get(ids.size() - 1).longValue();
    }

    protected ResultActions guestOrders(String qrToken, long dishId, int quantity) throws Exception {
        return post("/api/public/tables/" + qrToken + "/items", null,
                Map.of("items", List.of(Map.of("menuItemId", dishId, "quantity", quantity))));
    }

    /** A SePay webhook body; null values are left out like SePay does for empty fields. */
    protected ResultActions sepay(String apiKey, long txnId, String content, long amount, String transferType)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", txnId);
        body.put("gateway", "Vietcombank");
        body.put("transactionDate", "2026-09-30 12:00:00");
        body.put("accountNumber", "0000000000");
        body.put("content", content);
        body.put("transferType", transferType);
        body.put("transferAmount", amount);
        body.put("accumulated", 0);
        body.put("referenceCode", "FT" + txnId);
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/api/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
        if (apiKey != null) {
            request.header("Authorization", "Apikey " + apiKey);
        }
        return mvc.perform(request);
    }

    protected static long newTxnId() {
        return System.nanoTime();
    }
}
