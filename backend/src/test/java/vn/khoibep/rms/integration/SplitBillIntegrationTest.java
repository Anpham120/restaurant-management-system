package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P1-07: one bill paid in several parts (FR-08.12, FR-08.13, US-38, BR-13, BR-43). */
class SplitBillIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Test
    void anEvenSplitAddsUpToTheBillAndTheLastPartClosesIt() throws Exception {
        TableRef table = newTable();
        long orderId = openOrder(table.id());
        addDish(orderId, newDish(1_000_001), 1);

        // US-38 AC1: three people, 333.334 + 333.334 + 333.333.
        cash(orderId, 333_334).andExpect(status().isOk());
        bill(orderId).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.paidAmount").value(333_334))
                .andExpect(jsonPath("$.due").value(666_667));
        get("/api/orders/" + orderId + "/payments", as("thungan")).andExpect(status().isNotFound());
        cash(orderId, 333_334).andExpect(status().isOk());
        cash(orderId, 333_333).andExpect(status().isOk());
        bill(orderId).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.due").value(0));
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].openOrderId", table.id()).value(hasItem(org.hamcrest.Matchers.nullValue())));

        // US-38 AC5
        get("/api/orders/" + orderId + "/payments", as("thungan"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].amount").value(333_333));
    }

    @Test
    void cashThenTransfersCloseTheOrderOnlyWhenTheBillIsCovered() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(250_000), 2);

        // US-38 AC2
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("amount", 200_000, "receivedAmount", 500_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change").value(300_000));
        transferAndPay(orderId, Map.of("amount", 100_000), 100_000);
        bill(orderId).andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.due").value(200_000));
        transferAndPay(orderId, Map.of(), 200_000);
        bill(orderId).andExpect(jsonPath("$.status").value("PAID"));
        ResultActions receipt = get("/api/orders/" + orderId + "/payments", as("thungan")).andExpect(status().isOk());
        List<String> methods = read(receipt, "$[*].method");
        assertThat(methods).containsExactly("CASH", "BANK_TRANSFER", "BANK_TRANSFER");
    }

    @Test
    void aPartIsMoreThanZeroAndNoMoreThanTheRestAndTheBillOnlyGrows() throws Exception {
        long orderId = openOrder(newTable().id());
        long dish = newDish(50_000);
        long first = addDish(orderId, dish, 2);

        // US-38 AC4
        cash(orderId, 150_000).andExpect(status().isBadRequest());
        cash(orderId, 0).andExpect(status().isBadRequest());
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), Map.of("amount", 0))
                .andExpect(status().isBadRequest());
        cash(orderId, 40_000).andExpect(status().isOk());
        post("/api/order-items/" + first + "/cancel", as("phucvu"), Map.of()).andExpect(status().isConflict());
        post("/api/orders/" + orderId + "/adjustments", as("thungan"),
                Map.of("type", "DISCOUNT", "amount", 5_000, "reason", "WAIT")).andExpect(status().isConflict());
        addDish(orderId, dish, 1);
        bill(orderId).andExpect(jsonPath("$.due").value(110_000));
    }

    @Test
    void reportsCountASplitBillOnce() throws Exception {
        ResultActions before = summary();
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(300_000), 1);
        cash(orderId, 100_000).andExpect(status().isOk());
        cash(orderId, 200_000).andExpect(status().isOk());

        // US-38 AC5
        ResultActions after = summary();
        assertThat(readLong(after, "$.orderCount") - readLong(before, "$.orderCount")).isEqualTo(1);
        assertThat(readLong(after, "$.revenue") - readLong(before, "$.revenue")).isEqualTo(300_000);
        assertThat(cashPayments(after) - cashPayments(before)).isEqualTo(2);
    }

    private ResultActions cash(long orderId, long amount) throws Exception {
        return post("/api/orders/" + orderId + "/payments/cash", as("thungan"),
                Map.of("amount", amount, "receivedAmount", amount));
    }

    private ResultActions bill(long orderId) throws Exception {
        return get("/api/orders/" + orderId, as("thungan")).andExpect(status().isOk());
    }

    /** Asks for a transfer and has SePay bring exactly that amount in. */
    private void transferAndPay(long orderId, Map<String, Object> body, long amount) throws Exception {
        ResultActions transfer = post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value((int) amount));
        String reference = read(transfer, "$.reference");
        sepay(SEPAY_KEY, newTxnId(), "CK " + reference, amount, "in").andExpect(status().isOk());
    }

    private ResultActions summary() throws Exception {
        return get("/api/reports/summary?from=" + TODAY + "&to=" + TODAY, as("quanly")).andExpect(status().isOk());
    }

    private static long cashPayments(ResultActions summary) throws Exception {
        List<Number> counts = read(summary, "$.byMethod[?(@.method == 'CASH')].count");
        return counts.isEmpty() ? 0 : counts.getFirst().longValue();
    }
}
