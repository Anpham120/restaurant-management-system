package vn.bnn.rms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.bnn.rms.IntegrationTest;

/** US-23, US-24 and BR-25: clocking in and out, and signed corrections. Each test uses its own dates. */
class AttendanceIntegrationTest extends IntegrationTest {

    @Test
    void clockInNeedsAShiftTodayAndTheRightTime() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        String token = as(waiter.username());
        long shift = newWorkShift("08:00", "12:00");
        LocalDate day = LocalDate.of(2033, 3, 10);

        clock.set(day.atTime(7, 50));
        ResultActions noShift = post("/api/me/attendance/check-in", token, null).andExpect(status().isConflict());
        assertThat(body(noShift)).contains("không có ca");

        assign(waiter.id(), shift, day);
        clock.set(day.atTime(7, 40));
        ResultActions early = post("/api/me/attendance/check-in", token, null).andExpect(status().isConflict());
        assertThat(body(early)).contains("Chưa tới giờ vào ca").contains("07:45");

        clock.set(day.atTime(8, 10));
        post("/api/me/attendance/check-in", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lateMinutes").value(10));
        ResultActions twice = post("/api/me/attendance/check-in", token, null).andExpect(status().isConflict());
        assertThat(body(twice)).contains("Bạn đang trong ca");

        clock.set(day.atTime(11, 50));
        post("/api/me/attendance/check-out", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earlyMinutes").value(10))
                .andExpect(jsonPath("$.workedMinutes").value(220));

        get("/api/me/attendance?from=" + day + "&to=" + day, token)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workDate").value(day.toString()));
    }

    @Test
    void correctionsNeedAReasonAndAreSigned() throws Exception {
        EmployeeRef chef = newEmployee("CHEF", "MONTHLY", 9_000_000);
        long shift = newWorkShift("08:00", "12:00");
        LocalDate day = LocalDate.of(2033, 4, 12);
        assign(chef.id(), shift, day);
        clock.set(day.atTime(8, 0));
        long id = readLong(post("/api/me/attendance/check-in", as(chef.username()), null)
                .andExpect(status().isOk()), "$.id");

        // The chef forgot to clock out.
        String url = "/api/attendance/" + id;
        put(url, as("quanly"), Map.of("checkInAt", vn(day.atTime(8, 0)), "checkOutAt", vn(day.atTime(12, 0))))
                .andExpect(status().isBadRequest());
        put(url, as("quanly"), Map.of("checkInAt", vn(day.atTime(8, 0)), "checkOutAt", vn(day.atTime(12, 0)),
                        "reason", "Quên bấm ra ca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(240))
                .andExpect(jsonPath("$.editReason").value("Quên bấm ra ca"))
                .andExpect(jsonPath("$.editedByName").value("Nguyễn Văn Quản"));

        get("/api/attendance?from=" + day + "&to=" + day + "&employeeId=" + chef.id(), as("quanly"))
                .andExpect(jsonPath("$[0].editedByName").value("Nguyễn Văn Quản"));
        put(url, as(chef.username()), Map.of("checkInAt", vn(day.atTime(7, 0)), "checkOutAt", vn(day.atTime(12, 0)),
                        "reason", "Tự sửa"))
                .andExpect(status().isForbidden());
    }
}
