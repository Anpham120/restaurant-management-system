package vn.khoibep.rms.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P2-01: shifts of the cash drawer (FR-17, US-34, BR-39). */
class CashShiftIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cashNeedsAnOpenShift() throws Exception {
        closeCurrentShift();
        long orderId = orderOf(100_000);

        // US-34 AC1
        ResultActions refused = payCash(orderId, 100_000).andExpect(status().isConflict());
        assertThat(body(refused)).contains("Chưa mở ca");
        get("/api/cash-shifts/current", as("thungan")).andExpect(status().isNoContent());
        post("/api/cash-shifts", as("thungan"), Map.of("openingFloat", 1_000_000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingFloat").value(1_000_000))
                .andExpect(jsonPath("$.expectedCash").value(1_000_000));
        post("/api/cash-shifts", as("thungan"), Map.of("openingFloat", 500_000)).andExpect(status().isConflict());
        payCash(orderId, 100_000).andExpect(status().isOk());
    }

    @Test
    void expectedCashFollowsCashTakenAndPaidOut() throws Exception {
        freshShift(1_000_000);

        // US-34 AC2: the guest hands over 500.000 for 350.000; the drawer keeps 350.000.
        payCash(orderOf(350_000), 500_000).andExpect(status().isOk());
        current().andExpect(jsonPath("$.cashTaken").value(350_000))
                .andExpect(jsonPath("$.cashPayments").value(1))
                .andExpect(jsonPath("$.expectedCash").value(1_350_000));
        long transferred = orderOf(200_000);
        long paymentId = readLong(post("/api/orders/" + transferred + "/payments/transfer", as("thungan"), null)
                .andExpect(status().isOk()), "$.paymentId");
        post("/api/payments/" + paymentId + "/confirm", as("thungan"), null).andExpect(status().isOk());
        current().andExpect(jsonPath("$.expectedCash").value(1_350_000));

        // US-34 AC3
        post("/api/cash-shifts/current/expenses", as("thungan"), Map.of("amount", 120_000, "reason", "Mua đá"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseTotal").value(120_000))
                .andExpect(jsonPath("$.expectedCash").value(1_230_000))
                .andExpect(jsonPath("$.expenses[0].reason").value("Mua đá"))
                .andExpect(jsonPath("$.expenses[0].createdByName").value(fullName("thungan")));
        expense("thungan", 300_001).andExpect(status().isForbidden());
        expense("quanly", 300_001).andExpect(status().isOk());
        ResultActions tooMuch = expense("quanly", 1_000_000).andExpect(status().isConflict());
        assertThat(body(tooMuch)).contains("929.999 đ");
        post("/api/cash-shifts/current/expenses", as("thungan"), Map.of("amount", 0, "reason", "Thử"))
                .andExpect(status().isBadRequest());
        post("/api/cash-shifts/current/expenses", as("thungan"), Map.of("amount", 10_000, "reason", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closingRecordsTheCountAndADifferenceNeedsAReason() throws Exception {
        long shiftId = freshShift(500_000);
        payCash(orderOf(200_000), 200_000).andExpect(status().isOk());

        // US-34 AC4
        close(690_000, null).andExpect(status().isBadRequest());
        close(690_000, "Thối nhầm cho khách bàn 5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(700_000))
                .andExpect(jsonPath("$.countedCash").value(690_000))
                .andExpect(jsonPath("$.difference").value(-10_000))
                .andExpect(jsonPath("$.closedByName").value(fullName("thungan")));
        get("/api/cash-shifts/current", as("thungan")).andExpect(status().isNoContent());
        expense("thungan", 10_000).andExpect(status().isConflict());
        close(0, null).andExpect(status().isConflict());
        payCash(orderOf(50_000), 50_000).andExpect(status().isConflict());

        // US-34 AC5
        String shifts = "/api/cash-shifts?from=" + TODAY + "&to=" + TODAY;
        get(shifts, as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].difference", shiftId).value(hasItem(-10_000)))
                .andExpect(jsonPath("$[?(@.id == %d)].closeNote", shiftId).value(hasItem("Thối nhầm cho khách bàn 5")))
                .andExpect(jsonPath("$[?(@.id == %d)].cashTaken", shiftId).value(hasItem(200_000)));
        get("/api/cash-shifts?from=2026-01-01&to=2026-06-30", as("quanly")).andExpect(status().isBadRequest());

        // A count that matches needs no reason.
        freshShift(300_000);
        close(300_000, null).andExpect(status().isOk()).andExpect(jsonPath("$.difference").value(0));
    }

    @Test
    void theDatabaseKeepsOneOpenShiftAndAReasonForEveryDifference() {
        // BR-39, also against writes that skip the application.
        Long anyone = jdbc.queryForObject("SELECT id FROM employee WHERE username = 'thungan'", Long.class);
        assertThat(catchThrowable(() -> jdbc.update(
                "INSERT INTO cash_shift (opened_by, opened_at, opening_float) VALUES (?, now(), 0)", anyone)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(catchThrowable(() -> jdbc.update(
                "UPDATE cash_shift SET closed_by = ?, closed_at = now(), expected_cash = 100, counted_cash = 90 "
                        + "WHERE closed_at IS NULL", anyone)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyCashiersAndManagersHandleTheDrawer() throws Exception {
        // US-34 AC6
        for (String user : List.of("phucvu", "bep")) {
            get("/api/cash-shifts/current", as(user)).andExpect(status().isForbidden());
            post("/api/cash-shifts/current/expenses", as(user), Map.of("amount", 1_000, "reason", "Thử"))
                    .andExpect(status().isForbidden());
            post("/api/cash-shifts/current/close", as(user), Map.of("countedCash", 0)).andExpect(status().isForbidden());
        }
        String shifts = "/api/cash-shifts?from=" + TODAY + "&to=" + TODAY;
        get(shifts, as("thungan")).andExpect(status().isForbidden());
        get(shifts, as("admin")).andExpect(status().isOk());
    }

    /** Closes whatever shift is open, with the count it expects, and opens one with this float. */
    private long freshShift(long openingFloat) throws Exception {
        closeCurrentShift();
        return readLong(post("/api/cash-shifts", as("thungan"), Map.of("openingFloat", openingFloat))
                .andExpect(status().isCreated()), "$.id");
    }

    private void closeCurrentShift() throws Exception {
        ResultActions current = current();
        if (current.andReturn().getResponse().getStatus() == 200) {
            close(readLong(current, "$.expectedCash"), null).andExpect(status().isOk());
        }
    }

    private ResultActions current() throws Exception {
        return get("/api/cash-shifts/current", as("thungan"));
    }

    private ResultActions close(long counted, String note) throws Exception {
        Map<String, Object> body = note == null ? Map.of("countedCash", counted)
                : Map.of("countedCash", counted, "note", note);
        return post("/api/cash-shifts/current/close", as("thungan"), body);
    }

    private ResultActions expense(String user, long amount) throws Exception {
        return post("/api/cash-shifts/current/expenses", as(user), Map.of("amount", amount, "reason", "Trả tiền rau"));
    }

    private ResultActions payCash(long orderId, long received) throws Exception {
        return post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", received));
    }

    private String fullName(String username) throws Exception {
        return read(get("/api/auth/me", as(username)), "$.fullName");
    }

    /** An open order at a new table with one dish at this price. */
    private long orderOf(long total) throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(total), 1);
        return orderId;
    }
}
