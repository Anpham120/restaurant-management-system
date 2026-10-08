package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.khoibep.rms.IntegrationTest;

/** US-07 and BR-21. */
class ReportIntegrationTest extends IntegrationTest {

    @Test
    void revenueCountsConfirmedPaymentsAndSkipsCancelledDishes() throws Exception {
        long revenueBefore = readLong(get("/api/reports/summary", as("quanly")), "$.revenue");
        long ordersBefore = readLong(get("/api/reports/summary", as("quanly")), "$.orderCount");

        String sold = unique("Món bán chạy");
        String cancelled = unique("Món bị huỷ");
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(sold, 70_000), 50);
        long cancelledItem = addDish(orderId, newDish(cancelled, 20_000), 50);
        post("/api/order-items/" + cancelledItem + "/cancel", as("phucvu"), Map.of()).andExpect(status().isOk());

        // A pending transfer on another order is not revenue.
        long otherOrder = openOrder(newTable().id());
        addDish(otherOrder, newDish(10_000), 1);
        post("/api/orders/" + otherOrder + "/payments/transfer", as("thungan"), null).andExpect(status().isOk());

        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 3_500_000))
                .andExpect(status().isOk());

        var after = get("/api/reports/summary", as("quanly")).andExpect(status().isOk());
        assertThat(readLong(after, "$.revenue") - revenueBefore).isEqualTo(3_500_000);
        assertThat(readLong(after, "$.orderCount") - ordersBefore).isEqualTo(1);
        List<String> topNames = read(after, "$.topItems[*].itemName");
        assertThat(topNames).contains(sold).doesNotContain(cancelled);
    }

    @Test
    void rangeMustBeValid() throws Exception {
        get("/api/reports/summary?from=2026-09-30&to=2026-09-01", as("quanly")).andExpect(status().isBadRequest());
        get("/api/reports/summary", as("thungan")).andExpect(status().isForbidden());
        get("/api/reports/summary?from=2026-09-01&to=2026-09-30", as("admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"));
    }
}
