package vn.khoibep.rms.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** P4-02: guests known by their phone number, their visits, and whether they may be sent messages (FR-19, US-39, BR-44). */
class CustomerIntegrationTest extends IntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);

    @Test
    void aGuestIsFoundWhicheverWayTheNumberIsWritten() throws Exception {
        String phone = newPhone();
        String name = unique("Chị Lan");
        long orderId = openOrder(newTable().id());

        // US-39 AC1
        long id = readLong(attach(orderId, spaced(phone), name).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.visits").value(0))
                .andExpect(jsonPath("$.mayContact").value(false)), "$.id");
        get("/api/orders/" + orderId, as("thungan"))
                .andExpect(jsonPath("$.customerId").value(id))
                .andExpect(jsonPath("$.customerName").value(name))
                .andExpect(jsonPath("$.customerPhone").value(phone));

        long other = openOrder(newTable().id());
        attach(other, "+84" + phone.substring(1), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
        attach(other, "84" + phone.substring(1), "Tên khác").andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));

        // FR-19.1
        for (String q : List.of(phone.substring(4), "+84 " + phone.substring(1, 6), name.toUpperCase())) {
            get("/api/customers?q=" + q, as("quanly")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(hasItem((int) id)));
        }
        get("/api/customers", as("quanly")).andExpect(jsonPath("$[*].id").value(hasItem((int) id)));
        put("/api/customers/" + id, as("quanly"), Map.of("name", "Lan Nguyễn", "note", "Thích bàn gần cửa sổ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lan Nguyễn"))
                .andExpect(jsonPath("$.note").value("Thích bàn gần cửa sổ"));
    }

    @Test
    void aPaidVisitCountsWithTheDepositTakenOffAndTheBookingShows() throws Exception {
        String phone = newPhone();
        Map<String, Object> booking = new HashMap<>(Map.of("guestName", unique("Anh Minh"), "phone",
                "+84 " + phone.substring(1), "reservedAt", vn(TOMORROW.atTime(19, 0)), "guestCount", 2,
                "depositAmount", 200_000));
        ResultActions booked = post("/api/reservations", as("phucvu"), booking).andExpect(status().isCreated());
        long reservationId = readLong(booked, "$.id");
        post("/api/reservations/" + reservationId + "/deposit/confirm", as("quanly"), null).andExpect(status().isOk());
        long orderId = readLong(post("/api/reservations/" + reservationId + "/seat", as("phucvu"),
                Map.of("tableId", newTable().id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPhone").value(phone)), "$.id");
        long id = readLong(get("/api/orders/" + orderId, as("thungan")), "$.customerId");
        addDish(orderId, newDish(500_000), 1);

        // US-39 AC2: an open order is no visit yet.
        get("/api/customers/" + id, as("quanly")).andExpect(jsonPath("$.customer.visits").value(0))
                .andExpect(jsonPath("$.visits").isEmpty());
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 300_000))
                .andExpect(status().isOk());
        attach(openOrder(newTable().id()), phone, null).andExpect(status().isOk());

        get("/api/customers/" + id, as("quanly")).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.visits").value(1))
                .andExpect(jsonPath("$.customer.spent").value(500_000))
                .andExpect(jsonPath("$.customer.lastVisitAt").value(notNullValue()))
                .andExpect(jsonPath("$.visits[0].orderId").value(orderId))
                .andExpect(jsonPath("$.visits[0].paid").value(500_000))
                .andExpect(jsonPath("$.bookings[0].id").value(reservationId))
                .andExpect(jsonPath("$.bookings[0].code").value((String) read(booked, "$.code")))
                .andExpect(jsonPath("$.bookings[0].status").value("SEATED"));
        get("/api/customers?q=" + phone, as("quanly"))
                .andExpect(jsonPath("$[0].visits").value(1))
                .andExpect(jsonPath("$[0].spent").value(500_000));
    }

    @Test
    void aRefusalStopsMessagesUntilTheGuestAgreesAgain() throws Exception {
        long id = readLong(attach(openOrder(newTable().id()), newPhone(), null), "$.id");

        // US-39 AC3
        post("/api/customers/" + id + "/consent", as("thungan"), Map.of("channel", "ZALO", "source", "Hỏi tại quầy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentChannel").value("ZALO"))
                .andExpect(jsonPath("$.consentSource").value("Hỏi tại quầy"))
                .andExpect(jsonPath("$.consentAt").value(notNullValue()))
                .andExpect(jsonPath("$.mayContact").value(true));
        // The server clock set back between the two: the refusal still counts.
        clock.set(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusHours(1));
        post("/api/customers/" + id + "/opt-out", as("thungan"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.optedOutAt").value(notNullValue()))
                .andExpect(jsonPath("$.consentChannel").value("ZALO"))
                .andExpect(jsonPath("$.mayContact").value(false));
        clock.reset();
        post("/api/customers/" + id + "/consent", as("quanly"), Map.of("channel", "SMS", "source", "Phiếu giấy"))
                .andExpect(jsonPath("$.consentChannel").value("SMS"))
                .andExpect(jsonPath("$.optedOutAt").value(nullValue()))
                .andExpect(jsonPath("$.mayContact").value(true));

        post("/api/customers/" + id + "/consent", as("thungan"), Map.of("source", "Hỏi tại quầy"))
                .andExpect(status().isBadRequest());
        post("/api/customers/" + id + "/consent", as("thungan"), Map.of("channel", "ZALO", "source", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNumberMustHaveTenDigitsFromZero() throws Exception {
        long orderId = openOrder(newTable().id());

        // US-39 AC4
        for (String phone : List.of("091234567", "09123456789", "1912345678", "+8491234567")) {
            attach(orderId, phone, null).andExpect(status().isBadRequest());
        }
        attach(orderId, " ", null).andExpect(status().isBadRequest());
        get("/api/orders/" + orderId, as("thungan")).andExpect(jsonPath("$.customerId").value(nullValue()));

        // A booking keeps the number as written and has no guest.
        Map<String, Object> booking = new HashMap<>(Map.of("guestName", unique("Khách"), "phone", "123",
                "reservedAt", vn(TOMORROW.atTime(12, 0)), "guestCount", 2, "depositAmount", 0));
        long reservationId = readLong(post("/api/reservations", as("phucvu"), booking)
                .andExpect(status().isCreated()), "$.id");
        post("/api/reservations/" + reservationId + "/seat", as("phucvu"), Map.of("tableId", newTable().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(nullValue()));
    }

    @Test
    void aClosedOrderTakesNoGuest() throws Exception {
        long orderId = openOrder(newTable().id());
        addDish(orderId, newDish(100_000), 1);
        post("/api/orders/" + orderId + "/payments/cash", as("thungan"), Map.of("receivedAmount", 100_000))
                .andExpect(status().isOk());
        attach(orderId, newPhone(), null).andExpect(status().isConflict());
        attach(Long.MAX_VALUE, newPhone(), null).andExpect(status().isNotFound());
    }

    @Test
    void onlyCashiersAndManagersSeeGuests() throws Exception {
        long orderId = openOrder(newTable().id());
        long id = readLong(attach(orderId, newPhone(), null), "$.id");

        // US-39 AC5
        for (String user : List.of("phucvu", "bep", "thungan")) {
            get("/api/customers", as(user)).andExpect(status().isForbidden());
            get("/api/customers/" + id, as(user)).andExpect(status().isForbidden());
            put("/api/customers/" + id, as(user), Map.of("name", "X")).andExpect(status().isForbidden());
        }
        for (String user : List.of("phucvu", "bep")) {
            post("/api/orders/" + orderId + "/customer", as(user), Map.of("phone", newPhone()))
                    .andExpect(status().isForbidden());
            post("/api/customers/" + id + "/opt-out", as(user), null).andExpect(status().isForbidden());
        }
        get("/api/customers/" + id, as("admin")).andExpect(status().isOk());
        get("/api/customers?q=" + newPhone(), as("quanly"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
    }

    private ResultActions attach(long orderId, String phone, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("name", name);
        return post("/api/orders/" + orderId + "/customer", as("thungan"), body);
    }

    /** A mobile number no other test uses: 09 and eight random digits. */
    private static String newPhone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    /** "0912 345 678": as a guest would say it. */
    private static String spaced(String phone) {
        return phone.substring(0, 4) + " " + phone.substring(4, 7) + " " + phone.substring(7);
    }
}
