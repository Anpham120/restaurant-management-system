package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** US-22 and BR-24: leave requests; approving clears the shifts of those days. Each test uses its own dates. */
class LeaveIntegrationTest extends IntegrationTest {

    @Test
    void approvingLeaveClearsTheShiftsOfThoseDays() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        long shift = newWorkShift("10:00", "14:00");
        LocalDate first = LocalDate.of(2034, 5, 15);
        assign(waiter.id(), shift, first);
        assign(waiter.id(), shift, first.plusDays(1));
        long id = requestLeave(waiter.username(), first, first.plusDays(1));

        post("/api/leave-requests/" + id + "/approve", as("quanly"), Map.of("note", "Đồng ý"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.decidedByName").value("Nguyễn Văn Quản"));
        get("/api/me/schedule?from=" + first + "&to=" + first.plusDays(1), as(waiter.username()))
                .andExpect(jsonPath("$.length()").value(0));

        ResultActions onLeave = post("/api/schedule", as("quanly"),
                Map.of("employeeId", waiter.id(), "workShiftId", shift, "workDate", first.toString()))
                .andExpect(status().isConflict());
        assertThat(body(onLeave)).contains("duyệt nghỉ");
    }

    @Test
    void onlyPendingRequestsCanBeCancelledAndOnlyByTheirOwner() throws Exception {
        EmployeeRef chef = newEmployee("CHEF", "MONTHLY", 9_000_000);
        LocalDate day = LocalDate.of(2034, 6, 2);

        long pending = requestLeave(chef.username(), day, day);
        post("/api/me/leave-requests/" + pending + "/cancel", as("phucvu"), null).andExpect(status().isForbidden());
        post("/api/me/leave-requests/" + pending + "/cancel", as(chef.username()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long approved = requestLeave(chef.username(), day, day);
        post("/api/leave-requests/" + approved + "/approve", as("quanly"), null).andExpect(status().isOk());
        post("/api/me/leave-requests/" + approved + "/cancel", as(chef.username()), null)
                .andExpect(status().isConflict());
    }

    @Test
    void aDayWithAClockInCannotBeApprovedAsLeave() throws Exception {
        EmployeeRef cashier = newEmployee("CASHIER", "HOURLY", 25_000);
        long shift = newWorkShift("08:00", "12:00");
        LocalDate day = LocalDate.of(2034, 7, 20);
        assign(cashier.id(), shift, day);
        clock.set(day.atTime(8, 0));
        post("/api/me/attendance/check-in", as(cashier.username()), null).andExpect(status().isOk());
        clock.set(day.atTime(12, 0));
        post("/api/me/attendance/check-out", as(cashier.username()), null).andExpect(status().isOk());

        long id = requestLeave(cashier.username(), day, day);
        ResultActions refused = post("/api/leave-requests/" + id + "/approve", as("quanly"), null)
                .andExpect(status().isConflict());
        assertThat(body(refused)).contains("chấm công");
    }

    @Test
    void rejectingNeedsAReasonAndNobodyDecidesTheirOwnRequest() throws Exception {
        LocalDate day = LocalDate.of(2034, 8, 8);
        long own = requestLeave("quanly", day, day);

        post("/api/leave-requests/" + own + "/approve", as("quanly"), null).andExpect(status().isForbidden());
        post("/api/leave-requests/" + own + "/reject", as("admin"), Map.of("note", "")).andExpect(status().isBadRequest());
        post("/api/leave-requests/" + own + "/reject", as("admin"), Map.of("note", "Tuần đó đông khách"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Tuần đó đông khách"));
    }

    private long requestLeave(String username, LocalDate from, LocalDate to) throws Exception {
        return readLong(post("/api/me/leave-requests", as(username),
                        Map.of("fromDate", from.toString(), "toDate", to.toString(), "type", "PAID",
                                "reason", "Việc gia đình"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING")),
                "$.id");
    }
}
