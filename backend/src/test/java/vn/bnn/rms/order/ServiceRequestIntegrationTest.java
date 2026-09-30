package vn.bnn.rms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import vn.bnn.rms.IntegrationTest;
import vn.bnn.rms.common.realtime.RealtimeEvent.Alert;
import vn.bnn.rms.common.realtime.RealtimeEvent;

/** US-27 and BR-29: a guest calls a waiter or asks for the bill, and one waiter takes the call. */
@RecordApplicationEvents
class ServiceRequestIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void guestCallsAndOneWaiterTakesTheCall() throws Exception {
        TableRef table = newTable();

        // AC1: the call rings the waiters' screens. AC2: tapping again adds nothing and rings nothing.
        call(table, "CALL_STAFF").andExpect(status().isOk())
                .andExpect(jsonPath("$.openRequests").value(contains("CALL_STAFF")));
        call(table, "CALL_STAFF").andExpect(status().isOk());
        assertThat(alerts(table.id())).containsExactly(Alert.SERVICE_REQUEST);

        List<Number> ids = read(get("/api/service-requests", as("phucvu")),
                "$[?(@.tableId == %d)].id".formatted(table.id()));
        assertThat(ids).hasSize(1);
        long id = ids.get(0).longValue();
        get("/api/service-requests", as("phucvu"))
                .andExpect(jsonPath("$[?(@.id == %d)].tableName", id).value(hasItem(table.name())))
                .andExpect(jsonPath("$[?(@.id == %d)].type", id).value(hasItem("CALL_STAFF")));

        // AC4: a waiter takes it once; the guest page stops showing it and staff screens refresh without ringing.
        post("/api/service-requests/" + id + "/take", as("phucvu"), null).andExpect(status().isOk());
        post("/api/service-requests/" + id + "/take", as("quanly"), null).andExpect(status().isConflict());
        get("/api/public/tables/" + table.qrToken(), null).andExpect(jsonPath("$.openRequests").isEmpty());
        assertThat(alerts(table.id())).containsExactly(Alert.SERVICE_REQUEST, null);

        // FR-06.7: who took the call and when are kept.
        Map<String, Object> row = jdbc.queryForMap(
                "select handled_by, handled_at, created_at from service_request where id = ?", id);
        long waiterId = jdbc.queryForObject("select id from employee where username = 'phucvu'", Long.class);
        assertThat(((Number) row.get("handled_by")).longValue()).isEqualTo(waiterId);
        assertThat((Timestamp) row.get("handled_at")).isAfterOrEqualTo((Timestamp) row.get("created_at"));

        // Once taken, the guest can call again.
        call(table, "CALL_STAFF").andExpect(jsonPath("$.openRequests").value(contains("CALL_STAFF")));
        assertThat(alerts(table.id())).containsExactly(Alert.SERVICE_REQUEST, null, Alert.SERVICE_REQUEST);
    }

    @Test
    void theBillNeedsAnOpenOrder() throws Exception {
        TableRef table = newTable();
        // AC3
        call(table, "BILL").andExpect(status().isConflict());
        openOrder(table.id());
        call(table, "BILL").andExpect(status().isOk())
                .andExpect(jsonPath("$.openRequests").value(contains("BILL")));
    }

    @Test
    void onlyWaitersSeeCallsAndBadCallsAreRefused() throws Exception {
        get("/api/service-requests", as("bep")).andExpect(status().isForbidden());
        get("/api/service-requests", as("thungan")).andExpect(status().isForbidden());
        post("/api/service-requests/999999999/take", as("phucvu"), null).andExpect(status().isNotFound());
        post("/api/public/tables/khong-ton-tai/requests", null, Map.of("type", "CALL_STAFF"))
                .andExpect(status().isNotFound());
        call(newTable(), "PARTY").andExpect(status().isBadRequest());
    }

    private ResultActions call(TableRef table, String type) throws Exception {
        return post("/api/public/tables/" + table.qrToken() + "/requests", null, Map.of("type", type));
    }

    /** The alert of each REQUESTS_CHANGED notice of the table, oldest first; null when nothing should ring. */
    private List<Alert> alerts(long tableId) {
        return events.stream(RealtimeEvent.class)
                .filter(e -> RealtimeEvent.REQUESTS_CHANGED.equals(e.type()) && Objects.equals(e.tableId(), tableId))
                .map(RealtimeEvent::alert)
                .toList();
    }
}
