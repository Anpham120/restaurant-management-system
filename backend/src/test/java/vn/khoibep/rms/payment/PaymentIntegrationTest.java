package vn.khoibep.rms.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** US-16, US-17, US-18, US-19, US-28 and BR-12 to BR-17, BR-33. */
class PaymentIntegrationTest extends IntegrationTest {

    @Test
    void cashMustCoverTheTotalAndClosesTheTable() throws Exception {
        TableRef table = newTable();
        long orderId = openOrder(table.id());
        addDish(orderId, newDish(45_000), 2);

        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 50_000))
                .andExpect(status().isBadRequest());
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 100_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(90_000))
                .andExpect(jsonPath("$.change").value(10_000))
                .andExpect(jsonPath("$.status").value("PAID"));

        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("PAID"));
        get("/api/tables", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].status", table.id()).value(hasItem("AVAILABLE")));
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 100_000))
                .andExpect(status().isConflict());
    }

    /** US-28 AC2, AC3, BR-33: a receipt only for a paid order, and only for cashiers. */
    @Test
    void receiptOnlyOnceTheOrderIsPaid() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(30_000), 2);
        String receipt = "/api/orders/" + orderId + "/payment";
        get(receipt, as("thungan")).andExpect(status().isNotFound());

        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 100_000))
                .andExpect(status().isOk());
        get(receipt, as("thungan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.amount").value(60_000))
                .andExpect(jsonPath("$.receivedAmount").value(100_000))
                .andExpect(jsonPath("$.change").value(40_000));
        get(receipt, as("phucvu")).andExpect(status().isForbidden());
    }

    @Test
    void waiterCannotTakePayments() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(10_000), 1);
        post("/api/orders/" + orderId + "/payments/cash", as("phucvu"), Map.of("receivedAmount", 10_000))
                .andExpect(status().isForbidden());
    }

    @Test
    void noPaymentWhileGuestDishesAwaitConfirmation() throws Exception {
        TableRef table = newTable();
        long orderId = readLong(guestOrders(table.qrToken(), newDish(20_000), 1), "$.order.orderId");
        ResultActions result = post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(status().isConflict());
        assertThat(body(result)).contains("Còn món chờ xác nhận");
    }

    @Test
    void transferIsConfirmedAutomaticallyByTheWebhook() throws Exception {
        TableRef table = newTable();
        long orderId = openOrder(table.id());
        addDish(orderId, newDish(60_000), 2);

        ResultActions transfer = post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(120_000));
        String reference = read(transfer, "$.reference");
        String qrUrl = read(transfer, "$.qrImageUrl");
        assertThat(reference).matches("KB[A-Z0-9]{8}");
        assertThat(qrUrl).startsWith("https://img.vietqr.io/image/").contains("amount=120000", reference);

        // Asking again while the bill is unchanged returns the same code.
        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(jsonPath("$.reference").value(reference));

        long txnId = newTxnId();
        String content = "NGUYEN VAN A chuyen tien " + reference.substring(0, 3) + " " + reference.substring(3);
        sepay("sai-khoa", txnId, content, 120_000, "in").andExpect(status().isUnauthorized());
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("OPEN"));

        sepay(SEPAY_KEY, txnId, content, 120_000, "in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("PAID"));
        // US-28 AC2: the receipt of a transfer shows its code.
        get("/api/orders/" + orderId + "/payment", as("thungan"))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.confirmation").value("AUTO"));

        // SePay retries: the same transaction is recorded once.
        sepay(SEPAY_KEY, txnId, content, 120_000, "in").andExpect(jsonPath("$.success").value(true));
        get("/api/bank-transactions?status=MATCHED", as("thungan"))
                .andExpect(jsonPath("$[?(@.providerTxnId == '%s')]", String.valueOf(txnId)).value(hasSize(1)));
    }

    @Test
    void wrongAmountIsLeftForTheCashierToConfirm() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(80_000), 1);
        ResultActions transfer = post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null);
        String reference = read(transfer, "$.reference");
        long paymentId = readLong(transfer, "$.paymentId");

        long txnId = newTxnId();
        sepay(SEPAY_KEY, txnId, reference, 79_000, "in").andExpect(jsonPath("$.success").value(true));
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("OPEN"));
        ResultActions unmatched = get("/api/bank-transactions", as("thungan"))
                .andExpect(jsonPath("$[?(@.providerTxnId == '%s')].matchStatus", String.valueOf(txnId))
                        .value(hasItem("UNMATCHED")));
        assertThat(body(unmatched)).contains("Sai số tiền");

        post("/api/payments/" + paymentId + "/confirm", as("thungan"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation").value("MANUAL"));
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void addingDishesVoidsTheOldTransferCode() throws Exception {
        long orderId = openOrder(newTable().id());
        long dish = newDish(50_000);
        addDish(orderId, dish, 1);
        String oldReference = read(post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null),
                "$.reference");

        addDish(orderId, dish, 1);
        sepay(SEPAY_KEY, newTxnId(), oldReference, 50_000, "in").andExpect(jsonPath("$.success").value(true));
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("OPEN"));

        post("/api/orders/" + orderId + "/payments/transfer", as("thungan"), null)
                .andExpect(jsonPath("$.amount").value(100_000))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.not(oldReference)));
    }

    @Test
    void guestPaysFromThePhoneAndTheTableIsFreed() throws Exception {
        TableRef table = newTable();
        long orderId = readLong(guestOrders(table.qrToken(), newDish(35_000), 2), "$.order.orderId");
        post("/api/orders/" + orderId + "/confirm-pending", as("phucvu"), null).andExpect(status().isOk());

        ResultActions payment = post("/api/public/tables/" + table.qrToken() + "/payment", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(70_000));
        String reference = read(payment, "$.reference");

        sepay(SEPAY_KEY, newTxnId(), "TT " + reference, 70_000, "in").andExpect(jsonPath("$.success").value(true));
        get("/api/public/tables/" + table.qrToken(), null).andExpect(jsonPath("$.order").doesNotExist());
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void outgoingTransfersAreIgnored() throws Exception {
        long txnId = newTxnId();
        sepay(SEPAY_KEY, txnId, "tra tien nha cung cap", 500_000, "out").andExpect(jsonPath("$.success").value(true));
        get("/api/bank-transactions?status=IGNORED", as("thungan"))
                .andExpect(jsonPath("$[?(@.providerTxnId == '%s')]", String.valueOf(txnId)).value(hasSize(1)));
    }
}
