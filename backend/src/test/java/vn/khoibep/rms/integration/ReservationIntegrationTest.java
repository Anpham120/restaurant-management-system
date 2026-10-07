package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P4-01: bookings, deposits by VietQR, and the deposit taken off the bill (FR-18, US-37, BR-42). */
class ReservationIntegrationTest extends IntegrationTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TOMORROW = LocalDate.now(VN).plusDays(1);
    private static final String TODAY = LocalDate.now(VN).toString();

    @Test
    void aBookingHasACodeAndShowsOnItsDay() throws Exception {
        TableRef table = newTable();

        // US-37 AC1
        ResultActions created = book(table.id(), 500_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.depositPaidAt").value(nullValue()))
                .andExpect(jsonPath("$.tableName").value(table.name()));
        long id = readLong(created, "$.id");
        assertThat(code(created)).matches("KB[A-Z0-9]{8}");
        day(TOMORROW).andExpect(jsonPath("$.reservations[?(@.id == %d)].guestCount", id).value(hasItem(6)));

        Map<String, Object> changed = booking(table.id(), 500_000);
        changed.put("guestCount", 8);
        put("/api/reservations/" + id, as("phucvu"), changed).andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(8));

        Map<String, Object> none = booking(table.id(), 0);
        none.put("guestCount", 0);
        post("/api/reservations", as("phucvu"), none).andExpect(status().isBadRequest());
        none.put("guestCount", 2);
        none.remove("phone");
        post("/api/reservations", as("phucvu"), none).andExpect(status().isBadRequest());
    }

    @Test
    void theConfirmationIsKeptAsItWasSent() throws Exception {
        ResultActions created = book(null, 500_000);
        long id = readLong(created, "$.id");
        String code = code(created);
        String when = TOMORROW.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // US-37 AC2
        get("/api/reservations/" + id + "/confirmation", as("phucvu"))
                .andExpect(jsonPath("$.text").value(containsString(code)))
                .andExpect(jsonPath("$.text").value(containsString("19:00 ngày " + when + ", 6 khách")))
                .andExpect(jsonPath("$.text").value(containsString("500.000 đ")))
                .andExpect(jsonPath("$.sentAt").value(nullValue()));
        post("/api/reservations/" + id + "/confirmation", as("phucvu"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(containsString("nội dung " + code)))
                .andExpect(jsonPath("$.sentAt").value(notNullValue()));
        day(TOMORROW).andExpect(jsonPath("$.reservations[?(@.id == %d)].confirmationSentAt", id)
                .value(hasItem(notNullValue())));
    }

    @Test
    void theWebhookTakesAnExactDepositAndAManagerMayConfirmByHand() throws Exception {
        ResultActions created = book(null, 500_000);
        long id = readLong(created, "$.id");
        String code = code(created);

        // US-37 AC3
        post("/api/reservations/" + id + "/deposit", as("phucvu"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(code))
                .andExpect(jsonPath("$.amount").value(500_000))
                .andExpect(jsonPath("$.qrImageUrl").value(containsString("amount=500000")));
        sepay(SEPAY_KEY, newTxnId(), "COC " + code, 400_000, "in").andExpect(status().isOk());
        day(TOMORROW).andExpect(jsonPath("$.reservations[?(@.id == %d)].depositPaidAt", id).value(hasItem(nullValue())));
        get("/api/bank-transactions?status=UNMATCHED", as("thungan"))
                .andExpect(jsonPath("$[*].note").value(hasItem("Sai số tiền cọc: cần 500000, nhận 400000")));
        sepay(SEPAY_KEY, newTxnId(), "COC " + code, 500_000, "in").andExpect(status().isOk());
        day(TOMORROW).andExpect(jsonPath("$.reservations[?(@.id == %d)].depositConfirmation", id).value(hasItem("AUTO")));
        post("/api/reservations/" + id + "/deposit", as("phucvu"), null).andExpect(status().isConflict());

        long other = readLong(book(null, 300_000), "$.id");
        post("/api/reservations/" + other + "/deposit/confirm", as("phucvu"), null).andExpect(status().isForbidden());
        post("/api/reservations/" + other + "/deposit/confirm", as("quanly"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.depositConfirmation").value("MANUAL"));
        get("/api/audit-entries?from=" + TODAY + "&to=" + TODAY, as("quanly"))
                .andExpect(jsonPath("$[?(@.action == 'MANUAL_CONFIRMATION')].amount").value(hasItem(300_000)));
    }

    @Test
    void theDepositComesOffTheBillAndCountsAsRevenueWhenPaid() throws Exception {
        TableRef table = newTable();
        ResultActions created = book(table.id(), 500_000);
        long id = readLong(created, "$.id");
        post("/api/reservations/" + id + "/deposit/confirm", as("quanly"), null).andExpect(status().isOk());

        // US-37 AC4
        long orderId = readLong(post("/api/reservations/" + id + "/seat", as("phucvu"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(table.id()))
                .andExpect(jsonPath("$.guestCount").value(6))
                .andExpect(jsonPath("$.note").value("Đặt bàn " + code(created))), "$.id");
        addDish(orderId, newDish(900_000), 2);
        get("/api/orders/" + orderId, as("thungan"))
                .andExpect(jsonPath("$.depositCredit").value(500_000))
                .andExpect(jsonPath("$.total").value(1_300_000));

        ResultActions before = summary();
        long cashBefore = readLong(get("/api/cash-shifts/current", as("thungan")), "$.expectedCash");
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 1_300_000))
                .andExpect(status().isOk());
        ResultActions after = summary();
        assertThat(readLong(after, "$.revenue") - readLong(before, "$.revenue")).isEqualTo(1_800_000);
        assertThat(deposits(after) - deposits(before)).isEqualTo(500_000);
        // BR-39: the drawer took the cash only.
        assertThat(readLong(get("/api/cash-shifts/current", as("thungan")), "$.expectedCash") - cashBefore)
                .isEqualTo(1_300_000);
        day(TOMORROW)
                .andExpect(jsonPath("$.reservations[?(@.id == %d)].status", id).value(hasItem("SEATED")))
                .andExpect(jsonPath("$.reservations[?(@.id == %d)].depositApplied", id).value(hasItem(500_000)))
                .andExpect(jsonPath("$.reservations[?(@.id == %d)].orderId", id).value(hasItem((int) orderId)));
    }

    @Test
    void aDepositBiggerThanTheBillCoversItAll() throws Exception {
        long id = readLong(book(null, 500_000), "$.id");
        post("/api/reservations/" + id + "/deposit/confirm", as("quanly"), null).andExpect(status().isOk());
        long orderId = readLong(post("/api/reservations/" + id + "/seat", as("phucvu"),
                Map.of("tableId", newTable().id())).andExpect(status().isOk()), "$.id");
        addDish(orderId, newDish(300_000), 1);

        // US-37 AC5
        get("/api/orders/" + orderId, as("thungan"))
                .andExpect(jsonPath("$.depositCredit").value(300_000))
                .andExpect(jsonPath("$.total").value(0));
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 0))
                .andExpect(status().isOk());
        day(TOMORROW).andExpect(jsonPath("$.reservations[?(@.id == %d)].depositApplied", id).value(hasItem(300_000)));
    }

    @Test
    void onlyABookingStillWaitingChanges() throws Exception {
        TableRef busy = newTable();
        openOrder(busy.id());
        long noTable = readLong(book(null, 0), "$.id");
        post("/api/reservations/" + noTable + "/seat", as("phucvu"), null).andExpect(status().isBadRequest());
        post("/api/reservations/" + noTable + "/seat", as("phucvu"), Map.of("tableId", busy.id()))
                .andExpect(status().isConflict());
        post("/api/reservations/" + noTable + "/deposit", as("phucvu"), null).andExpect(status().isConflict());

        // US-37 AC5
        long id = readLong(book(null, 200_000), "$.id");
        post("/api/reservations/" + id + "/cancel", as("phucvu"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        put("/api/reservations/" + id, as("phucvu"), booking(null, 200_000)).andExpect(status().isConflict());
        post("/api/reservations/" + id + "/seat", as("phucvu"), Map.of("tableId", newTable().id()))
                .andExpect(status().isConflict());
        post("/api/reservations/" + id + "/no-show", as("phucvu"), null).andExpect(status().isConflict());
        long missed = readLong(book(null, 0), "$.id");
        post("/api/reservations/" + missed + "/no-show", as("phucvu"), null).andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void onlyWaitersAndManagersBook() throws Exception {
        // US-37 AC6
        for (String user : List.of("bep", "thungan")) {
            get("/api/reservations?date=" + TOMORROW, as(user)).andExpect(status().isForbidden());
            post("/api/reservations", as(user), booking(null, 0)).andExpect(status().isForbidden());
        }
        get("/api/reservations?date=" + TOMORROW, as("admin")).andExpect(status().isOk());
    }

    /** Six guests tomorrow at 19:00, Vietnam time. */
    private Map<String, Object> booking(Long tableId, long deposit) {
        Map<String, Object> body = new HashMap<>(Map.of("guestName", unique("Khách"), "phone", "0912345678",
                "reservedAt", vn(TOMORROW.atTime(19, 0)), "guestCount", 6, "depositAmount", deposit));
        body.put("tableId", tableId);
        return body;
    }

    private ResultActions book(Long tableId, long deposit) throws Exception {
        return post("/api/reservations", as("phucvu"), booking(tableId, deposit)).andExpect(status().isCreated());
    }

    private ResultActions day(LocalDate date) throws Exception {
        return get("/api/reservations?date=" + date, as("phucvu")).andExpect(status().isOk());
    }

    private ResultActions summary() throws Exception {
        return get("/api/reports/summary?from=" + TODAY + "&to=" + TODAY, as("quanly")).andExpect(status().isOk());
    }

    private static long deposits(ResultActions summary) throws Exception {
        List<Number> amounts = read(summary, "$.byMethod[?(@.method == 'DEPOSIT')].amount");
        return amounts.isEmpty() ? 0 : amounts.getFirst().longValue();
    }

    private static String code(ResultActions created) throws Exception {
        return read(created, "$.code");
    }
}
