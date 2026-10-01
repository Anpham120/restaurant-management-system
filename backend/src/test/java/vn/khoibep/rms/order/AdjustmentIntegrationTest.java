package vn.khoibep.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;
import vn.khoibep.rms.common.realtime.RealtimeEvent;

/** P1-04: discounts and dishes given free, approved by a manager past the limit (FR-08.10, FR-08.11, US-30, BR-35). */
@RecordApplicationEvents
class AdjustmentIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Test
    void aDiscountWithinTheLimitTakesEffectAtOnce() throws Exception {
        long orderId = billOf(300_000, 2);

        // US-30 AC1: 50.000 off 600.000 is within 10% and 150.000.
        discount(orderId, "thungan", 50_000, "WAIT", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(600_000))
                .andExpect(jsonPath("$.discountTotal").value(50_000))
                .andExpect(jsonPath("$.total").value(550_000))
                .andExpect(jsonPath("$.adjustments[0].status").value("APPLIED"));
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 550_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(550_000));

        // US-30 AC6: logged by the cashier who gave it.
        assertThat(auditLines(orderId)).singleElement().satisfies(e -> assertThat(e)
                .containsEntry("subject", "Giảm giá bill")
                .containsEntry("amount", 50_000)
                .containsEntry("reason", "Chờ lâu")
                .containsEntry("employeeName", fullName("thungan")));
    }

    @Test
    void pastTheLimitADiscountWaitsForAManagerAndBlocksPayment() throws Exception {
        long orderId = billOf(300_000, 2);

        // US-30 AC2: 61.000 is past 10% of 600.000.
        long id = readLong(discount(orderId, "thungan", 61_000, "FOOD_QUALITY", null)
                .andExpect(jsonPath("$.total").value(600_000))
                .andExpect(jsonPath("$.pendingAdjustmentCount").value(1)), "$.adjustments[0].id");
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 600_000))
                .andExpect(status().isConflict());
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null).andExpect(status().isConflict());

        // US-30 AC3: the manager sees it, with the table and who asked, and a realtime notice went out.
        get("/api/adjustments?status=PENDING", as("quanly"))
                .andExpect(jsonPath("$[?(@.id == %d)].createdByName", id).value(hasItem(fullName("thungan"))));
        assertThat(events.stream(RealtimeEvent.class).filter(e -> RealtimeEvent.ADJUSTMENTS_CHANGED.equals(e.type())))
                .isNotEmpty();
        post("/api/adjustments/" + id + "/approve", as("phucvu"), null).andExpect(status().isForbidden());
        post("/api/adjustments/" + id + "/approve", as("thungan"), null).andExpect(status().isForbidden());
        post("/api/adjustments/" + id + "/approve", as("quanly"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.decidedByName").value(fullName("quanly")));
        get("/api/orders/" + orderId, as("thungan"))
                .andExpect(jsonPath("$.total").value(539_000))
                .andExpect(jsonPath("$.pendingAdjustmentCount").value(0));
        // US-30 AC6: logged by the manager who approved it.
        assertThat(auditLines(orderId)).singleElement()
                .satisfies(e -> assertThat(e).containsEntry("employeeName", fullName("quanly")));
    }

    @Test
    void aRejectedDiscountLeavesTheTotalAndThePaymentFree() throws Exception {
        long orderId = billOf(1_000_000, 2);

        // 160.000 is within 10% of 2.000.000 but past 150.000.
        long id = readLong(discount(orderId, "thungan", 160_000, "PROMOTION", null)
                .andExpect(jsonPath("$.adjustments[0].status").value("PENDING")), "$.adjustments[0].id");
        post("/api/adjustments/" + id + "/reject", as("quanly"), null)
                .andExpect(jsonPath("$.status").value("REJECTED"));
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.total").value(2_000_000));
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 2_000_000))
                .andExpect(status().isOk());
        assertThat(auditLines(orderId)).isEmpty();
    }

    @Test
    void aManagerIsNotHeldByTheLimit() throws Exception {
        long orderId = billOf(300_000, 2);
        discount(orderId, "quanly", 200_000, "STAFF_ERROR", null)
                .andExpect(jsonPath("$.adjustments[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.total").value(400_000));
        // Never more than the dishes are worth.
        discount(orderId, "quanly", 400_001, "STAFF_ERROR", null).andExpect(status().isBadRequest());
    }

    @Test
    void aDishGivenFreeTakesItsLineOffOnceAndGoesWithTheDish() throws Exception {
        long dish = newDish(45_000);
        long orderId = openOrder(newTable().id());
        long given = addDish(orderId, dish, 2);
        addDish(orderId, newDish(1_000_000), 1);

        // US-30 AC4: the line is 90.000, within 10% of 1.090.000 and within 150.000.
        comp(orderId, "thungan", given)
                .andExpect(jsonPath("$.adjustments[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.adjustments[0].itemName").value(endsWith(" x2")))
                .andExpect(jsonPath("$.total").value(1_000_000));
        comp(orderId, "thungan", given).andExpect(status().isConflict());
        post("/api/order-items/" + given + "/cancel", as("phucvu"), null).andExpect(status().isOk());
        get("/api/orders/" + orderId, as("thungan"))
                .andExpect(jsonPath("$.adjustments[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.discountTotal").value(0))
                .andExpect(jsonPath("$.total").value(1_000_000));
    }

    @Test
    void aDiscountNeedsAReasonAndACashierOrManager() throws Exception {
        long orderId = billOf(300_000, 1);

        // US-30 AC5
        discount(orderId, "thungan", 10_000, "OTHER", null).andExpect(status().isBadRequest());
        discount(orderId, "thungan", 10_000, "OTHER", "  ").andExpect(status().isBadRequest());
        discount(orderId, "thungan", 10_000, null, null).andExpect(status().isBadRequest());
        discount(orderId, "phucvu", 10_000, "WAIT", null).andExpect(status().isForbidden());
        discount(orderId, "thungan", 10_000, "OTHER", "Khách quen").andExpect(status().isCreated());
    }

    @Test
    void takingADiscountBackRaisesTheTotalAndVoidsTheTransferCode() throws Exception {
        long orderId = billOf(300_000, 2);
        long id = readLong(discount(orderId, "thungan", 30_000, "WAIT", null), "$.adjustments[0].id");
        String code = read(post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(jsonPath("$.amount").value(570_000)), "$.reference");

        post("/api/adjustments/" + id + "/cancel", as("thungan"), null)
                .andExpect(jsonPath("$.total").value(600_000));
        // BR-14: the old code was for 570.000.
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(jsonPath("$.amount").value(600_000))
                .andExpect(jsonPath("$.reference").value(not(code)));
        post("/api/adjustments/" + id + "/cancel", as("thungan"), null).andExpect(status().isConflict());
    }

    /** An open order with {@code quantity} of one new dish at {@code price}. */
    private long billOf(long price, int quantity) throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(price), quantity);
        return orderId;
    }

    private ResultActions discount(long orderId, String user, long amount, String reason, String note) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("type", "DISCOUNT", "amount", amount));
        body.put("reason", reason);
        body.put("note", note);
        return post("/api/orders/" + orderId + "/adjustments", as(user), body);
    }

    private ResultActions comp(long orderId, String user, long itemId) throws Exception {
        return post("/api/orders/" + orderId + "/adjustments", as(user),
                Map.of("type", "COMP", "orderItemId", itemId, "reason", "FOOD_QUALITY"));
    }

    private List<Map<String, Object>> auditLines(long orderId) throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        List<Map<String, Object>> all = read(get("/api/audit-entries?from=" + today + "&to=" + today, as("quanly")), "$");
        return all.stream().filter(e -> "DISCOUNT_GIVEN".equals(e.get("action"))
                && e.get("orderId") != null && ((Number) e.get("orderId")).longValue() == orderId).toList();
    }

    private String fullName(String username) throws Exception {
        return read(get("/api/auth/me", as(username)), "$.fullName");
    }
}
